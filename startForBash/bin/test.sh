#!/bin/bash
# startForBash/test.sh
# Использование: ./test.sh [-s <service>] | ./test.sh -a
START_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="$START_DIR/../lib"   # ИСПРАВЛЕНО
source "$LIB_DIR/common.sh"
source "$LIB_DIR/health.sh"

service=""; all=0
while [ $# -gt 0 ]; do
    case "$1" in
        -s|--service) service="$2"; shift 2 ;;
        -a|--all)     all=1; shift ;;
        -h|--help)
            echo "Использование: ./test.sh [-s <service>] | ./test.sh -a"
            echo "  Сервисы: Eureka, Config, Gateway, Auth, Order, Kitchen, Payment, Notification"
            exit 0 ;;
        *) write_error "Неизвестный аргумент: $1"; exit 1 ;;
    esac
done

if [ -z "$service" ] && [ "$all" -eq 0 ]; then
    write_error "Укажите сервис (-s) или используйте -a (все)"
    exit 1
fi

health_test "$service"