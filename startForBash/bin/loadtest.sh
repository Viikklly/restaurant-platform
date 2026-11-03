#!/bin/bash
# startForBash/bin/loadtest.sh
# Использование:
#   ./loadtest.sh --up | --down | --status | --list
#   ./loadtest.sh --tool k6 --test scenarios/0-smoke.js
#   ./loadtest.sh --tool k6 --test scenarios/order/5-full-flow-e2e.js --rate 0.1
#   ./loadtest.sh --tool k6 --test scenarios/0-smoke.js --rw
#   ./loadtest.sh --dashboard scenarios/order/5-full-flow-e2e.js --rate 0.3
#   ./loadtest.sh --tool jmeter --test auth-load-test.jmx
#   ./loadtest.sh --tool gatling --test ApiLoadTest

START_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="$START_DIR/../lib"
source "$LIB_DIR/common.sh"
source "$LIB_DIR/loadtest.sh"

tool=""; testfile=""; up=0; down=0; status=0; list=0; dashboard=0
rate=""; tag=""; rw=0

while [ $# -gt 0 ]; do
    case "$1" in
        --up)        up=1; shift ;;
        --down)      down=1; shift ;;
        --status)    status=1; shift ;;
        --list)      list=1; shift ;;
        --tool)      tool="$2"; shift 2 ;;
        --test)      testfile="$2"; shift 2 ;;
        --rate)      rate="$2"; shift 2 ;;
        --tag)       tag="$2"; shift 2 ;;
        --rw)        rw=1; shift ;;
        --dashboard) dashboard=1; testfile="$2"; shift 2 ;;
        -h|--help)
            echo "Использование:"
            echo "  ./loadtest.sh --up | --down | --status | --list"
            echo "  ./loadtest.sh --tool k6 --test scenarios/0-smoke.js"
            echo "  ./loadtest.sh --tool k6 --test scenarios/order/5-full-flow-e2e.js --rate 0.1"
            echo "  ./loadtest.sh --tool k6 --test scenarios/0-smoke.js --rw"
            echo "  ./loadtest.sh --dashboard scenarios/order/5-full-flow-e2e.js --rate 0.3"
            echo "  ./loadtest.sh --tool jmeter --test auth-load-test.jmx"
            echo "  ./loadtest.sh --tool gatling --test ApiLoadTest"
            exit 0 ;;
        *) write_error "Неизвестный аргумент: $1"; exit 1 ;;
    esac
done

check_docker
if [ ! -f "$PROJECT_ROOT/$COMPOSE_LOADTEST" ]; then
    write_error "Файл $COMPOSE_LOADTEST не найден в $PROJECT_ROOT"
    exit 1
fi

# Обработка флагов (порядок важен: сначала простые действия)
[ "$list" -eq 1 ]      && { show_tests; exit 0; }
[ "$status" -eq 1 ]    && { show_loadtest_status; exit 0; }
[ "$up" -eq 1 ]        && { start_loadtest; exit $?; }
[ "$down" -eq 1 ]      && { stop_loadtest; exit $?; }
[ "$dashboard" -eq 1 ] && { invoke_k6_dashboard "$testfile" "$rate"; exit $?; }

# Запуск конкретного инструмента
if [ -n "$tool" ]; then
    case "$tool" in
        k6)      invoke_k6 "$testfile" "$rate" "$tag" "$rw" ;;
        jmeter)  invoke_jmeter "$testfile" ;;
        gatling) invoke_gatling "$testfile" ;;
        *) write_error "Неизвестный инструмент: $tool (k6, jmeter, gatling)"; exit 1 ;;
    esac
else
    write_step "Использование:"
    write_step "  ./loadtest.sh --up | --down | --status | --list"
    write_step "  ./loadtest.sh --tool k6 --test scenarios/0-smoke.js"
    write_step "  ./loadtest.sh --tool k6 --test scenarios/order/5-full-flow-e2e.js --rate 0.1"
    write_step "  ./loadtest.sh --dashboard scenarios/order/5-full-flow-e2e.js --rate 0.3"
fi