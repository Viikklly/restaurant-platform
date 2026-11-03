#!/bin/bash
# startForBash/stop.sh
# Использование: ./stop.sh [--clean] [--infra]
START_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="$START_DIR/../lib"   # ИСПРАВЛЕНО
source "$LIB_DIR/common.sh"
source "$LIB_DIR/stack.sh"

clean=0; infra=0
for a in "$@"; do
    case "$a" in
        -c|--clean) clean=1 ;;
        -i|--infra) infra=1 ;;
        -h|--help)
            echo "Использование: ./stop.sh [--clean] [--infra]"
            echo "  --clean - удалить тома"
            echo "  --infra - только инфраструктура"
            exit 0 ;;
        *) write_error "Неизвестный аргумент: $a"; exit 1 ;;
    esac
done
stack_stop "$clean" "$infra"