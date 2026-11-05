#!/bin/bash
# startForBash/lib/stack.sh
LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$LIB_DIR/common.sh"
source "$LIB_DIR/ui.sh"

# Инфраструктурные контейнеры, у которых есть healthcheck в docker-compose.yml
INFRA_HEALTHCHECK_CONTAINERS=(
    restaurant-postgres-auth
    restaurant-postgres-order
    restaurant-postgres-kitchen
    restaurant-postgres-payment
    restaurant-redis
)

stack_full() {
    write_section "ПОЛНЫЙ ЗАПУСК"
    check_docker

    local compose
    compose=$(detect_main_compose)
    if [ -z "$compose" ]; then
        write_error "docker-compose.yml не найден в $PROJECT_ROOT"
        exit 1
    fi
    write_step "Используем: $compose"

    # --- Защита от перезапуска во время нагрузочного теста ---
    if docker ps --format '{{.Names}}' | grep -q "^restaurant-k6$"; then
        write_warning "Контейнер restaurant-k6 запущен — возможно, идёт нагрузочный тест."
        read -r -p "Перезапуск стека прервёт тест. Продолжить? (y/N): " confirm
        if [[ "$confirm" != "y" && "$confirm" != "Y" ]]; then
            write_warning "Отменено"
            return 0
        fi
    fi

    write_step "Проверка и удаление ZooKeeper..."
    if docker ps -a --format '{{.Names}}' | grep -q "restaurant-zookeeper"; then
        write_warning "Найден старый контейнер ZooKeeper. Удаляем..."
        docker rm -f restaurant-zookeeper 2>/dev/null
        write_success "ZooKeeper удалён"
    else
        write_success "ZooKeeper не найден"
    fi

    write_step "Останавливаем старые контейнеры..."
    docker-compose -f "$compose" down >/dev/null 2>&1

    # ---------- БД и брокеры ----------
    write_step "Запускаем БД, Redis, Kafka и UI..."
    docker-compose -f "$compose" up -d \
      postgres-auth postgres-order postgres-kitchen postgres-payment redis kafka \
      kafka-ui redis-insight pgadmin

    write_step "Ждём готовности инфраструктуры (healthcheck)..."
    local c
    for c in "${INFRA_HEALTHCHECK_CONTAINERS[@]}"; do
        wait_for_health "$c" 60 || {
            write_error "$c не стал healthy. Проверь: docker logs $c"
            exit 1
        }
    done

    # Kafka стартует дольше (start_period: 40s)
    wait_for_health restaurant-kafka 90 || {
        write_error "Kafka не стала healthy. Проверь: docker logs restaurant-kafka"
        exit 1
    }

    # UI-сервисы: у них нет healthcheck в docker-compose, поэтому просто проверяем, что контейнеры запустились
    write_step "Проверяем, что UI-сервисы запустились..."
    sleep 3
    for ui in restaurant-kafka-ui restaurant-redis-insight restaurant-pgadmin; do
        if docker ps --format '{{.Names}}' | grep -qx "$ui"; then
            write_success "$ui запущен"
        else
            write_warning "$ui не запустился (проверь: docker logs $ui)"
        fi
    done


    write_success "Инфраструктура готова"

    # ---------- Мониторинг ----------
    write_step "Запускаем мониторинг..."
    docker-compose -f "$compose" up -d \
        prometheus grafana alertmanager loki promtail jaeger
    write_success "Мониторинг запущен"

    # Мониторинг проверяем по HTTP (не у всех есть healthcheck)
    wait_for_service "http://localhost:9090/-/healthy" "Prometheus" 30 2 || true
    wait_for_service "http://localhost:3000/api/health" "Grafana" 30 2 || true
    wait_for_service "http://localhost:3100/ready"      "Loki"     30 2 || true
    wait_for_service "http://localhost:16686/" "Jaeger" 30 2 || true

    # ---------- Discovery Service ----------
    write_step "Запускаем Discovery Service..."
    docker-compose -f "$compose" up -d discovery-service
    wait_for_service "http://localhost:8761/actuator/health" "Discovery" 60 2 || {
        write_error "Discovery Service не поднялся. Проверь: docker logs restaurant-discovery-service"
        exit 1
    }
    write_success "Discovery Service запущен"

    # ---------- Config Service ----------
    write_step "Запускаем Config Service..."
    docker-compose -f "$compose" up -d config-service
    wait_for_service "http://localhost:8888/actuator/health" "Config Service" 60 2 || {
        write_error "Config Service не поднялся. Проверь: docker logs restaurant-config-service"
        exit 1
    }
    write_success "Config Service запущен"

    # ---------- Микросервисы ----------
    write_step "Запускаем микросервисы..."
    docker-compose -f "$compose" up -d \
        gateway-service auth-service order-service kitchen-service payment-service notification-service
    write_success "Контейнеры микросервисов запущены"

    # Даём время на старт Spring Boot + применение Liquibase-миграций.
    # Если миграции падают — healthcheck сервиса останется DOWN, и мы это увидим ниже.
    write_step "Ждём готовности микросервисов (Liquibase миграции + старт Spring Boot)..."
    local i
    local services_ready=0
    for i in $(seq 1 90); do
        if check_health "http://localhost:8081/actuator/health" 2 \
            && check_health "http://localhost:8082/actuator/health" 2 \
            && check_health "http://localhost:8083/actuator/health" 2 \
            && check_health "http://localhost:8084/actuator/health" 2 \
            && check_health "http://localhost:8090/actuator/health" 2; then
            services_ready=1
            break
        fi
        if (( i % 10 == 0 )); then
            write_warning "Ещё ждём... (${i}s)"
        fi
        sleep 1
    done

    if [ "$services_ready" -eq 1 ]; then
        write_success "Все микросервисы ответили на /actuator/health"
    else
        write_warning "Не все микросервисы ответили за 90s. Проверь статус ниже."
        write_step "  Возможные причины:"
        write_step "    - ошибка Liquibase → docker logs restaurant-order-service | grep -i liquibase"
        write_step "    - сервис ещё стартует → подожди и выполни ./test.sh -a"
    fi

    # Проверяем, что Liquibase нигде не упал явно
    _check_liquibase_errors "$compose"

    health_monitoring

    write_section "ГОТОВО!"
    show_containers
    show_urls
}

