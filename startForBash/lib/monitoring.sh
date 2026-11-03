#!/bin/bash
# startForBash/lib/monitoring.sh
# Start / stop / check мониторинга (только функции)

LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$LIB_DIR/common.sh"
source "$LIB_DIR/health.sh"

MONITORING_CONTAINERS=(
    "restaurant-loki"
    "restaurant-promtail"
    "restaurant-grafana"
    "restaurant-prometheus"
    "restaurant-alertmanager"
    "restaurant-jaeger"
)
MONITORING_SERVICES=("loki" "promtail" "grafana" "prometheus" "alertmanager" "jaeger")

# ---------- START ----------
monitoring_start() {
    write_section "ЗАПУСК МОНИТОРИНГА"
    check_docker

    local compose
    compose=$(detect_infra_compose)
    if [ -z "$compose" ]; then
        write_error "Compose-файл не найден!"
        exit 1
    fi
    write_step "Используем: $compose"

    write_step "Останавливаем старые контейнеры мониторинга..."
    docker-compose -f "$compose" stop "${MONITORING_SERVICES[@]}" >/dev/null 2>&1
    docker-compose -f "$compose" rm -f "${MONITORING_SERVICES[@]}" >/dev/null 2>&1
    write_success "Старые контейнеры удалены"

    write_step "Запускаем мониторинг..."
    docker-compose -f "$compose" up -d "${MONITORING_SERVICES[@]}" || {
        write_error "Ошибка при запуске мониторинга!"
        exit 1
    }
    write_success "Мониторинг запущен"

    sleep 15

    write_section "ПРОВЕРКА МОНИТОРИНГА"
    write_step "Статус контейнеров:"
    docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" \
        --filter "name=restaurant-loki" \
        --filter "name=restaurant-promtail" \
        --filter "name=restaurant-grafana" \
        --filter "name=restaurant-prometheus" \
        --filter "name=restaurant-jaeger" \
        --filter "name=restaurant-alertmanager"

    # Базовые эндпоинты (Prometheus/Grafana/Alertmanager/Loki/Jaeger)
    health_monitoring_full
    local rc=$?

    # Promtail — отдельно (есть в start-monitoring.ps1, отсутствует в health_monitoring_full)
    write_step "\n Проверка Promtail:"
    local pcode
    pcode=$(curl -s -o /dev/null -w "%{http_code}" -m 3 "http://localhost:9080/ready" 2>/dev/null)
    if [ "$pcode" == "200" ] || [ "$pcode" == "204" ]; then
        write_success "Promtail - OK (статус $pcode)"
    else
        write_warning "Promtail - статус $pcode"
        rc=1
    fi

    write_section "МОНИТОРИНГ ЗАПУЩЕН!"
    write_step "  Prometheus:   http://localhost:9090"
    write_step "  Grafana:      http://localhost:3000 (admin/admin)"
    write_step "  Alertmanager: http://localhost:9093"
    write_step "  Loki:         http://localhost:3100"
    write_step "  Jaeger:       http://localhost:16686"

    if [ $rc -eq 0 ]; then
        write_success "\n ВСЕ КОМПОНЕНТЫ РАБОТАЮТ!"
    else
        write_warning "\n НЕКОТОРЫЕ КОМПОНЕНТЫ НЕ РАБОТАЮТ"
    fi
}

# ---------- STOP ----------
# $1 = clean (0/1)
monitoring_stop() {
    local clean="${1:-0}"
    write_section "ОСТАНОВКА МОНИТОРИНГА"
    check_docker

    local compose
    compose=$(detect_infra_compose)
    if [ -z "$compose" ]; then
        write_error "Compose-файл не найден!"
        exit 1
    fi

    if [ "$clean" -eq 1 ]; then
        write_step "Останавливаем и удаляем тома..."
        docker-compose -f "$compose" rm -sf "${MONITORING_SERVICES[@]}"
        write_success "Остановлено, тома удалены"
    else
        write_step "Останавливаем мониторинг..."
        docker-compose -f "$compose" stop "${MONITORING_SERVICES[@]}"
        write_success "Остановлено"
    fi

    write_step "Оставшиеся контейнеры:"
    docker ps --format "table {{.Names}}\t{{.Status}}" --filter "name=restaurant-"
}

