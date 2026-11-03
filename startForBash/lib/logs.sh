#!/bin/bash
# startForBash/lib/logs.sh
LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$LIB_DIR/common.sh"

MONITORING_SERVICES=("loki" "promtail" "grafana" "prometheus" "alertmanager" "jaeger")

logs_services() {
    local service="$1" lines="${2:-50}" filter="$3" follow="${4:-0}"
    local compose
    compose=$(detect_main_compose)
    if [ -z "$compose" ]; then
        write_error "docker-compose.yml не найден в $PROJECT_ROOT"
        return 1
    fi

    local cmd=(docker-compose -f "$compose" logs --tail="$lines")
    [ "$follow" -eq 1 ] && cmd+=("-f")
    [ -n "$service" ] && cmd+=("$service")

    if [ -n "$filter" ]; then
        "${cmd[@]}" 2>&1 | grep --line-buffered --color=always "$filter"
    else
        "${cmd[@]}"
    fi
}

logs_monitoring() {
    local service="$1" lines="${2:-30}" follow="${3:-0}"

    if [ -n "$service" ]; then
        local ok=0 s
        for s in "${MONITORING_SERVICES[@]}"; do
            [ "$s" == "$service" ] && ok=1 && break
        done
        if [ $ok -eq 0 ]; then
            write_error "Сервис '$service' не найден в списке мониторинга"
            write_step "  Доступные: ${MONITORING_SERVICES[*]}"
            return 1
        fi
        write_step "Логи сервиса: $service"
        local cmd=(docker-compose logs --tail="$lines")
        [ "$follow" -eq 1 ] && cmd+=("-f")
        cmd+=("$service")
        "${cmd[@]}"
    else
        write_step "Логи всех сервисов мониторинга (последние $lines строк)"
        local cmd=(docker-compose logs --tail="$lines")
        [ "$follow" -eq 1 ] && cmd+=("-f")
        cmd+=("${MONITORING_SERVICES[@]}")
        "${cmd[@]}"
    fi
}