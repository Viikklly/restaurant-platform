#!/bin/bash
# startForBash/bin/logs.sh
START_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="$START_DIR/../lib"   # ИСПРАВЛЕНО
source "$LIB_DIR/common.sh"
source "$LIB_DIR/logs.sh"

service=""; all=0; follow=0; lines=50; filter=""; monitoring=0
while [ $# -gt 0 ]; do
    case "$1" in
        -s|--service)    service="$2"; shift 2 ;;
        -a|--all)        all=1; shift ;;
        -m|--monitoring) monitoring=1; shift ;;
        -f|--follow)     follow=1; shift ;;
        -n|--lines)
            if ! [[ "${2:-}" =~ ^[0-9]+$ ]]; then
                write_error "Опция -n требует числового значения"
                exit 1
            fi
            lines="$2"; shift 2 ;;
        -t|--filter)     filter="$2"; shift 2 ;;
        -h|--help)
            echo "Использование:"
            echo "  ./logs.sh -s <service> [-n 50] [-f] [-t FILTER]"
            echo "  ./logs.sh -a [-n 50] [-f] [-t FILTER]"
            echo "  ./logs.sh -m [-s <monitoring-service>] [-n 30] [-f]"
            exit 0 ;;
        *) write_error "Неизвестный аргумент: $1"; exit 1 ;;
    esac
done

if [ "$monitoring" -eq 1 ]; then
    logs_monitoring "$service" "$lines" "$follow"
    exit $?
fi

if [ -z "$service" ] && [ "$all" -eq 0 ]; then
    write_error "Укажите сервис (-s) или используйте -a (все)"
    write_step "  Примеры:"
    write_step "    ./logs.sh -s auth-service"
    write_step "    ./logs.sh -a -f"
    write_step "    ./logs.sh -s gateway-service -t ERROR"
    exit 1
fi

logs_services "$service" "$lines" "$filter" "$follow"