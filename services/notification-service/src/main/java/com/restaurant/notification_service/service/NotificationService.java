package com.restaurant.notification_service.service;

import com.restaurant.common.events.OrderCreatedEvent;
import com.restaurant.common.events.PaymentProcessedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class NotificationService {

    // Слушаем ВСЕ события
    @KafkaListener(
            topics = {"order.created", "payment.processed", "order.paid"},
            groupId = "notification-group"
    )
    public void sendNotification(Object event) {

        // Определяем тип события и отправляем соответствующее уведомление
        if (event instanceof OrderCreatedEvent) {
            OrderCreatedEvent e = (OrderCreatedEvent) event;
            log.info("📧 УВЕДОМЛЕНИЕ ПОЛЬЗОВАТЕЛЮ #{}: Ваш заказ #{} создан на сумму {} руб.",
                    e.getUserId(), e.getOrderId(), e.getTotalAmount());

        } else if (event instanceof PaymentProcessedEvent) {
            PaymentProcessedEvent e = (PaymentProcessedEvent) event;
            String statusText = "SUCCESS".equals(e.getStatus())
                    ? "✅ успешно оплачен"
                    : "❌ НЕ ПРОШЁЛ оплату: " + e.getMessage();
            log.info("📧 УВЕДОМЛЕНИЕ: Заказ #{} {}", e.getOrderId(), statusText);

        } else if (event instanceof Long) {
            Long orderId = (Long) event;
            log.info("📧 УВЕДОМЛЕНИЕ: Заказ #{} передан на кухню и готовится!", orderId);
        }
    }
}
