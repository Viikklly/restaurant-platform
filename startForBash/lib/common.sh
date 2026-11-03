#!/bin/bash
# startForBash/lib/common.sh
# Общие утилиты: проверки, определение путей, ожидание

LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASH_ROOT="$(cd "$LIB_DIR/.." && pwd)"

# FIX: ищем корень проекта вверх по дереву, где лежит docker-compose.yml
# (работает независимо от глубины startForBash)
_find_project_root() {
    local d="$BASH_ROOT"
    local i=0
    while [ "$i" -lt 6 ]; do
        if [ -f "$d/docker-compose.yml" ] || [ -f "$d/docker-compose.infra.yml" ]; then
            echo "$d"
            return 0
        fi
        d="$(cd "$d/.." && pwd)"
        i=$((i+1))
    done
    # fallback — старое поведение
    echo "$(cd "$BASH_ROOT/../.." && pwd)"
}

PROJECT_ROOT="$(_find_project_root)"

source "$LIB_DIR/colors.sh"

check_docker() {
    if ! docker info >/dev/null 2>&1; then
        write_error "Docker Desktop не запущен. Откройте его и подождите."
        exit 1
    fi
}

detect_infra_compose() {
    if [ -f "$PROJECT_ROOT/docker-compose.infra.yml" ]; then
        echo "$PROJECT_ROOT/docker-compose.infra.yml"
    elif [ -f "$PROJECT_ROOT/docker-compose.yml" ]; then
        echo "$PROJECT_ROOT/docker-compose.yml"
    else
        echo ""
    fi
}

detect_main_compose() {
    if [ -f "$PROJECT_ROOT/docker-compose.yml" ]; then
        echo "$PROJECT_ROOT/docker-compose.yml"
    else
        echo ""
    fi
}

detect_loadtest_compose() {
    if [ -f "$PROJECT_ROOT/docker-compose.loadtest.yml" ]; then
        echo "$PROJECT_ROOT/docker-compose.loadtest.yml"
    else
        echo ""
    fi
}

show_containers() {
    write_section "СТАТУС КОНТЕЙНЕРОВ"
    docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" 2>/dev/null
}

wait_for_service() {
    local url="$1"
    local name="$2"
    local max_attempts="${3:-30}"
    local interval="${4:-2}"
    local i

    write_step "Ожидаем $name..."
    for ((i=1; i<=max_attempts; i++)); do
        if curl -s -f -m 2 "$url" >/dev/null 2>&1; then
            write_success "$name готов"
            return 0
        fi
        if (( i % 5 == 0 )); then
            write_warning "Ждем $name... ($i/$max_attempts)"
        fi
        sleep "$interval"
    done
    write_warning "$name не ответил, продолжаем..."
    return 1
}

check_url() {
    local url="$1"
    local timeout="${2:-3}"
    local code
    code=$(curl -s -o /dev/null -w "%{http_code}" -m "$timeout" "$url" 2>/dev/null)
    if [ "$code" == "200" ] || [ "$code" == "204" ]; then
        return 0
    fi
    return 1
}

check_health() {
    local url="$1"
    local timeout="${2:-3}"
    local code
    code=$(curl -s -o /dev/null -w "%{http_code}" -m "$timeout" "$url" 2>/dev/null)
    [ "$code" == "200" ]
}

# Ожидание healthy-статуса Docker-контейнера.
# Использует healthcheck, определённый в docker-compose.yml.
# $1 — имя контейнера (например, restaurant-postgres-order)
# $2 — максимум секунд ожидания (по умолчанию 60)
wait_for_health() {
    local container="$1"
    local max_attempts="${2:-60}"
    local i=0
    local status=""

    write_step "Ожидаем healthy: $container"
    while [ "$i" -lt "$max_attempts" ]; do
        status=$(docker inspect --format='{{.State.Health.Status}}' "$container" 2>/dev/null || echo "not_found")

        case "$status" in
            healthy)
                write_success "$container готов (${i}s)"
                return 0
                ;;
            not_found)
                write_error "$container не найден"
                return 1
                ;;
        esac

        i=$((i + 1))
        sleep 1
    done

    write_error "$container не стал healthy за ${max_attempts}s (текущий: $status)"
    return 1
}

# Ожидание сразу нескольких контейнеров последовательно.
# $1 — таймаут на каждый контейнер
# $@ (со 2-го) — имена контейнеров
wait_for_health_all() {
    local timeout="${1:-60}"
    shift
    local c
    for c in "$@"; do
        wait_for_health "$c" "$timeout" || return 1
    done
    return 0
}