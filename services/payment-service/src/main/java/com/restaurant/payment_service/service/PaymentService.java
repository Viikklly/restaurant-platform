package com.restaurant.payment_service.service;

import com.restaurant.common.events.OrderCreatedEvent;
import com.restaurant.common.events.PaymentProcessedEvent;
import com.restaurant.payment_service.entity.PaymentTransaction;
import com.restaurant.payment_service.repository.PaymentTransactionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Сервис платежа
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {


    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final PaymentTransactionRepository paymentTransactionRepository;


    @Value("${payment.max-amount:10000}")
    private BigDecimal maxAmount;

    /**
     * Операция оплаты заказа
     */
    @KafkaListener(topics = "order-service.order.created", groupId = "payment-group")
    public void processPayment(OrderCreatedEvent event) {
        log.info(" PaymentService получил событие для заказа #{}", event.getOrderId());

        PaymentProcessedEvent result;
        String topic;

        /// Сравниваем сумму с лимитом 10000
        if (event.getTotalAmount().compareTo(maxAmount) < 0) {
            log.info("Платёж для заказа #{} УСПЕШЕН", event.getOrderId());
            result = new PaymentProcessedEvent(
                    event.getOrderId(),
                    "SUCCESS",
                    "Платёж одобрен"
            );
            topic = "payment-service.payment.success";
        } else {
            log.warn(" Платёж для заказа #{} ОТКАЗАН (сумма {} >= 10000)",
                    event.getOrderId(), event.getTotalAmount());
            result = new PaymentProcessedEvent(
                    event.getOrderId(),
                    "FAILED",
                    "Сумма превышает лимит 10000 руб."
            );
            topic = "payment-service.payment.failed";
        }

        /// Сохраняем платеж в БД
        saveTransaction(event, result);
        log.info("Платеж сохранен в БД");


        /// Отправляем в соответствующий топик
        kafkaTemplate.send(topic, result);
        log.info(" Результат платежа отправлен в топик '{}'", topic);
    }



    @Transactional
    public void saveTransaction(OrderCreatedEvent event, PaymentProcessedEvent result) {
        PaymentTransaction transaction = PaymentTransaction.builder()
                .orderId(event.getOrderId())
                .userId(event.getUserId())
                .amount(event.getTotalAmount())
                .statusPayment(result.getStatus())
                .message(result.getMessage())
                .build();
        paymentTransactionRepository.save(transaction);
    }

    /**
     * Получить все транзакции (для отладки)
     */
    @Transactional
    public List<PaymentTransaction> getAllTransactions() {
        log.info(" Получение всех транзакций");
        return paymentTransactionRepository.findAll();
    }

    /**
     * Получить транзакцию по ID
     */
    @Transactional
    public PaymentTransaction getTransactionById(Long id) {
        log.info(" Получение транзакции по ID: {}", id);
        return paymentTransactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Транзакция не найдена: " + id));
    }

    /**
     * Получить все транзакции по ID заказа
     */
    @Transactional
    public List<PaymentTransaction> getTransactionsByOrderId(Long orderId) {
        log.info(" Получение транзакций по заказу: {}", orderId);
        return paymentTransactionRepository.findByOrderId(orderId);
    }

    /**
     * Получить все транзакции по ID пользователя
     */
    @Transactional
    public List<PaymentTransaction> getTransactionsByUserId(Long userId) {
        log.info(" Получение транзакций по пользователю: {}", userId);
        return paymentTransactionRepository.findByUserId(userId);
    }
}
