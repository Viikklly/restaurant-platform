package com.restaurant.notification_service.service;

import com.restaurant.common.events.KitchenOrderReadyEvent;
import com.restaurant.common.events.OrderCreatedEvent;
import com.restaurant.common.events.PaymentProcessedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;


/**
 * Этот сервис слушает события из Kafka и отправляет уведомления пользователям
 * (в нашем случае(пока что) — логирует их).
 */
@Service
@Slf4j
public class NotificationService {

    /**
    * Слушаем топик, что стартует приготовления блюда
     */
    @KafkaListener(topics = "kitchen-service.cooking.started", groupId = "notification-group")
    public void handleCookingStarted(OrderCreatedEvent event) {
        log.info("(NotificationService) УВЕДОМЛЕНИЕ: Заказ #{} начали готовить! Скоро будет готов.", event.getOrderId());
    }

    /**
    * Слушаем топик с заказами
     */
    @KafkaListener(topics = "order-service.order.created", groupId = "notification-group")
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("(NotificationService) УВЕДОМЛЕНИЕ: Заказ #{} создан на сумму {} руб.",
                event.getOrderId(), event.getTotalAmount());
    }

    /**
     *  Слушаем топик с платежами
     */
    @KafkaListener(topics = "payment-service.payment.success", groupId = "notification-group")
    public void handlePaymentSuccess(PaymentProcessedEvent event) {
        log.info("(NotificationService) УВЕДОМЛЕНИЕ: Заказ #{} успешно оплачен!", event.getOrderId());
    }

    /**
     * Слушаем заказ не оплачен
     */
    @KafkaListener(topics = "payment-service.payment.failed", groupId = "notification-group")
    public void handlePaymentFailed(PaymentProcessedEvent event) {
        log.info("(NotificationService) УВЕДОМЛЕНИЕ: Заказ #{} НЕ ОПЛАЧЕН: {}", event.getOrderId(), event.getMessage());
    }

    /**
    * Слушаем топик с кухней
     */
    @KafkaListener(topics = "order-service.order.paid", groupId = "notification-group")
    public void handleOrderPaid(Long orderId) {
        log.info("(NotificationService) УВЕДОМЛЕНИЕ: Заказ #{} передан на кухню и готовится!", orderId);
    }

    /**
    * Слушаем топик с готовыми заказами от кухни
     */
    @KafkaListener(topics = "kitchen-service.order.ready", groupId = "notification-group")
    public void handleOrderReady(KitchenOrderReadyEvent event) {
        log.info("(NotificationService) УВЕДОМЛЕНИЕ: Заказ #{} ГОТОВ к выдаче.",
                event.getOrderId());
    }
}
