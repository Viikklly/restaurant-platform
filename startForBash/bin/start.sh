#!/bin/bash
# startForBash/bin/start.sh
# Использование: ./start.sh [full|dev]
START_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="$START_DIR/../lib"   # <--- ВОТ ТАК ПРАВИЛЬНО
source "$LIB_DIR/common.sh"
source "$LIB_DIR/stack.sh"
source "$LIB_DIR/ui.sh"
source "$LIB_DIR/health.sh"

case "${1:-full}" in
    full)  stack_full ;;
    dev)   stack_dev ;;
    -h|--help)
        echo "Использование: ./start.sh [full|dev]"
        echo "  full  - полный запуск всех сервисов (по умолчанию)"
        echo "  dev   - только инфраструктура для разработки"
        ;;
    *) write_error "Неизвестный режим: $1"; exit 1 ;;
esac