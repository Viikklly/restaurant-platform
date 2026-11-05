#!/bin/bash
# startForBash/lib/status.sh
# Статус сервисов и мониторинга (только функция)

LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$LIB_DIR/common.sh"

show_status() {
    clear 2>/dev/null || true
    write_section "СТАТУС СЕРВИСОВ"
    show_containers

    write_section "ЗДОРОВЬЕ СЕРВИСОВ"
    local services=(
        "Eureka:8761" "Config:8888" "Gateway:8090" "Auth:8081"
        "Order:8082" "Kitchen:8083" "Payment:8084" "Notification:8086"
    )
    local svc name port
    for svc in "${services[@]}"; do
        name="${svc%%:*}"; port="${svc##*:}"
        if check_health "http://localhost:$port/actuator/health" 2; then
            write_success "$name: UP"
        else
            write_error "$name: DOWN"
        fi
    done


        write_section "ЗДОРОВЬЕ UI-СЕРВИСОВ"
        local ui_services=(
            "Kafka UI:8085" "Redis Insight:5540" "PGAdmin:5050"
        )
        local ui name port code
        for ui in "${ui_services[@]}"; do
            name="${ui%%:*}"; port="${ui##*:}"
            # Получаем HTTP-код. Если сервер не отвечает, curl вернёт 000.
            code=$(curl -s -o /dev/null -w "%{http_code}" -m 3 "http://localhost:$port" 2>/dev/null)

            # Если код не 000 и не пустой — значит, сервис ответил (200, 302, 401 и т.д.)
            if [ -n "$code" ] && [ "$code" != "000" ]; then
                write_success "$name: UP (HTTP $code)"
            else
                write_error "$name: DOWN (не отвечает)"
            fi
        done


    write_section "ЗДОРОВЬЕ МОНИТОРИНГА"
    local monitoring=(
        "Prometheus:9090/-/healthy" "Grafana:3000/api/health"
        "Alertmanager:9093/-/healthy" "Loki:3100/ready"
        "Jaeger:16686/"
    )
    local m name path
    for m in "${monitoring[@]}"; do
        name="${m%%:*}"; path="${m#*:}"
        if check_url "http://localhost:$path" 2; then
            write_success "$name: OK"
        else
            write_error "$name: DOWN"
        fi
    done
}