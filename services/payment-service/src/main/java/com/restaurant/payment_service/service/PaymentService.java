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

    private final RedisService redisService;  /// Redis


    @Value("${payment.max-amount:10000}")
    private BigDecimal maxAmount;

    /**
     * Операция оплаты заказа
     */
    @KafkaListener(topics = "order-service.order.created", groupId = "payment-group")
    public void processPayment(OrderCreatedEvent event) {
        log.info("PaymentService получил событие для заказа #{}", event.getOrderId());

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
            log.warn("Платёж для заказа #{} ОТКАЗАН (сумма {} >= 10000)",
                    event.getOrderId(), event.getTotalAmount());
            result = new PaymentProcessedEvent(
                    event.getOrderId(),
                    "FAILED",
                    "Сумма превышает лимит 10000 руб."
            );
            topic = "payment-service.payment.failed";
        }

        /// Сохраняем платеж в БД
        PaymentTransaction transaction = saveTransaction(event, result);

        /// Сохраняем в Redis кэш
        cacheTransaction(transaction);

        /// Очищаем кэши списков (так как появилась новая транзакция)
        clearListCaches(event.getUserId(), event.getOrderId());

        /// Отправляем в соответствующий топик
        kafkaTemplate.send(topic, result);
        log.info("Результат платежа отправлен в топик '{}'", topic);
    }



    /**
     * Сохранить транзакцию в БД
     */
    @Transactional
    public PaymentTransaction saveTransaction(OrderCreatedEvent event, PaymentProcessedEvent result) {
        PaymentTransaction transaction = PaymentTransaction.builder()
                .orderId(event.getOrderId())
                .userId(event.getUserId())
                .amount(event.getTotalAmount())
                .statusPayment(result.getStatus())
                .message(result.getMessage())
                .createdAt(LocalDateTime.now())
                .build();

        PaymentTransaction saved = paymentTransactionRepository.save(transaction);
        log.info("Платеж сохранен в БД с ID: {}", saved.getId());

        return saved;
    }

    /**
     * Получить все транзакции (для отладки)
     */
    @Transactional
    public List<PaymentTransaction> getAllTransactions() {
        String cacheKey = "payments:all";

        /// Пробуем взять из кэша
        List<PaymentTransaction> cached = redisService.get(cacheKey, List.class);
        if (cached != null) {
            log.info("Получен список всех транзакций из кэша ({} записей)", cached.size());
            return cached;
        }

        /// Нет в кэше - берем из БД
        log.info("Загрузка всех транзакций из БД");
        List<PaymentTransaction> transactions = paymentTransactionRepository.findAll();

        /// Сохраняем в кэш на 2 мин
        redisService.saveWithExpire(cacheKey, transactions, 120);
        log.info("Список всех транзакций сохранен в кэш ({} записей)", transactions.size());

        return transactions;
    }

    /**
     * Получить транзакцию по ID
     */
    @Transactional
    public PaymentTransaction getTransactionById(Long id) {
        String cacheKey = "payment:" + id;

        /// Пробуем взять из кэша
        PaymentTransaction cached = redisService.get(cacheKey, PaymentTransaction.class);
        if (cached != null) {
            log.info("Транзакция #{} получена из кэша", id);
            return cached;
        }

        /// Нет в кэше - берем из БД
        log.info("Загрузка транзакции #{} из БД", id);
        PaymentTransaction transaction = paymentTransactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Транзакция не найдена: " + id));

        /// Сохраняем в кэш на 2 мин
        redisService.saveWithExpire(cacheKey, transaction, 120);
        log.info("Транзакция #{} сохранена в кэш", id);
        return transaction;
    }

    /**
     * Получить все транзакции по ID заказа
     */
    @Transactional
    public List<PaymentTransaction> getTransactionsByOrderId(Long orderId) {
        String cacheKey = "payments:order:" + orderId;

        /// Пробуем взять из кэша
        List<PaymentTransaction> cached = redisService.get(cacheKey, List.class);
        if (cached != null) {
            log.info("Транзакции заказа #{} получены из кэша ({} записей)", orderId, cached.size());
            return cached;
        }

        /// Нет в кэше - берем из БД
        log.info("Загрузка транзакций заказа #{} из БД", orderId);
        List<PaymentTransaction> transactions = paymentTransactionRepository.findByOrderId(orderId);

        /// Сохраняем в кэш на 2 мин
        redisService.saveWithExpire(cacheKey, transactions, 120);
        log.info("Транзакции заказа #{} сохранены в кэш ({} записей)", orderId, transactions.size());

        return transactions;
    }

    /**
     * Получить все транзакции по ID пользователя
     */
    @Transactional
    public List<PaymentTransaction> getTransactionsByUserId(Long userId) {
        String cacheKey = "payments:user:" + userId;

        /// Пробуем взять из кэша
        List<PaymentTransaction> cached = redisService.get(cacheKey, List.class);
        if (cached != null) {
            log.info("Транзакции пользователя #{} получены из кэша ({} записей)", userId, cached.size());
            return cached;
        }

        /// Нет в кэше - берем из БД
        log.info("Загрузка транзакций пользователя #{} из БД", userId);
        List<PaymentTransaction> transactions = paymentTransactionRepository.findByUserId(userId);

        /// Сохраняем в кэш на 2 мин
        redisService.saveWithExpire(cacheKey, transactions, 120);
        log.info("Транзакции пользователя #{} сохранены в кэш ({} записей)", userId, transactions.size());
        return transactions;
    }

    /**
     * Кэширование отдельной транзакции
     */
    private void cacheTransaction(PaymentTransaction transaction) {
        String cacheKey = "payment:" + transaction.getId();
        redisService.saveWithExpire(cacheKey, transaction, 300);
        log.info("Транзакция #{} сохранена в кэш", transaction.getId());
    }

    /**
     * Очистка кэшей списков при добавлении новой транзакции
     */
    private void clearListCaches(Long userId, Long orderId) {
        /// Очищаем общий список
        redisService.delete("payments:all");

        /// Очищаем список по пользователю
        redisService.delete("payments:user:" + userId);

        /// Очищаем список по заказу
        redisService.delete("payments:order:" + orderId);

        log.info("Очищены кэши списков для user#{}, order#{}", userId, orderId);
    }
}
