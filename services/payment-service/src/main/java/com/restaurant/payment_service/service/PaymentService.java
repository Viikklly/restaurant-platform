package com.restaurant.payment_service.service;

import com.restaurant.common.events.OrderCreatedEvent;
import com.restaurant.common.events.PaymentProcessedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    // Слушаем топик, куда Order Service отправляет новые заказы
    @KafkaListener(topics = "order.created", groupId = "payment-group")
    public void processPayment(OrderCreatedEvent event) {
        log.info("💳 Payment Service получил заказ #{} на сумму {} руб.",
                event.getOrderId(), event.getTotalAmount());

        // Симулируем обработку платежа
        // По ТЗ: если сумма < 10000 руб. - успех, иначе отказ
        PaymentProcessedEvent result;

        if (event.getTotalAmount().compareTo(BigDecimal.valueOf(10000)) < 0) {
            log.info(" Платёж для заказа #{} УСПЕШЕН", event.getOrderId());
            result = new PaymentProcessedEvent(
                    event.getOrderId(),
                    "SUCCESS",
                    "Платёж одобрен"
            );
        } else {
            log.warn(" Платёж для заказа #{} ОТКАЗАН (сумма {} >= 10000)",
                    event.getOrderId(), event.getTotalAmount());
            result = new PaymentProcessedEvent(
                    event.getOrderId(),
                    "FAILED",
                    "Сумма превышает лимит 10000 руб."
            );
        }

        // Отправляем результат обратно в Kafka
        kafkaTemplate.send("payment.processed", result);
        log.info(" Результат платежа отправлен в топик 'payment.processed'");
    }
}
