#!/bin/bash
# startForBash/bin/monitor.sh
START_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="$START_DIR/../lib"   # ИСПРАВЛЕНО
source "$LIB_DIR/common.sh"
source "$LIB_DIR/health.sh"
source "$LIB_DIR/monitoring.sh"

case "${1:-}" in
    -h|--help)
        echo "Использование: ./monitor.sh [start|stop|check] [--clean]"
        echo "  start  - запуск мониторинга"
        echo "  stop   - остановка мониторинга (--clean: с удалением томов)"
        echo "  check  - проверка работоспособности (по умолчанию)"
        exit 0 ;;
esac

action="${1:-check}"; shift || true
clean=0
for a in "$@"; do
    case "$a" in
        -c|--clean) clean=1 ;;
        -h|--help)
            echo "Использование: ./monitor.sh [start|stop|check] [--clean]"
            exit 0 ;;
        *) write_error "Неизвестный аргумент: $a"; exit 1 ;;
    esac
done

case "$action" in
    start)  monitoring_start ;;
    stop)   monitoring_stop "$clean" ;;
    check)  monitoring_check ;;
    *) write_error "Неизвестное действие: $action"; exit 1 ;;
esac