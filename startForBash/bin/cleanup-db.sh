#!/bin/bash
# startForBash/bin/cleanup-db.sh
# Очистка БД после нагрузочных тестов.
#
# ВАЖНО:
# 1. databasechangelog (история Liquibase) НЕ трогается — миграции не перезапускаются.
# 2. Сид-данные (alice/bobi/cap в authdb, Пицца/Паста/Цезарь в orderdb.items)
#    НЕ удаляются — Liquibase не накатит их повторно (changeset уже в databasechangelog).
# 3. Удаляются только "нагрузочные" данные:
#    - users с email LIKE 'load_%' (создаются k6/auth/2-register.js)
#    - orders, order_items (orderdb)
#    - tickets (kitchendb)
#    - payment_transactions (paymentdb)
#    - Redis (FLUSHALL)
# 4. Метрики Prometheus (counter) НЕ сбрасываются — графики продолжат расти.
#
# СКРИПТ ЛИНЕЙНЫЙ (не обёрнут в функцию), поэтому "local" использовать НЕЛЬЗЯ.

START_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="$START_DIR/../lib"
source "$LIB_DIR/common.sh"

write_section "ОЧИСТКА БАЗ ДАННЫХ ПОСЛЕ ТЕСТОВ"

# Проверяем, запущен ли хотя бы контейнер авторизации (он первым стартует)
if ! docker ps --format '{{.Names}}' | grep -q "^restaurant-postgres-auth$"; then
    write_error "Контейнер restaurant-postgres-auth не запущен! Запустите инфраструктуру."
    exit 1
fi

echo "Что вы хотите очистить?"
echo "  1) Только тестовых пользователей в AuthDB (email LIKE 'load_%')"
echo "  2) ВСЕ НАГРУЗОЧНЫЕ ДАННЫЕ (Order + Kitchen + Payment + Redis + тестовые users)"
echo "     Сид-данные (alice/bobi/cap, меню Пицца/Паста/Цезарь) СОХРАНЯЮТСЯ"
echo "  0) Отмена"
read -r -p "Выберите вариант (0-2): " choice

case "$choice" in
    1)
        echo ""
        write_step "Текущее количество пользователей в authdb:"
        docker exec -i restaurant-postgres-auth psql -U postgres -d authdb -t -c "SELECT count(*) FROM users;"

        write_step "Количество тестовых пользователей (load_%):"
        docker exec -i restaurant-postgres-auth psql -U postgres -d authdb -t -c \
            "SELECT count(*) FROM users WHERE email LIKE 'load_%';"

        write_warning "Это удалит только пользователей с email LIKE 'load_%'."
        write_step "  Сид-данные (alice@test.com, bobi@test.com, cap@test.com) останутся."
        read -r -p "Вы уверены? (y/N): " confirm

        if [[ "$confirm" != "y" && "$confirm" != "Y" ]]; then
            write_warning "Отменено"
            exit 0
        fi

        write_step "Удаляем тестовых пользователей (load_%)..."
        docker exec -i restaurant-postgres-auth psql -U postgres -d authdb \
            -c "DELETE FROM users WHERE email LIKE 'load_%';"

        write_success "Тестовые пользователи удалены. Сид-данные сохранены."
        ;;

    2)
        echo ""
        write_warning "ВНИМАНИЕ: Это удалит все нагрузочные данные из Order, Kitchen, Payment баз и Redis!"
        write_warning "  - orderdb: orders, order_items (МЕНЮ items НЕ трогается)"
        write_warning "  - kitchendb: tickets"
        write_warning "  - paymentdb: payment_transactions"
        write_warning "  - authdb: только users с email LIKE 'load_%'"
        write_warning "  - Redis: FLUSHALL"
        write_step "Сид-данные СОХРАНЯЮТСЯ:"
        write_step "  - authdb: alice@test.com, bobi@test.com, cap@test.com"
        write_step "  - orderdb: items (Пицца, Паста, Цезарь)"
        write_warning "Таблицы databasechangelog (история Liquibase) НЕ будут затронуты."
        write_warning "Метрики Prometheus (counter) НЕ сбрасываются — графики продолжат расти."
        write_step "  Для полного сброса метрик: ./clean.sh --all && ./start.sh full"
        read -r -p "Вы уверены? (y/N): " confirm

        if [[ "$confirm" != "y" && "$confirm" != "Y" ]]; then
            write_warning "Отменено"
            exit 0
        fi

        # ---------- Auth DB ----------
        # НЕ TRUNCATE users (удалит сид-данные alice/bobi/cap).
        # Удаляем только тестовых, созданных k6/auth/2-register.js.
        write_step "Очищаем authdb (тестовые пользователи load_%)..."
        docker exec -i restaurant-postgres-auth psql -U postgres -d authdb \
            -c "DELETE FROM users WHERE email LIKE 'load_%';"

        # ---------- Order DB ----------
        # Один TRUNCATE для связанных таблиц. items (меню) НЕ трогаем —
        # без него order-create упадёт (productName='Пицца' не найдено).
        write_step "Очищаем orderdb (order_items, orders)..."
        docker exec -i restaurant-postgres-order psql -U postgres -d orderdb \
            -c "TRUNCATE TABLE order_items, orders RESTART IDENTITY CASCADE;"

        # ---------- Kitchen DB ----------
        write_step "Очищаем kitchendb (tickets)..."
        docker exec -i restaurant-postgres-kitchen psql -U postgres -d kitchendb \
            -c "TRUNCATE TABLE tickets RESTART IDENTITY CASCADE;"

        # ---------- Payment DB ----------
        write_step "Очищаем paymentdb (payment_transactions)..."
        docker exec -i restaurant-postgres-payment psql -U postgres -d paymentdb \
            -c "TRUNCATE TABLE payment_transactions RESTART IDENTITY CASCADE;"

        # ---------- Redis ----------
        write_step "Очищаем кэш Redis..."
        docker exec -i restaurant-redis redis-cli FLUSHALL >/dev/null

        # ---------- Проверка, что сид-данные на месте ----------
        write_step "Проверяем сид-данные..."
        local_auth_count=$(docker exec -i restaurant-postgres-auth psql -U postgres -d authdb -t -c \
            "SELECT count(*) FROM users;")
        local_items_count=$(docker exec -i restaurant-postgres-order psql -U postgres -d orderdb -t -c \
            "SELECT count(*) FROM items;")

        write_step "  authdb.users: $(echo $local_auth_count | tr -d ' ')"
        write_step "  orderdb.items: $(echo $local_items_count | tr -d ' ')"

        if [ "$(echo $local_auth_count | tr -d ' ')" -lt "3" ]; then
            write_warning "  ⚠ Пользователей меньше 3 — возможно, сид-данные повреждены."
            write_step "    Восстановить: пересоздать том authdb (docker-compose rm -f postgres-auth && ...)"
        fi
        if [ "$(echo $local_items_count | tr -d ' ')" -lt "1" ]; then
            write_error "  ⚠ Меню items пусто! Order-create упадёт."
            write_step "    Восстановить: пересоздать том orderdb (docker-compose rm -f postgres-order && ...)"
        fi

        write_success "Нагрузочные данные очищены. Сид-данные сохранены."
        ;;

    0)
        write_warning "Отменено"
        exit 0
        ;;

    *)
        write_error "Неверный выбор"
        exit 1
        ;;
esac