stack_dev() {
    write_section "ЗАПУСК ДЛЯ РАЗРАБОТКИ"
    check_docker

    local compose
    compose=$(detect_infra_compose)
    if [ -z "$compose" ]; then
        write_error "Compose-файл не найден!"
        exit 1
    fi
    write_step "Используем: $compose"

    # down -v удаляет тома, включая databasechangelog Liquibase.
    write_warning "ВНИМАНИЕ: dev-режим выполняет 'down -v' — будут удалены ВСЕ тома,"
    write_warning "включая базы данных и таблицы databasechangelog (история Liquibase)."
    read -r -p "Продолжить? (y/N): " confirm
    if [[ "$confirm" != "y" && "$confirm" != "Y" ]]; then
        write_warning "Отменено"
        return 0
    fi

    write_step "Очистка старых контейнеров и томов..."
    docker-compose -f "$compose" down -v >/dev/null 2>&1
    docker-compose -f "$compose" down --remove-orphans >/dev/null 2>&1
    write_success "Очистка выполнена"

    write_step "Запуск инфраструктуры и мониторинга..."
    docker-compose -f "$compose" up -d || { write_error "Ошибка запуска"; exit 1; }
    write_success "Запуск выполнен"

    write_step "Ожидание готовности инфраструктуры..."
    local c
    for c in "${INFRA_HEALTHCHECK_CONTAINERS[@]}"; do
        wait_for_health "$c" 60 || write_warning "$c не healthy, продолжаем"
    done
    wait_for_health restaurant-kafka 90 || write_warning "Kafka не healthy, продолжаем"

    write_section "ГОТОВО!"
    show_containers
    show_urls

    write_success "\n Запуск микросервисов через IDE:"
    write_step "    1. Discovery    → http://localhost:8761"
    write_step "    2. Config       → http://localhost:8888"
    write_step "    3. Auth         → http://localhost:8081"
    write_step "    4. Order        → http://localhost:8082"
    write_step "    5. Kitchen      → http://localhost:8083"
    write_step "    6. Payment      → http://localhost:8084"
    write_step "    7. Notification → http://localhost:8086"
    write_step "    8. Gateway      → http://localhost:8090"
}

stack_stop() {
    local clean="${1:-0}"
    local infra="${2:-0}"

    write_section "ОСТАНОВКА"

    if [ "$infra" -eq 1 ]; then
        local compose
        compose=$(detect_infra_compose)
        if [ -z "$compose" ]; then
            write_error "Compose-файл для инфраструктуры не найден!"
            exit 1
        fi
        write_step "Останавливаем инфраструктуру ($compose)..."
        if [ "$clean" -eq 1 ]; then
            write_warning "Будут удалены тома, включая databasechangelog Liquibase."
            docker-compose -f "$compose" down -v
            write_success "Инфраструктура остановлена, тома удалены"
        else
            docker-compose -f "$compose" down
            write_success "Инфраструктура остановлена"
        fi
    else
        local compose
        compose=$(detect_main_compose)
        if [ -z "$compose" ]; then
            write_error "docker-compose.yml не найден в $PROJECT_ROOT"
            exit 1
        fi
        write_step "Останавливаем все сервисы..."
        if [ "$clean" -eq 1 ]; then
            write_warning "Будут удалены тома, включая databasechangelog Liquibase."
            docker-compose -f "$compose" down -v
            write_success "Все сервисы остановлены, тома удалены"
        else
            docker-compose -f "$compose" down
            write_success "Все сервисы остановлены"
        fi
    fi

    show_containers
}

# Проверка логов на явные ошибки Liquibase после старта.
# Не прерывает выполнение, только предупреждает.
_check_liquibase_errors() {
    local compose="$1"
    local services=(
        "restaurant-auth-service"
        "restaurant-order-service"
        "restaurant-kitchen-service"
        "restaurant-payment-service"
    )
    local found=0 s errs

    write_step "Проверка логов на ошибки Liquibase..."
    for s in "${services[@]}"; do
        if docker ps -a --format '{{.Names}}' | grep -qx "$s"; then
            errs=$(docker logs --tail 500 "$s" 2>&1 \
                | grep -iE "liquibase.*(exception|error|failed)" \
                | grep -viE "No changesets to apply" \
                | head -3)
            if [ -n "$errs" ]; then
                write_warning "Возможные ошибки Liquibase в $s:"
                echo "$errs" | while IFS= read -r line; do
                    write_error "   $line"
                done
                found=1
            fi
        fi
    done

    if [ "$found" -eq 0 ]; then
        write_success "Ошибок Liquibase в логах не найдено"
    else
        write_step "  Диагностика:"
        write_step "    docker logs restaurant-order-service | grep -i liquibase"
        write_step "    docker exec -it restaurant-postgres-order psql -U postgres -d orderdb \\"
        write_step "      -c 'SELECT id, exectype FROM databasechangelog ORDER BY orderexecuted;'"
    fi
}