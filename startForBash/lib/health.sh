#!/bin/bash
# startForBash/lib/health.sh
# Проверки здоровья сервисов, мониторинга, Eureka

LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$LIB_DIR/common.sh"

# ---------- Health сервисов ----------
health_services() {
    local services=(
        "Eureka:8761"
        "Config:8888"
        "Gateway:8090"
        "Auth:8081"
        "Order:8082"
        "Kitchen:8083"
        "Payment:8084"
        "Notification:8086"
    )
    local svc name port
    for svc in "${services[@]}"; do
        name="${svc%%:*}"
        port="${svc##*:}"
        if check_health "http://localhost:$port/actuator/health" 2; then
            write_success "$name: UP"
        else
            write_error "$name: DOWN"
        fi
    done
}

# ---------- Health мониторинга (базовая) ----------
health_monitoring() {
    local monitoring=(
        "Prometheus:9090/-/healthy"
        "Grafana:3000/api/health"
        "Jaeger:16686/"
    )
    local svc name path all_ok=0
    for svc in "${monitoring[@]}"; do
        name="${svc%%:*}"
        path="${svc#*:}"
        if check_url "http://localhost:$path" 3; then
            write_success "$name: OK"
        else
            write_warning "$name: не отвечает"
            all_ok=1
        fi
    done
    return $all_ok
}

# ---------- Health мониторинга (расширенная) ----------
health_monitoring_full() {
    local monitoring=(
        "Prometheus:9090/-/healthy:200"
        "Grafana:3000/api/health:200"
        "Alertmanager:9093/-/healthy:200"
        "Loki:3100/ready:200"
        "Jaeger:16686/:200"
    )
    local svc name path expected all_ok=0
    for svc in "${monitoring[@]}"; do
        name="${svc%%:*}"
        local rest="${svc#*:}"
        path="${rest%:*}"
        expected="${rest##*:}"
        local code
        code=$(curl -s -o /dev/null -w "%{http_code}" -m 3 "http://localhost:$path" 2>/dev/null)
        if [ "$code" == "$expected" ] || [ "$code" == "204" ]; then
            write_success "$name: OK (статус $code)"
        else
            write_warning "$name: статус $code (ожидался $expected)"
            all_ok=1
        fi
    done
    return $all_ok
}

# ---------- Тест сервисов (с подсчётом) ----------
# $1 = service (пусто = все)
health_test() {
    local service="$1"
    local ALL_SERVICES=(
        "Eureka:http://localhost:8761/actuator/health"
        "Config:http://localhost:8888/actuator/health"
        "Gateway:http://localhost:8090/actuator/health"
        "Auth:http://localhost:8081/actuator/health"
        "Order:http://localhost:8082/actuator/health"
        "Kitchen:http://localhost:8083/actuator/health"
        "Payment:http://localhost:8084/actuator/health"
        "Notification:http://localhost:8086/actuator/health"
    )
    local SERVICES=()

    if [ -n "$service" ]; then
        local entry name
        for entry in "${ALL_SERVICES[@]}"; do
            name="${entry%%:*}"
            [ "$name" == "$service" ] && SERVICES+=("$entry")
        done
        if [ ${#SERVICES[@]} -eq 0 ]; then
            write_error "Сервис '$service' не найден"
            exit 1
        fi
    else
        SERVICES=("${ALL_SERVICES[@]}")
    fi

    local success=0 total=0 entry name url code
    for entry in "${SERVICES[@]}"; do
        name="${entry%%:*}"
        url="${entry#*:}"
        total=$((total+1))
        code=$(curl -s -o /dev/null -w "%{http_code}" -m 5 "$url" 2>/dev/null)
        if [ "$code" == "200" ]; then
            write_success "$name: OK"
            success=$((success+1))
        else
            write_error "$name: Status $code"
        fi
    done

    write_section "РЕЗУЛЬТАТ"
    write_step "  Успешно: $success из $total"
    if [ $success -eq $total ]; then
        write_success "Все сервисы работают!"
    else
        write_warning "Некоторые сервисы недоступны"
    fi
}

# ---------- Eureka: проверка регистрации ----------
eureka_check() {
    local services=(
        "AUTH-SERVICE"
        "ORDER-SERVICE"
        "KITCHEN-SERVICE"
        "PAYMENT-SERVICE"
        "GATEWAY-SERVICE"
    )

    write_step "Подключение к Eureka..."
    local json
    json=$(curl -s -m 10 -H "Accept: application/json" "http://localhost:8761/eureka/apps" 2>/dev/null)
    if [ -z "$json" ]; then
        write_error "Не удалось подключиться к Eureka!"
        write_step "  Проверьте, что discovery-service запущен"
        write_step "  URL: http://localhost:8761"
        return 1
    fi

    # Простой парсинг имён приложений из JSON без jq
    local registered
    registered=$(echo "$json" | grep -oE '"name"[[:space:]]*:[[:space:]]*"[^"]+"' \
                 | sed -E 's/.*"([^"]+)"$/\1/' | sort -u)

    echo -e "\n  ${CYAN}Регистрация в Eureka:${NC}"
    local all_ok=0 missing=()
    local svc
    for svc in "${services[@]}"; do
        if echo "$registered" | grep -qx "$svc"; then
            write_success "   $svc"
        else
            write_warning "   $svc (не зарегистрирован)"
            all_ok=1
            missing+=("$svc")
        fi
    done

    local count
    count=$(echo "$registered" | grep -c . 2>/dev/null || echo 0)
    write_step "\n  Всего зарегистрировано: $count сервисов"

    if [ $all_ok -eq 0 ]; then
        write_success "\n  Все сервисы зарегистрированы в Eureka!"
        write_step "    Eureka UI: http://localhost:8761"
        return 0
    else
        write_warning "\n  Некоторые сервисы не зарегистрированы!"
        write_step "    Отсутствуют: ${missing[*]}"
        write_step "\n  Рекомендации:"
        write_step "    1. Проверьте, запущены ли сервисы"
        write_step "    2. Проверьте логи проблемных сервисов"
        write_step "    3. Проверьте Eureka UI: http://localhost:8761"
        return 1
    fi
}