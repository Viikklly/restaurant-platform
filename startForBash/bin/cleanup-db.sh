#!/bin/bash
# startForBash/bin/cleanup-db.sh
# Очистка БД после нагрузочных тестов

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
echo "  1) Только пользователей в AuthDB (таблица users)"
echo "  2) ВСЕ ДАННЫЕ (AuthDB + OrderDB + KitchenDB + PaymentDB + Redis)"
echo "  0) Отмена"
read -r -p "Выберите вариант (0-2): " choice

case "$choice" in
    1)
        echo ""
        write_step "Текущее количество пользователей в authdb:"
        docker exec -i restaurant-postgres-auth psql -U postgres -d authdb -t -c "SELECT count(*) FROM users;"

        write_warning "Это удалит ВСЕХ пользователей (включая bobi@test.com)."
        read -r -p "Вы уверены? (y/N): " confirm

        if [[ "$confirm" != "y" && "$confirm" != "Y" ]]; then
            write_warning "Отменено"
            exit 0
        fi

        write_step "Очищаем users..."
        docker exec -i restaurant-postgres-auth psql -U postgres -d authdb -c "TRUNCATE TABLE users CASCADE;"

        write_step "Перезапускаем auth-service (15 сек)..."
        docker restart restaurant-auth-service >/dev/null
        sleep 15
        write_success "AuthDB очищена. Пользователи восстановлены."
        ;;

    2)
        echo ""
        write_warning "ВНИМАНИЕ: Это удалит ВСЕ данные из Auth, Order, Kitchen и Payment баз!"
        write_warning "Все заказы, блюда, тикеты и транзакции будут удалены."
        read -r -p "Вы уверены? (y/N): " confirm

        if [[ "$confirm" != "y" && "$confirm" != "Y" ]]; then
            write_warning "Отменено"
            exit 0
        fi

        # Auth DB (не трогаем databasechangelog!)
        write_step "Очищаем authdb (users)..."
        docker exec -i restaurant-postgres-auth psql -U postgres -d authdb -c "TRUNCATE TABLE users CASCADE;"

        # Order DB (очищаем заказы и позиции заказов, но оставляем меню - items)
        write_step "Очищаем orderdb (orders, order_items)..."
        docker exec -i restaurant-postgres-order psql -U postgres -d orderdb -c "TRUNCATE TABLE orders CASCADE;"
        docker exec -i restaurant-postgres-order psql -U postgres -d orderdb -c "TRUNCATE TABLE order_items CASCADE;"

        # Kitchen DB
        write_step "Очищаем kitchendb (tickets)..."
        docker exec -i restaurant-postgres-kitchen psql -U postgres -d kitchendb -c "TRUNCATE TABLE tickets CASCADE;"

        # Payment DB
        write_step "Очищаем paymentdb (payment_transactions)..."
        docker exec -i restaurant-postgres-payment psql -U postgres -d paymentdb -c "TRUNCATE TABLE payment_transactions CASCADE;"

        # Redis (кэш тикетов и заказов)
        write_step "Очищаем кэш Redis..."
        docker exec -i restaurant-redis redis-cli FLUSHALL >/dev/null

        # Перезапуск сервисов для восстановления меню и дефолтных пользователей
        write_step "Перезапускаем auth-service, order-service, kitchen-service и payment-service (15 сек)..."
        docker restart restaurant-auth-service restaurant-order-service restaurant-kitchen-service restaurant-payment-service >/dev/null
        sleep 15

        write_success "Все базы данных и Redis очищены. Сервисы перезапущены."
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