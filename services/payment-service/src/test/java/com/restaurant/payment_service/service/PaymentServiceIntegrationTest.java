package com.restaurant.payment_service.service;

import com.restaurant.common.events.OrderCreatedEvent;
import com.restaurant.common.events.PaymentProcessedEvent;
import com.restaurant.payment_service.entity.PaymentTransaction;
import com.restaurant.payment_service.metrics.KafkaMetrics;
import com.restaurant.payment_service.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.shaded.com.google.common.util.concurrent.ListenableFuture;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.cloud.config.enabled=false",
        "spring.kafka.bootstrap-servers=",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
        "spring.jpa.hibernate.ddl-auto=update"
})
@Testcontainers
@DisplayName("Интеграционные тесты PaymentService (БД + Redis)")
class PaymentServiceIntegrationTest {

    /// Мок kafka
    @MockitoBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    /// Мок метрики
    @MockitoBean
    private KafkaMetrics kafkaMetrics;

    /// контейнер PostgreSQL
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("payment_test")
            .withUsername("test")
            .withPassword("test");

    /// Redis
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    /// Подменяем реальные настройки на тестовые
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }


    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentTransactionRepository repository;

    @Autowired
    private RedisService redisService;

    /// Перед каждым тестом
    @BeforeEach
    void setUp() {
        // Очищаем БД
        repository.deleteAll();

        // Очищаем Redis (все ключи)
        redisService.delete("payments:all");
        redisService.delete("payments:user:*");
        redisService.delete("payments:order:*");
        redisService.delete("payment:*");
    }

    private PaymentTransaction createAndSaveTransaction(Long orderId, Long userId,
                                                        BigDecimal amount, String status) {
        PaymentTransaction tx = PaymentTransaction.builder()
                .orderId(orderId)
                .userId(userId)
                .amount(amount)
                .statusPayment(status)
                .message("Test message")
                .createdAt(LocalDateTime.now())
                .build();
        return repository.save(tx);
    }

    private OrderCreatedEvent createOrderEvent(Long orderId, Long userId, BigDecimal amount) {
        OrderCreatedEvent event = new OrderCreatedEvent();
        event.setOrderId(orderId);
        event.setUserId(userId);
        event.setTotalAmount(amount);
        return event;
    }


    /// Полный цикл обработки платежа через Kafka-событие.
    @Test
    @DisplayName("Полный цикл обработки платежа через Kafka-событие")
    void processPayment_ShouldSaveSuccessfully_WhenAmountBelowLimit() {
        /// Сообщение, которое приходит из Kafka
        OrderCreatedEvent event = createOrderEvent(1001L, 2001L, BigDecimal.valueOf(5000));

        /// Вызываем метод
        paymentService.processPayment(event);

        /// Сохранение в БД и она 1
        List<PaymentTransaction> transactions = repository.findAll();
        assertThat(transactions).hasSize(1);

        /// Проверяем каждое поле на соответствие
        PaymentTransaction saved = transactions.get(0);
        assertThat(saved.getOrderId()).isEqualTo(1001L);
        assertThat(saved.getUserId()).isEqualTo(2001L);
        assertThat(saved.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(5000));
        assertThat(saved.getStatusPayment()).isEqualTo("SUCCESS");
        assertThat(saved.getMessage()).isEqualTo("Платёж одобрен");
        assertThat(saved.getCreatedAt()).isNotNull();

        /// Проверяем, что транзакция сохранена в Redis по ключу payment:{id}
        String txCacheKey = "payment:" + saved.getId();
        PaymentTransaction cached = redisService.get(txCacheKey, PaymentTransaction.class);
        assertThat(cached).isNotNull();
        assertThat(cached.getId()).isEqualTo(saved.getId());
        assertThat(cached.getStatusPayment()).isEqualTo("SUCCESS");

        /// Проверяем, что после добавления новой транзакции кэши списков очищены
        assertThat(redisService.hasKey("payments:all")).isFalse();
        assertThat(redisService.hasKey("payments:user:2001")).isFalse();
        assertThat(redisService.hasKey("payments:order:1001")).isFalse();
    }


    /// Платеж отклонен при привышении лимита
    @Test
    @DisplayName("Платёж отклонён при сумме больше лимита")
    void processPayment_ShouldReject_WhenAmountExceedsLimit() {
        /// Событие
        OrderCreatedEvent event = createOrderEvent(1002L, 2002L, BigDecimal.valueOf(15000));

        /// Мок для KafkaTemplate
        when(kafkaTemplate.send(anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        /// Метрики
        doNothing().when(kafkaMetrics).incrementConsumed();
        doNothing().when(kafkaMetrics).incrementPaymentRequested();
        doNothing().when(kafkaMetrics).incrementPaymentFailed();
        doNothing().when(kafkaMetrics).incrementProduced();

        /// Вызываем метод
        paymentService.processPayment(event);

        /// БД
        List<PaymentTransaction> transactions = repository.findAll();
        assertThat(transactions)
                .as("Должна быть сохранена ровно одна транзакция")
                .hasSize(1);

        PaymentTransaction saved = transactions.get(0);
        assertThat(saved.getOrderId())
                .as("ID заказа должен совпадать")
                .isEqualTo(1002L);
        assertThat(saved.getUserId())
                .as("ID пользователя должен совпадать")
                .isEqualTo(2002L);
        assertThat(saved.getAmount())
                .as("Сумма должна совпадать")
                .usingComparator(BigDecimal::compareTo)
                .isEqualTo(BigDecimal.valueOf(15000));
        assertThat(saved.getStatusPayment())
                .as("Статус должен быть FAILED")
                .isEqualTo("FAILED");
        assertThat(saved.getMessage())
                .as("Сообщение должно указывать на превышение лимита")
                .isEqualTo("Сумма превышает лимит 10000 руб.");
        assertThat(saved.getCreatedAt())
                .as("Дата создания должна быть установлена")
                .isNotNull();

        /// Кэш
        String txCacheKey = "payment:" + saved.getId();
        PaymentTransaction cached = redisService.get(txCacheKey, PaymentTransaction.class);
        assertThat(cached)
                .as("Транзакция должна быть сохранена в кэш")
                .isNotNull();
        assertThat(cached.getStatusPayment())
                .as("Статус в кэше должен быть FAILED")
                .isEqualTo("FAILED");


        /// Проверяем очистку кэшей списков
        assertThat(redisService.hasKey("payments:all"))
                .as("Кэш всех транзакций должен быть очищен")
                .isFalse();
        assertThat(redisService.hasKey("payments:user:2002"))
                .as("Кэш транзакций пользователя должен быть очищен")
                .isFalse();
        assertThat(redisService.hasKey("payments:order:1002"))
                .as("Кэш транзакций заказа должен быть очищен")
                .isFalse();

        /// Проверяем Kafka (отправка в failed топик)
        ArgumentCaptor<PaymentProcessedEvent> eventCaptor =
                ArgumentCaptor.forClass(PaymentProcessedEvent.class);
        verify(kafkaTemplate).send(eq("payment-service.payment.failed"), eventCaptor.capture());

        PaymentProcessedEvent kafkaEvent = eventCaptor.getValue();
        assertThat(kafkaEvent.getOrderId())
                .as("Kafka событие должно содержать правильный orderId")
                .isEqualTo(1002L);
        assertThat(kafkaEvent.getStatus())
                .as("Kafka событие должно иметь статус FAILED")
                .isEqualTo("FAILED");
        assertThat(kafkaEvent.getMessage())
                .as("Kafka событие должно содержать сообщение об ошибке")
                .isEqualTo("Сумма превышает лимит 10000 руб.");

        /// Проверяем метрики
        verify(kafkaMetrics).incrementConsumed();
        verify(kafkaMetrics).incrementPaymentRequested();
        verify(kafkaMetrics).incrementPaymentFailed();
        verify(kafkaMetrics).incrementProduced();
    }

    @Test
    @DisplayName("Платёж не успешен при сумме равной лимиту")
    void processPayment_ShouldSucceed_WhenAmountEqualsLimit() {
        /// Событие, 10000
        OrderCreatedEvent event = createOrderEvent(1003L, 2003L, BigDecimal.valueOf(10000));

        /// Моки
        when(kafkaTemplate.send(anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        doNothing().when(kafkaMetrics).incrementConsumed();
        doNothing().when(kafkaMetrics).incrementPaymentRequested();
        doNothing().when(kafkaMetrics).incrementPaymentSuccess();
        doNothing().when(kafkaMetrics).incrementProduced();

        /// Вызываем метод
        paymentService.processPayment(event);

        /// БД
        List<PaymentTransaction> transactions = repository.findAll();
        assertThat(transactions)
                .as("Должна быть сохранена ровно одна транзакция")
                .hasSize(1);

        PaymentTransaction saved = transactions.get(0);

        assertThat(saved.getOrderId())
                .as("ID заказа должен совпадать")
                .isEqualTo(1003L);
        assertThat(saved.getUserId())
                .as("ID пользователя должен совпадать")
                .isEqualTo(2003L);
        assertThat(saved.getAmount())
                .as("Сумма должна быть равна 10000")
                .usingComparator(BigDecimal::compareTo)
                .isEqualTo(BigDecimal.valueOf(10000));
        assertThat(saved.getStatusPayment())
                .as("Статус должен быть FAILED (сумма равна лимиту)")
                .isEqualTo("FAILED");
        assertThat(saved.getMessage())
                .as("Сообщение должно быть 'Сумма превышает лимит 10000 руб.'")
                .isEqualTo("Сумма превышает лимит 10000 руб.");
        assertThat(saved.getCreatedAt())
                .as("Дата создания должна быть установлена")
                .isNotNull();
    }


    @Test
    @DisplayName("Первый раз из БД, второй из кэша")
    void getAllTransactions_ShouldUseCacheOnSecondCall() {
        /// 2 разные транзакции
        createAndSaveTransaction(2001L, 3001L, BigDecimal.valueOf(1000), "SUCCESS");
        createAndSaveTransaction(2002L, 3002L, BigDecimal.valueOf(2000), "FAILED");

        /// Первый вызов (из БД + сохраняет в kech)
        List<PaymentTransaction> result1 = paymentService.getAllTransactions();

        /// 2 заказа
        assertThat(result1)
                .as("Первый вызов должен вернуть 2 транзакции")
                .hasSize(2);
        /// в редис записался с ключом
        assertThat(redisService.hasKey("payments:all"))
                .as("Кэш должен быть создан")
                .isTrue();

        /// Вызов из кэша
        List<PaymentTransaction> result2 = paymentService.getAllTransactions();


        assertThat(result2)
                .as("Второй вызов должен вернуть 2 транзакции")
                .hasSize(2);
        assertThat(result2)
                .as("resul1 и result2 должны совпадать")
                .usingRecursiveComparison()
                .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .isEqualTo(result1);

    }



    @Test
    @DisplayName("Выбрасывает исключение при отсутствии транзакции")
    void getTransactionById_ShouldThrowException_WhenNotFound() {
        assertThatThrownBy(() -> paymentService.getTransactionById(99999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Транзакция не найдена: 99999");
    }


    @Test
    @DisplayName("Получение заказа по Id транзакции")
    void getTransactionsByOrderId_ShouldReturnTransactions() {
        ///  Создаем
        Long orderId = 4001L;
        createAndSaveTransaction(orderId, 5001L, BigDecimal.valueOf(1000), "SUCCESS");
        createAndSaveTransaction(orderId, 5002L, BigDecimal.valueOf(2000), "FAILED");
        createAndSaveTransaction(4002L, 5003L, BigDecimal.valueOf(3000), "SUCCESS");

        /// вызываем
        List<PaymentTransaction> result = paymentService.getTransactionsByOrderId(orderId);

        /// Проверяем
        assertThat(result)
                .as("Сохранено 2 транзакции")
                .hasSize(2);
        assertThat(result)
                .as("OrderId 4001 ")
                .extracting("orderId")
                .containsOnly(orderId);
        assertThat(result)
                .as("Статусы SUCCESS и FAILED")
                .extracting("statusPayment")
                .containsExactlyInAnyOrder("SUCCESS", "FAILED");


        assertThat(redisService.hasKey("payments:order:" + orderId))
                .as("В кеш сохранен заказ с ключом payments:order")
                .isTrue();
    }

    @Test
    @DisplayName("Проверяем возврат транзакций пользователя")
    void getTransactionsByUserId_ShouldReturnTransactions() {
        /// Создаем 3 транзакции
        Long userId = 6001L;
        createAndSaveTransaction(5001L, userId, BigDecimal.valueOf(1500), "SUCCESS");
        createAndSaveTransaction(5002L, userId, BigDecimal.valueOf(2500), "SUCCESS");
        createAndSaveTransaction(5003L, 6002L, BigDecimal.valueOf(3500), "FAILED");

        /// Вызываем
        List<PaymentTransaction> result = paymentService.getTransactionsByUserId(userId);


        assertThat(result)
                .as("2 транзакции")
                .hasSize(2);
        assertThat(result)
                .as("userId 6001")
                .extracting("userId")
                .containsOnly(userId);
        assertThat(result)
                .as("Суммы должны совпадать")
                .extracting(PaymentTransaction::getAmount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactlyInAnyOrder(
                        BigDecimal.valueOf(1500),
                        BigDecimal.valueOf(2500)
                );

        /// Проверяем кэш
        assertThat(redisService.hasKey("payments:user:" + userId))
                .as("В кэш сохранен заказ с ключом payments:order 6001")
                .isTrue();
    }


    @Test
    @DisplayName("Параллельная обработка нескольких платежей")
    void processPayment_ShouldHandleConcurrentRequests() throws InterruptedException {
        /// Количество параллельных задач 5
        int numberOfRequests = 5;
        /// Ждем, пока все 5 задач завершатся
        CountDownLatch latch = new CountDownLatch(numberOfRequests);
        /// Ограничиваем количество одновременных потоков 3
        ExecutorService executor = Executors.newFixedThreadPool(3);

        /// Настраиваем моки ДО запуска
        when(kafkaTemplate.send(anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        doNothing().when(kafkaMetrics).incrementConsumed();
        doNothing().when(kafkaMetrics).incrementPaymentRequested();
        doNothing().when(kafkaMetrics).incrementPaymentSuccess();
        doNothing().when(kafkaMetrics).incrementProduced();

        /// Потокобезопасный список, так как несколько потоков могут добавлять ошибки
        List<Exception> errors = Collections.synchronizedList(new ArrayList<>());

        /// Список для сохранения orderId
        List<Long> expectedOrderIds = new ArrayList<>();
        for (int i = 0; i < numberOfRequests; i++) {
            expectedOrderIds.add((long) (20000 + i));
        }

        /// Запускаем параллельные запросы
        for (int i = 0; i < numberOfRequests; i++) {
            final int index = i;   /// Нужно для использования в лямбде
            executor.submit(() -> {
                try {
                    /// Событие с уникальными данными
                    OrderCreatedEvent event = createOrderEvent(
                            (long) (20000 + index),
                            (long) (30000 + index),
                            BigDecimal.valueOf(1000 + index * 100)
                    );
                    paymentService.processPayment(event);
                } catch (Exception e) {
                    errors.add(e);
                } finally {
                    latch.countDown();  /// Уменьшаем счетчик, когда задача завершена
                }
            });
        }


        boolean completed = latch.await(10, TimeUnit.SECONDS);
        assertThat(completed)
                .as("Все задачи должны завершиться за 10 секунд")
                .isTrue();

        executor.shutdown();
        boolean terminated = executor.awaitTermination(5, TimeUnit.SECONDS);
        assertThat(terminated)
                .as("Executor должен завершиться за 5 секунд")
                .isTrue();


        assertThat(errors)
                .as("Не должно быть ошибок при параллельной обработке")
                .isEmpty();


        List<PaymentTransaction> allTransactions = repository.findAll();
        assertThat(allTransactions)
                .as("Должно быть сохранено %d транзакций", numberOfRequests)
                .hasSize(numberOfRequests);

        assertThat(allTransactions)
                .as("Все транзакции должны иметь статус SUCCESS")
                .allMatch(tx -> "SUCCESS".equals(tx.getStatusPayment()));

        assertThat(allTransactions)
                .as("orderId должны быть уникальными")
                .extracting(PaymentTransaction::getOrderId)
                .doesNotHaveDuplicates();

        assertThat(allTransactions)
                .as("Все ожидаемые orderId должны присутствовать")
                .extracting(PaymentTransaction::getOrderId)
                .containsAll(expectedOrderIds);

        assertThat(allTransactions)
                .as("ID транзакций должны быть уникальными")
                .extracting(PaymentTransaction::getId)
                .doesNotHaveDuplicates();

        assertThat(allTransactions)
                .as("userId должны быть уникальными")
                .extracting(PaymentTransaction::getUserId)
                .doesNotHaveDuplicates();


        List<BigDecimal> expectedAmounts = IntStream.range(0, numberOfRequests)
                .mapToObj(i -> BigDecimal.valueOf(1000 + i * 100))
                .collect(Collectors.toList());

        assertThat(allTransactions)
                .as("Суммы должны совпадать с ожидаемыми")
                .extracting(PaymentTransaction::getAmount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsAll(expectedAmounts);

        for (PaymentTransaction tx : allTransactions) {
            String cacheKey = "payment:" + tx.getId();
            assertThat(redisService.hasKey(cacheKey))
                    .as("Транзакция %d должна быть в кэше", tx.getId())
                    .isTrue();
        }

        assertThat(redisService.hasKey("payments:all"))
                .as("Кэш всех транзакций должен быть очищен")
                .isFalse();
    }

}
