#!/bin/bash
# startForBash/clean.sh
# Использование: ./clean.sh [--force] [--all]
START_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="$START_DIR/../lib"   # ИСПРАВЛЕНО
source "$LIB_DIR/common.sh"
source "$LIB_DIR/clean.sh"

force=0; all=0
for a in "$@"; do
    case "$a" in
        -f|--force) force=1 ;;
        -a|--all)   all=1 ;;
        -h|--help)
            echo "Использование: ./clean.sh [--force] [--all]"
            echo "  --force - без подтверждения"
            echo "  --all   - полная очистка (включая все образы)"
            exit 0 ;;
        *) write_error "Неизвестный аргумент: $a"; exit 1 ;;
    esac
done
clean_docker "$force" "$all"