#!/bin/bash
# startForBash/lib/loadtest.sh
# Нагрузочное тестирование (только функции)

LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$LIB_DIR/common.sh"

COMPOSE_LOADTEST="docker-compose.loadtest.yml"
K6_DEFAULT_TEST="scenarios/0-smoke.js"

test_main_stack() {
    if ! docker network ls --filter "name=restaurant-network" --format "{{.Name}}" 2>/dev/null | grep -q "restaurant-network"; then
        write_warning "Сеть restaurant-network не найдена!"
        write_step "  Сначала запустите основной стек: ./start.sh full"
        return 1
    fi
    return 0
}

show_tests() {
    write_step "Доступные тесты k6:"
    if [ -d "$PROJECT_ROOT/performance-tests/k6" ]; then
        # Ищем рекурсивно, исключаем results и lib, выводим пути относительно папки k6
        find "$PROJECT_ROOT/performance-tests/k6" -name "*.js" -not -path "*/results/*" -not -path "*/lib/*" | \
            sed "s|$PROJECT_ROOT/performance-tests/k6/||" | \
            sed 's/^/    - /'
    else
        write_warning "    (папка performance-tests/k6 не найдена)"
    fi

    write_step "Доступные тесты JMeter:"
    if [ -d "$PROJECT_ROOT/performance-tests/jmeter" ]; then
        find "$PROJECT_ROOT/performance-tests/jmeter" -name "*.jmx" -not -path "*/results/*" | \
            sed "s|$PROJECT_ROOT/performance-tests/jmeter/||" | \
            sed 's/^/    - /'
    else
        write_warning "    (папка performance-tests/jmeter не найдена)"
    fi

    write_step "Доступные симуляции Gatling:"
    if [ -d "$PROJECT_ROOT/performance-tests/gatling/simulations" ]; then
        ls -1 "$PROJECT_ROOT/performance-tests/gatling/simulations" 2>/dev/null | sed 's/^/    - /'
    else
        write_warning "    (папка performance-tests/gatling/simulations не найдена)"
    fi
}

show_loadtest_status() {
    write_step "Статус генераторов нагрузки:"
    local containers=("restaurant-k6" "restaurant-gatling" "restaurant-jmeter") c status exists
    for c in "${containers[@]}"; do
        status=$(docker ps --filter "name=$c" --format "{{.Status}}" 2>/dev/null)
        if [ -n "$status" ]; then
            write_success "  $c - $status"
        else
            exists=$(docker ps -a --filter "name=$c" --format "{{.Status}}" 2>/dev/null)
            if [ -n "$exists" ]; then
                write_warning "  $c - ОСТАНОВЛЕН"
            else
                write_step "  $c - НЕ СОЗДАН"
            fi
        fi
    done
}

start_loadtest() {
    test_main_stack || return 1
    write_step "Поднимаем генераторы нагрузки..."
    ( cd "$PROJECT_ROOT" && docker-compose -f "$COMPOSE_LOADTEST" up -d ) \
        && write_success "Генераторы запущены" \
        || write_error "Ошибка запуска"
}

stop_loadtest() {
    write_step "Останавливаем генераторы нагрузки..."
    ( cd "$PROJECT_ROOT" && docker-compose -f "$COMPOSE_LOADTEST" down ) \
        && write_success "Генераторы остановлены"
}

# Очистка имени файла от случайных дефисов и пробелов
_clean_k6_path() {
    local f="$1"
    f="${f#- }"      # убираем "- " в начале, если есть
    f="${f# }"       # убираем пробел в начале
    echo "$f"
}


# Запуск k6 с поддержкой RATE, TESTID и отправкой в Prometheus
invoke_k6() {
    local file="${1:-$K6_DEFAULT_TEST}"
    local rate="${2:-}"
    local tag="${3:-}"
    local rw="${4:-0}" # 1 = включить remote-write
    file="$(_clean_k6_path "$file")"

    # Формируем команду
    local cmd=(k6 run)

    # если rw=1, добавляем флаг
    [ "$rw" -eq 1 ] && cmd+=(--out experimental-prometheus-rw)

    # RATE — переменная окружения (читается в k6 через __ENV.RATE)
    [ -n "$rate" ] && cmd+=(-e "RATE=$rate")

    # TESTID — это TAG метрики (label testid в Prometheus), а НЕ env.
    # -e TESTID=... добавляет env, но НЕ label к метрикам.
    # --tag testid=... добавляет label testid="..." ко всем метрикам k6
    # → в Prometheus можно фильтровать {testid="order-read-..."} → в Grafana работает.
    [ -n "$tag" ]  && cmd+=(--tag "testid=$tag")

    cmd+=("/scripts/$file")

    write_step "Запуск k6: $file"
    [ -n "$rate" ] && write_step "  RATE=$rate"
    [ -n "$tag" ]  && write_step "  TESTID=$tag"
    [ "$rw" -eq 1 ] && write_step "  + отправка в Prometheus"

    docker exec -it restaurant-k6 "${cmd[@]}"
}

# Запуск теста с веб-дашбордом (с поддержкой RATE и TESTID)
invoke_k6_dashboard() {
    local file="${1:-$K6_DEFAULT_TEST}"
    local rate="${2:-}"
    local tag="${3:-}"
    file="$(_clean_k6_path "$file")"

    if ! docker ps --format '{{.Names}}' | grep -q "^restaurant-k6$"; then
        write_error "Контейнер restaurant-k6 не запущен!"
        return 1
    fi

    local cmd=(k6 run --out web-dashboard)
    [ -n "$rate" ] && cmd+=(-e "RATE=$rate")
    [ -n "$tag" ]  && cmd+=(--tag "testid=$tag")
    cmd+=("/scripts/$file")

    write_step "Запуск k6 с веб-дашбордом: $file"
    [ -n "$rate" ] && write_step "  RATE=$rate"
    [ -n "$tag" ]  && write_step "  TESTID=$tag"
    write_step "Дашборд будет доступен по адресу: http://localhost:5665"
    write_step "Для выхода нажмите Ctrl+C (это не остановит тест, он завершится сам)"

    docker exec -it restaurant-k6 "${cmd[@]}"
}

invoke_jmeter() {
    local file="${1:-auth-load-test.jmx}"
    local ts
    ts=$(date +"%Y-%m-%d_%H-%M-%S")
    write_step "Запуск JMeter: $file"
    docker exec -it restaurant-jmeter jmeter -n -t "/tests/$file" \
        -l "/results/result-$ts.jtl" -e -o "/results/html-$ts"
    write_success "HTML-отчёт: performance-tests/jmeter/results/html-$ts/"
}

invoke_gatling() {
    local class="${1:-ApiLoadTest}"
    write_step "Запуск Gatling: $class"
    docker exec -it restaurant-gatling gatling.sh -s "$class"
    write_success "HTML-отчёт: performance-tests/gatling/results/"
}