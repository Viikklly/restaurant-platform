#!/bin/bash
# startForBash/lib/stack.sh
LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$LIB_DIR/common.sh"
source "$LIB_DIR/ui.sh"

stack_full() {
    write_section "ПОЛНЫЙ ЗАПУСК"
    check_docker

    local compose
    compose=$(detect_main_compose)
    if [ -z "$compose" ]; then
        write_error "docker-compose.yml не найден в $PROJECT_ROOT"
        exit 1
    fi
    write_step "Используем: $compose"

    write_step "Проверка и удаление ZooKeeper..."
    if docker ps -a --format '{{.Names}}' | grep -q "restaurant-zookeeper"; then
        write_warning "Найден старый контейнер ZooKeeper. Удаляем..."
        docker rm -f restaurant-zookeeper 2>/dev/null
        write_success "ZooKeeper удалён"
    else
        write_success "ZooKeeper не найден"
    fi

    write_step "Останавливаем старые контейнеры..."
    docker-compose -f "$compose" down >/dev/null 2>&1

    write_step "Запускаем БД и брокеры..."
    docker-compose -f "$compose" up -d postgres-auth postgres-order postgres-kitchen postgres-payment redis kafka
    write_success "БД и брокеры запущены"
    sleep 15

    write_step "Запускаем мониторинг..."
    docker-compose -f "$compose" up -d prometheus grafana alertmanager loki promtail jaeger
    write_success "Мониторинг запущен"
    sleep 15

    write_step "Запускаем Discovery Service..."
    docker-compose -f "$compose" up -d discovery-service
    write_success "Discovery Service запущен"
    sleep 15

    write_step "Запускаем Config Service..."
    docker-compose -f "$compose" up -d config-service
    write_success "Config Service запущен"
    sleep 30

    write_step "Запускаем микросервисы..."
    docker-compose -f "$compose" up -d gateway-service auth-service order-service kitchen-service payment-service notification-service
    write_success "Микросервисы запущены"
    sleep 20

    health_monitoring

    write_section "ГОТОВО!"
    show_containers
    show_urls
}

stack_dev() {
    write_section "ЗАПУСК ДЛЯ РАЗРАБОТКИ"
    check_docker

    local compose
    compose=$(detect_infra_compose)
    if [ -z "$compose" ]; then
        write_error "Compose-файл не найден!"
        exit 1
    fi
    write_step "Используем: $compose"

    write_step "Очистка старых контейнеров..."
    docker-compose -f "$compose" down -v >/dev/null 2>&1
    docker-compose -f "$compose" down --remove-orphans >/dev/null 2>&1
    write_success "Очистка выполнена"

    write_step "Запуск инфраструктуры и мониторинга..."
    docker-compose -f "$compose" up -d || { write_error "Ошибка запуска"; exit 1; }
    write_success "Запуск выполнен"

    write_step "Ожидание инициализации..."
    sleep 15

    write_section "ГОТОВО!"
    show_containers
    show_urls

    write_success "\n Запуск микросервисов через IDE:"
    write_step "    1. Discovery    → http://localhost:8761"
    write_step "    2. Config       → http://localhost:8888"
    write_step "    3. Auth         → http://localhost:8081"
    write_step "    4. Order        → http://localhost:8082"
    write_step "    5. Kitchen      → http://localhost:8083"
    write_step "    6. Payment      → http://localhost:8084"
    write_step "    7. Notification → http://localhost:8086"
    write_step "    8. Gateway      → http://localhost:8090"
}

stack_stop() {
    local clean="${1:-0}"
    local infra="${2:-0}"

    write_section "ОСТАНОВКА"

    if [ "$infra" -eq 1 ]; then
        local compose
        compose=$(detect_infra_compose)
        if [ -z "$compose" ]; then
            write_error "Compose-файл для инфраструктуры не найден!"
            exit 1
        fi
        write_step "Останавливаем инфраструктуру ($compose)..."
        if [ "$clean" -eq 1 ]; then
            docker-compose -f "$compose" down -v
            write_success "Инфраструктура остановлена, тома удалены"
        else
            docker-compose -f "$compose" down
            write_success "Инфраструктура остановлена"
        fi
    else
        local compose
        compose=$(detect_main_compose)
        if [ -z "$compose" ]; then
            write_error "docker-compose.yml не найден в $PROJECT_ROOT"
            exit 1
        fi
        write_step "Останавливаем все сервисы..."
        if [ "$clean" -eq 1 ]; then
            docker-compose -f "$compose" down -v
            write_success "Все сервисы остановлены, тома удалены"
        else
            docker-compose -f "$compose" down
            write_success "Все сервисы остановлены"
        fi
    fi

    show_containers
}