# ---------- CHECK (расширенная, аналог check-monitoring.ps1) ----------
monitoring_check() {
    write_section "ПРОВЕРКА МОНИТОРИНГА"

    # 1. Контейнеры
    write_step " КОНТЕЙНЕРЫ:"
    local all_running=0 not_running=() c status
    for c in "${MONITORING_CONTAINERS[@]}"; do
        status=$(docker ps --filter "name=$c" --format "{{.Status}}" 2>/dev/null)
        if [ -n "$status" ]; then
            write_success "$c - $status"
        else
            local exists
            exists=$(docker ps -a --filter "name=$c" --format "{{.Status}}" 2>/dev/null)
            if [ -n "$exists" ]; then
                write_warning "$c - ОСТАНОВЛЕН ($exists)"
            else
                write_error "$c - НЕ СУЩЕСТВУЕТ"
            fi
            all_running=1
            not_running+=("$c")
        fi
    done

    # 2. Эндпоинты (Prometheus/Grafana/Alertmanager/Loki/Jaeger)
    write_step "\n ЭНДПОИНТЫ:"
    health_monitoring_full
    local all_ok=$?

    # 2b. Promtail (отдельно — как в check-monitoring.ps1)
    write_step "\n PROMTAIL:"
    local pcode
    pcode=$(curl -s -o /dev/null -w "%{http_code}" -m 3 "http://localhost:9080/ready" 2>/dev/null)
    if [ "$pcode" == "200" ] || [ "$pcode" == "204" ]; then
        write_success "Promtail - OK (статус $pcode)"
    else
        write_warning "Promtail - статус $pcode (ожидался 200)"
        all_ok=1
    fi

    # 3. Loki push
    write_step "\n LOKI PUSH:"
    local ts
    ts=$(date -u +"%Y-%m-%dT%H:%M:%S.000000Z")
    local payload="{\"streams\":[{\"stream\":{\"test\":\"health-check\"},\"values\":[[\"$ts\",\"health check\"]]}]}"
    local code
    code=$(curl -s -o /dev/null -w "%{http_code}" -m 5 \
        -X POST "http://localhost:3100/loki/api/v1/push" \
        -H "Content-Type: application/json" \
        -d "$payload" 2>/dev/null)
    if [ "$code" == "204" ] || [ "$code" == "200" ]; then
        write_success "Loki принимает логи - OK"
    else
        write_error "Loki НЕ принимает логи (статус $code)"
    fi

    # 4. Ошибки в логах
    write_step "\n ПОСЛЕДНИЕ ЛОГИ (ошибки):"
    local found=0 errs
    for c in "${MONITORING_CONTAINERS[@]}"; do
        if docker ps -a --filter "name=$c" --format "{{.Names}}" 2>/dev/null | grep -q "$c"; then
            errs=$(docker logs --tail 5 "$c" 2>&1 | grep -iE "error|panic|fatal" | head -3)
            if [ -n "$errs" ]; then
                write_warning "$c:"
                echo "$errs" | while IFS= read -r line; do write_error "   $line"; done
                found=1
            fi
        fi
    done
    [ $found -eq 0 ] && write_success "Ошибок в логах не найдено"

    # Итог
    echo ""
    if [ $all_running -eq 0 ] && [ $all_ok -eq 0 ]; then
        write_success " ВСЕ КОМПОНЕНТЫ МОНИТОРИНГА РАБОТАЮТ!"
        write_step "   Grafana: http://localhost:3000 (admin/admin)"
        write_step "   Loki:    http://localhost:3100"
        write_step "   Jaeger:  http://localhost:16686"
    else
        write_warning " НЕКОТОРЫЕ КОМПОНЕНТЫ НЕ РАБОТАЮТ"
        if [ ${#not_running[@]} -gt 0 ]; then
            write_warning "   Не запущены: ${not_running[*]}"
        fi
        write_step "\n   Рекомендации:"
        write_step "   1. Проверьте логи: docker logs <container_name>"
        write_step "   2. Перезапустите: ./monitor.sh start"
        write_step "   3. Проверьте конфиги в ./infrastructure/monitoring/"
    fi
}