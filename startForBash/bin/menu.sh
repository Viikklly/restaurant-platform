#!/bin/bash
# startForBash/bin/menu.sh
# Интерактивное меню
START_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="$START_DIR/../lib"
source "$LIB_DIR/common.sh"
source "$LIB_DIR/ui.sh"

wait_enter() { read -r -p "Нажмите Enter для продолжения..." _; }

while true; do
    show_menu
    read -r -p "Выберите действие (0-25): " choice
    case "$choice" in
        1)  "$START_DIR/start.sh" full ;;
        2)  "$START_DIR/start.sh" dev ;;
        3)  "$START_DIR/stop.sh" ;;
        4)  "$START_DIR/stop.sh" --clean ;;
        5)  "$START_DIR/status.sh" ;;
        6)  write_step "Ctrl+C для выхода из режима наблюдения"
            "$START_DIR/status.sh" --watch ;;
        7)  write_step "Ctrl+C для выхода из логов"
            "$START_DIR/logs.sh" -a -f ;;
        8)  read -r -p "Имя сервиса: " svc
            if [ -n "$svc" ]; then
                write_step "Ctrl+C для выхода из логов"
                "$START_DIR/logs.sh" -s "$svc" -f
            fi ;;
        9)  "$START_DIR/test.sh" -a ;;
        10) show_urls ;;
        11) "$START_DIR/monitor.sh" start ;;
        12) write_step "Ctrl+C для выхода из логов мониторинга"
            "$START_DIR/logs.sh" -m -s prometheus -f ;;
        13) "$START_DIR/monitor.sh" check ;;
        14) "$START_DIR/eureka.sh" ;;
        15) "$START_DIR/clean.sh" ;;
        16) "$START_DIR/clean.sh" --all --force ;;
        17) "$START_DIR/loadtest.sh" --up ;;
        18) "$START_DIR/loadtest.sh" --down ;;
        19) "$START_DIR/loadtest.sh" --status ;;
        20) "$START_DIR/loadtest.sh" --list ;;
        21) "$START_DIR/loadtest.sh" --list
            read -r -p "k6-тест (Enter=scenarios/0-smoke.js): " f
            f="${f:-scenarios/0-smoke.js}"

            # Спрашиваем RATE только для E2E и stress
            r=""
            if [[ "$f" == *"5-full-flow-e2e.js"* ]] || [[ "$f" == *"3-order-stress.js"* ]]; then
                read -r -p "RATE (Enter=0.1, 0.3 - stress, 1.0 - extreme): " r
            fi

            # Генерируем уникальный testid
            base_name=$(basename "$f" .js)
            testid="${base_name}-$(date +%Y%m%d-%H%M%S)"

            # Собираем аргументы: всегда --rw и --tag
            args=(--tool k6 --test "$f" --rw --tag "$testid")
            [ -n "$r" ] && args+=(--rate "$r")

            write_step "  TESTID=$testid"
            "$START_DIR/loadtest.sh" "${args[@]}" ;;
        22) "$START_DIR/loadtest.sh" --list
            read -r -p "JMeter-тест (Enter=auth-load-test.jmx): " f
            "$START_DIR/loadtest.sh" --tool jmeter --test "${f:-auth-load-test.jmx}" ;;
        23) "$START_DIR/loadtest.sh" --list
            read -r -p "Gatling класс (Enter=ApiLoadTest): " c
            "$START_DIR/loadtest.sh" --tool gatling --test "${c:-ApiLoadTest}" ;;
        24) write_step "Внимание: веб-дашборд откроется на http://localhost:5665"
            write_step "Тест начнется сразу. Для остановки нажмите Ctrl+C в терминале."
            "$START_DIR/loadtest.sh" --list
            read -r -p "k6-тест с дашбордом (Enter=scenarios/0-smoke.js): " f
            "$START_DIR/loadtest.sh" --dashboard "${f:-scenarios/0-smoke.js}" ;;
        25) "$START_DIR/cleanup-db.sh" ;;
        0)  write_success "До свидания!"; exit 0 ;;
        *)  write_error "Неверный выбор!"; sleep 1; continue ;;
    esac
    wait_enter
done