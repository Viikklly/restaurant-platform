package com.restaurant.kitchen_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class KitchenService {

    // Слушаем топик, куда Order Service отправляет оплаченные заказы
    @KafkaListener(topics = "order.paid", groupId = "kitchen-group")
    public void startCooking(Long orderId) {
        log.info(" КУХНЯ: Получен заказ #{} для приготовления", orderId);

        // Симуляция процесса приготовления
        try {
            log.info("🍳 Начинаем готовить заказ #{}...", orderId);

            // Имитируем долгую работу (2 секунды)
            Thread.sleep(2000);

            log.info("Заказ #{} ГОТОВ к выдаче!", orderId);

            // TODO: Отправить событие KitchenOrderReadyEvent в Kafka
            // kafkaTemplate.send("kitchen.order.ready", new KitchenOrderReadyEvent(orderId, ...));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("❌ Приготовление заказа #{} прервано", orderId);
        }
    }
}