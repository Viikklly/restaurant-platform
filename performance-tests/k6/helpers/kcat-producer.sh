#!/usr/bin/env bash
# Отправляет N событий OrderPaidEvent в Kafka
#
# Использование:
#   ./kcat-producer.sh 100
#   KAFFKA_BROKER=kafka:29092 ./kcat-producer.sh 100
# Запуск из контейнера k6:
# docker exec -it restaurant-k6 \

#  /scripts/helpers/kcat-producer.sh 100


N=${1:-100}
TOPIC="order-service.order.paid"
BROKER="${KAFKA_BROKER:-kafka:29092}"

echo "Отправляю $N сообщений в $TOPIC @ $BROKER..."

START=$(date +%s)
for i in $(seq 1 $N); do
  ORDER_ID=$(( (RANDOM * RANDOM) % 100000000 ))
  ITEM=$([ $((i % 2)) -eq 0 ] && echo "Пицца" || echo "Паста")
  echo "{\"orderId\":$ORDER_ID,\"userId\":1,\"orderItemsList\":[\"$ITEM\"],\"totalAmount\":499.99}"
  if (( i % 100 == 0 )); then
    echo "  → отправлено: $i" >&2
  fi
done | kcat -b "$BROKER" -t "$TOPIC" -P

END=$(date +%s)
DURATION=$((END - START))
RATE=$(( N / (DURATION > 0 ? DURATION : 1) ))

echo "Готово: $N сообщений за ${DURATION}s (~${RATE}/s)"