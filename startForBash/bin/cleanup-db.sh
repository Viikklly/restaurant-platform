#!/bin/bash
# startForBash/bin/cleanup-db.sh
# Очистка БД после нагрузочных тестов.
#
# ВАЖНО: мы НЕ трогаем таблицу databasechangelog (историю миграций Liquibase).
# TRUNCATE затрагивает только бизнес-данные. После перезапуска сервисов
# Liquibase увидит, что все changeset'ы уже применены, и не будет их накатывать заново.
#
# СКРИПТ ЛИНЕЙНЫЙ (не обёрнут в функцию), поэтому "local" здесь использовать НЕЛЬЗЯ —
# Bash выдаст "local: can only be used in a function".

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
        docker exec -i restaurant-postgres-auth psql -U postgres -d authdb \
            -c "TRUNCATE TABLE users RESTART IDENTITY CASCADE;"

        write_step "Перезапускаем auth-service..."
        docker restart restaurant-auth-service >/dev/null

        # Ждём healthcheck auth-service
        i=0
        ok=0
        while [ "$i" -lt 60 ]; do
            if check_health "http://localhost:8081/actuator/health" 2; then
                ok=1
                break
            fi
            i=$((i + 1))
            sleep 1
        done

        if [ "$ok" -eq 1 ]; then
            write_success "AuthDB очищена. auth-service готов."
        else
            write_warning "auth-service не ответил за 60s — проверь: docker logs restaurant-auth-service"
        fi
        ;;

    2)
        echo ""
        write_warning "ВНИМАНИЕ: Это удалит ВСЕ данные из Auth, Order, Kitchen и Payment баз!"
        write_warning "Все заказы, блюда, тикеты и транзакции будут удалены."
        write_warning "Таблицы databasechangelog (история Liquibase) НЕ будут затронуты."
        write_warning "Метрики Prometheus (counter) НЕ сбрасываются — графики продолжат расти."
        write_step "  Для полного сброса метрик: ./clean.sh --all && ./start.sh full"
        read -r -p "Вы уверены? (y/N): " confirm

        if [[ "$confirm" != "y" && "$confirm" != "Y" ]]; then
            write_warning "Отменено"
            exit 0
        fi

        # ---------- Auth DB ----------
        write_step "Очищаем authdb (users)..."
        docker exec -i restaurant-postgres-auth psql -U postgres -d authdb \
            -c "TRUNCATE TABLE users RESTART IDENTITY CASCADE;"

        # ---------- Order DB ----------
        # Один TRUNCATE для связанных таблиц: order_items и orders.
        # RESTART IDENTITY сбрасывает последовательности.
        # Меню (items) НЕ трогаем — оно нужно для нагрузочных тестов.
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

        # ---------- Перезапуск сервисов ----------
        write_step "Перезапускаем auth-service, order-service, kitchen-service и payment-service..."
        docker restart \
            restaurant-auth-service \
            restaurant-order-service \
            restaurant-kitchen-service \
            restaurant-payment-service >/dev/null

        # ---------- Ожидание готовности (без local!) ----------
        write_step "Ждём готовности сервисов (healthcheck)..."
        for s in \
            restaurant-auth-service \
            restaurant-order-service \
            restaurant-kitchen-service \
            restaurant-payment-service; do
            # Явное соответствие контейнер → порт.
            # Имя контейнера не заканчивается цифрой, поэтому извлечь порт
            # через regex нельзя — используем case.
            case "$s" in
                restaurant-auth-service)    port=8081 ;;
                restaurant-order-service)   port=8082 ;;
                restaurant-kitchen-service) port=8083 ;;
                restaurant-payment-service) port=8084 ;;
                *)                          port=""   ;;
            esac

            i=0
            ok=0
            while [ "$i" -lt 60 ]; do
                if [ -n "$port" ] && check_health "http://localhost:$port/actuator/health" 2; then
                    ok=1
                    break
                fi
                i=$((i + 1))
                sleep 1
            done

            if [ "$ok" -eq 1 ]; then
                write_success "$s готов"
            else
                write_warning "$s не ответил за 60s — проверь: docker logs $s"
            fi
        done

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