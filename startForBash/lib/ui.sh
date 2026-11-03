#!/bin/bash
# startForBash/lib/ui.sh
# Визуальные элементы: список URL, меню (только функции)

LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$LIB_DIR/colors.sh"

show_urls() {
    write_section "ДОСТУПНЫЕ СЕРВИСЫ"

    echo -e "\n  ${CYAN}ИНФРАСТРУКТУРА:${NC}"
    echo "    PostgreSQL Auth:    localhost:5437 (authdb/postgres/postgres)"
    echo "    PostgreSQL Order:   localhost:5433 (orderdb/postgres/postgres)"
    echo "    PostgreSQL Kitchen: localhost:5434 (kitchendb/postgres/postgres)"
    echo "    PostgreSQL Payment: localhost:5435 (paymentdb/postgres/postgres)"
    echo "    Redis:              localhost:6379"
    echo "    Kafka:              localhost:9092"
    echo "    Zookeeper:          localhost:2181"

    echo -e "\n  ${CYAN}UI/ВИЗУАЛИЗАЦИЯ:${NC}"
    echo "    PGAdmin:     http://localhost:5050 (admin@restaurant.com/admin)"
    echo "    Kafka UI:    http://localhost:8085"

    echo -e "\n  ${CYAN}МОНИТОРИНГ:${NC}"
    echo "    Prometheus:   http://localhost:9090"
    echo "    Grafana:      http://localhost:3000 (admin/admin)"
    echo "    Alertmanager: http://localhost:9093"
    echo "    Loki:         http://localhost:3100"
    echo "    Jaeger:       http://localhost:16686"

    echo -e "\n  ${CYAN}МИКРОСЕРВИСЫ:${NC}"
    echo "    Gateway:      http://localhost:8090"
    echo "    Auth:         http://localhost:8081"
    echo "    Order:        http://localhost:8082"
    echo "    Kitchen:      http://localhost:8083"
    echo "    Payment:      http://localhost:8084"
    echo "    Notification: http://localhost:8086"
    echo "    Eureka:       http://localhost:8761"
    echo "    Config:       http://localhost:8888"
}

show_menu() {
    write_section "УПРАВЛЕНИЕ МИКРОСЕРВИСАМИ"
    cat <<'EOF'

   ОСНОВНОЕ
  1. Полный запуск всех сервисов
  2. Запуск инфраструктуры (разработка)
  3. Остановка всех сервисов
  4. Остановка с очисткой томов
  5. Статус контейнеров
  6. Статус с автообновлением
  7. Логи (все сервисы)
  8. Логи (конкретный сервис)
  9. Тестирование всех сервисов
 10. Показать все URL

  МОНИТОРИНГ
 11. Запуск только мониторинга
 12. Логи мониторинга
 13. Проверить мониторинг
 14. Проверить Eureka

  ОЧИСТКА
 15. Очистка Docker системы
 16. Полная очистка (все образы + тома)

 НАГРУЗОЧНОЕ ТЕСТИРОВАНИЕ
 17. Поднять генераторы нагрузки
 18. Остановить генераторы нагрузки
 19. Статус генераторов нагрузки
 20. Список доступных тестов
 21. Запустить тест k6
 22. Запустить тест JMeter
 23. Запустить симуляцию Gatling
 24. Запустить тест k6 с веб-дашбордом (графики)
 25. Очистить БД после тестов (load_%, mix_%)

  0. Выход
EOF
}