#!/bin/bash
# startForBash/status.sh
# Использование: ./status.sh [--watch]
START_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="$START_DIR/../lib"
source "$LIB_DIR/common.sh"
source "$LIB_DIR/status.sh"

watch=0
case "${1:-}" in
    -w|--watch) watch=1 ;;
    -h|--help)  echo "Использование: ./status.sh [--watch]"; exit 0 ;;
esac

if [ "$watch" -eq 1 ]; then
    while true; do
        show_status
        write_step "Обновление каждые 5 секунд. Ctrl+C для выхода"
        sleep 5
    done
else
    show_status
fi