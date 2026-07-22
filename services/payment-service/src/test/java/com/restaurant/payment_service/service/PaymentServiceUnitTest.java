package com.restaurant.payment_service.service;

import com.restaurant.common.events.OrderCreatedEvent;
import com.restaurant.common.events.PaymentProcessedEvent;
import com.restaurant.payment_service.entity.PaymentTransaction;
import com.restaurant.payment_service.metrics.KafkaMetrics;
import com.restaurant.payment_service.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit-тесты PaymentService")
class PaymentServiceUnitTest {

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private RedisService redisService;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private KafkaMetrics kafkaMetrics;

    @InjectMocks
    private PaymentService paymentService;

    private final Long TEST_ORDER_ID = 1001L;
    private final Long TEST_USER_ID = 2001L;
    private final BigDecimal TEST_AMOUNT = BigDecimal.valueOf(5000);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paymentService, "maxAmount", BigDecimal.valueOf(10000));
    }

    private OrderCreatedEvent createOrderEvent(Long orderId, Long userId, BigDecimal amount) {
        OrderCreatedEvent event = new OrderCreatedEvent();
        event.setOrderId(orderId);
        event.setUserId(userId);
        event.setTotalAmount(amount);
        return event;
    }

    private PaymentTransaction createTransaction(Long id, Long orderId, Long userId,
                                                 BigDecimal amount, String status, String message) {
        return PaymentTransaction.builder()
                .id(id)
                .orderId(orderId)
                .userId(userId)
                .amount(amount)
                .statusPayment(status)
                .message(message)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private PaymentTransaction createTransaction(Long id, Long orderId, Long userId,
                                                 BigDecimal amount, String status) {
        return createTransaction(id, orderId, userId, amount, status, "Test message");
    }

    @Nested
    @DisplayName("Тесты processPayment() - обработка Kafka события")
    class ProcessPaymentTests {

        @Test
        @DisplayName("Успешная оплата при сумме меньше лимита")
        void processPayment_ShouldSucceed_WhenAmountBelowLimit() {
            OrderCreatedEvent event = createOrderEvent(TEST_ORDER_ID, TEST_USER_ID, BigDecimal.valueOf(5000));

            PaymentTransaction savedTransaction = createTransaction(1L, TEST_ORDER_ID, TEST_USER_ID,
                    BigDecimal.valueOf(5000), "SUCCESS", "Платёж одобрен");

            when(paymentTransactionRepository.save(any(PaymentTransaction.class)))
                    .thenReturn(savedTransaction);
            when(kafkaTemplate.send(anyString(), any()))
                    .thenReturn(CompletableFuture.completedFuture(null));

            doNothing().when(redisService).saveWithExpire(anyString(), any(), anyLong());
            doNothing().when(redisService).delete(anyString());

            paymentService.processPayment(event);

            ArgumentCaptor<PaymentTransaction> transactionCaptor =
                    ArgumentCaptor.forClass(PaymentTransaction.class);
            verify(paymentTransactionRepository).save(transactionCaptor.capture());

            PaymentTransaction saved = transactionCaptor.getValue();
            assertThat(saved.getOrderId()).isEqualTo(TEST_ORDER_ID);
            assertThat(saved.getUserId()).isEqualTo(TEST_USER_ID);
            assertThat(saved.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(5000));
            assertThat(saved.getStatusPayment()).isEqualTo("SUCCESS");
            assertThat(saved.getMessage()).isEqualTo("Платёж одобрен");

            verify(redisService).saveWithExpire(eq("payment:1"), any(PaymentTransaction.class), eq(300L));
            verify(redisService).delete("payments:all");
            verify(redisService).delete("payments:user:" + TEST_USER_ID);
            verify(redisService).delete("payments:order:" + TEST_ORDER_ID);

            ArgumentCaptor<PaymentProcessedEvent> kafkaCaptor =
                    ArgumentCaptor.forClass(PaymentProcessedEvent.class);
            verify(kafkaTemplate).send(eq("payment-service.payment.success"), kafkaCaptor.capture());

            PaymentProcessedEvent kafkaEvent = kafkaCaptor.getValue();
            assertThat(kafkaEvent.getOrderId()).isEqualTo(TEST_ORDER_ID);
            assertThat(kafkaEvent.getStatus()).isEqualTo("SUCCESS");
            assertThat(kafkaEvent.getMessage()).isEqualTo("Платёж одобрен");

            verify(kafkaMetrics).incrementConsumed();
            verify(kafkaMetrics).incrementPaymentRequested();
            verify(kafkaMetrics).incrementPaymentSuccess();
            verify(kafkaMetrics).incrementProduced();
            verify(kafkaMetrics).startProcessingTimer();
        }

        @Test
        @DisplayName("Отказ оплаты при превышении лимита")
        void processPayment_ShouldReject_WhenAmountExceedsLimit() {
            OrderCreatedEvent event = createOrderEvent(TEST_ORDER_ID, TEST_USER_ID, BigDecimal.valueOf(15000));

            PaymentTransaction savedTransaction = createTransaction(1L, TEST_ORDER_ID, TEST_USER_ID,
                    BigDecimal.valueOf(15000), "FAILED", "Сумма превышает лимит 10000 руб.");

            when(paymentTransactionRepository.save(any(PaymentTransaction.class)))
                    .thenReturn(savedTransaction);
            when(kafkaTemplate.send(anyString(), any()))
                    .thenReturn(CompletableFuture.completedFuture(null));

            doNothing().when(redisService).saveWithExpire(anyString(), any(), anyLong());
            doNothing().when(redisService).delete(anyString());

            paymentService.processPayment(event);

            ArgumentCaptor<PaymentTransaction> transactionCaptor =
                    ArgumentCaptor.forClass(PaymentTransaction.class);
            verify(paymentTransactionRepository).save(transactionCaptor.capture());

            PaymentTransaction saved = transactionCaptor.getValue();
            assertThat(saved.getStatusPayment()).isEqualTo("FAILED");
            assertThat(saved.getMessage()).isEqualTo("Сумма превышает лимит 10000 руб.");

            verify(kafkaTemplate).send(eq("payment-service.payment.failed"), any(PaymentProcessedEvent.class));
            verify(kafkaMetrics).incrementPaymentFailed();
            verify(kafkaMetrics, never()).incrementPaymentSuccess();
        }

        @Test
        @DisplayName("Отказ оплаты при сумме равной лимиту")
        void processPayment_ShouldReject_WhenAmountEqualsLimit() {
            OrderCreatedEvent event = createOrderEvent(TEST_ORDER_ID, TEST_USER_ID, BigDecimal.valueOf(10000));

            PaymentTransaction savedTransaction = createTransaction(1L, TEST_ORDER_ID, TEST_USER_ID,
                    BigDecimal.valueOf(10000), "FAILED", "Сумма превышает лимит 10000 руб.");

            when(paymentTransactionRepository.save(any(PaymentTransaction.class)))
                    .thenReturn(savedTransaction);
            when(kafkaTemplate.send(anyString(), any()))
                    .thenReturn(CompletableFuture.completedFuture(null));

            doNothing().when(redisService).saveWithExpire(anyString(), any(), anyLong());
            doNothing().when(redisService).delete(anyString());

            paymentService.processPayment(event);

            ArgumentCaptor<PaymentTransaction> transactionCaptor =
                    ArgumentCaptor.forClass(PaymentTransaction.class);
            verify(paymentTransactionRepository).save(transactionCaptor.capture());

            PaymentTransaction saved = transactionCaptor.getValue();
            assertThat(saved.getStatusPayment()).isEqualTo("FAILED");
            assertThat(saved.getMessage()).isEqualTo("Сумма превышает лимит 10000 руб.");
        }

        @Test
        @DisplayName("Обработка исключения при сохранении в БД")
        void processPayment_ShouldThrowException_WhenSaveFails() {
            OrderCreatedEvent event = createOrderEvent(TEST_ORDER_ID, TEST_USER_ID, BigDecimal.valueOf(5000));

            when(paymentTransactionRepository.save(any(PaymentTransaction.class)))
                    .thenThrow(new RuntimeException("DB connection failed"));

            // проверяем, что исключение пробрасывается дальше
            assertThatThrownBy(() -> paymentService.processPayment(event))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("DB connection failed"); // Используем contains вместо точного совпадения

            verify(kafkaTemplate, never()).send(anyString(), any());
            verify(redisService, never()).saveWithExpire(anyString(), any(), anyLong());
        }
    }

    @Nested
    @DisplayName("Тесты getAllTransactions()")
    class GetAllTransactionsTests {

        @Test
        @DisplayName("Возвращает список из БД, если кэш пуст")
        void getAllTransactions_ShouldReturnFromDB_WhenCacheEmpty() {
            List<PaymentTransaction> transactions = List.of(
                    createTransaction(1L, 1001L, 2001L, BigDecimal.valueOf(1000), "SUCCESS"),
                    createTransaction(2L, 1002L, 2002L, BigDecimal.valueOf(2000), "FAILED")
            );

            when(redisService.getList("payments:all", PaymentTransaction.class))
                    .thenReturn(null);
            when(paymentTransactionRepository.findAll())
                    .thenReturn(transactions);
            doNothing().when(redisService).saveWithExpire(eq("payments:all"), eq(transactions), eq(120L));

            List<PaymentTransaction> result = paymentService.getAllTransactions();

            assertThat(result).hasSize(2);
            assertThat(result).extracting("orderId").containsExactlyInAnyOrder(1001L, 1002L);

            verify(paymentTransactionRepository).findAll();
            verify(redisService).saveWithExpire(eq("payments:all"), eq(transactions), eq(120L));
        }

        @Test
        @DisplayName("Возвращает из кэша, если он не пуст")
        void getAllTransactions_ShouldReturnFromCache_WhenCacheExists() {
            List<PaymentTransaction> cachedTransactions = List.of(
                    createTransaction(1L, 1001L, 2001L, BigDecimal.valueOf(1000), "SUCCESS")
            );

            when(redisService.getList("payments:all", PaymentTransaction.class))
                    .thenReturn(cachedTransactions);

            List<PaymentTransaction> result = paymentService.getAllTransactions();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getOrderId()).isEqualTo(1001L);

            verify(paymentTransactionRepository, never()).findAll();
            verify(redisService, never()).saveWithExpire(anyString(), any(), anyLong());
        }

        @Test
        @DisplayName("Возвращает пустой список, если БД пуста")
        void getAllTransactions_ShouldReturnEmptyList_WhenDBEmpty() {
            when(redisService.getList("payments:all", PaymentTransaction.class))
                    .thenReturn(null);
            when(paymentTransactionRepository.findAll())
                    .thenReturn(List.of());
            doNothing().when(redisService).saveWithExpire(eq("payments:all"), eq(List.of()), eq(120L));

            List<PaymentTransaction> result = paymentService.getAllTransactions();

            assertThat(result).isEmpty();
            verify(redisService).saveWithExpire(eq("payments:all"), eq(List.of()), eq(120L));
        }
    }

    @Nested
    @DisplayName("Тесты getTransactionById()")
    class GetTransactionByIdTests {

        private final Long TRANSACTION_ID = 1L;

        @Test
        @DisplayName("Возвращает транзакцию из кэша, если она там есть")
        void getTransactionById_ShouldReturnFromCache_WhenExists() {
            PaymentTransaction cached = createTransaction(TRANSACTION_ID, TEST_ORDER_ID,
                    TEST_USER_ID, BigDecimal.valueOf(5000), "SUCCESS");

            when(redisService.get("payment:" + TRANSACTION_ID, PaymentTransaction.class))
                    .thenReturn(cached);

            PaymentTransaction result = paymentService.getTransactionById(TRANSACTION_ID);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(TRANSACTION_ID);
            assertThat(result.getOrderId()).isEqualTo(TEST_ORDER_ID);

            verify(paymentTransactionRepository, never()).findById(anyLong());
        }

        @Test
        @DisplayName("Загружает из БД и кэширует, если в кэше нет")
        void getTransactionById_ShouldLoadFromDB_WhenNotInCache() {
            PaymentTransaction dbTransaction = createTransaction(TRANSACTION_ID, TEST_ORDER_ID,
                    TEST_USER_ID, BigDecimal.valueOf(5000), "SUCCESS");

            when(redisService.get("payment:" + TRANSACTION_ID, PaymentTransaction.class))
                    .thenReturn(null);
            when(paymentTransactionRepository.findById(TRANSACTION_ID))
                    .thenReturn(Optional.of(dbTransaction));
            doNothing().when(redisService).saveWithExpire(eq("payment:" + TRANSACTION_ID), eq(dbTransaction), eq(120L));

            PaymentTransaction result = paymentService.getTransactionById(TRANSACTION_ID);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(TRANSACTION_ID);

            verify(paymentTransactionRepository).findById(TRANSACTION_ID);
            verify(redisService).saveWithExpire(eq("payment:" + TRANSACTION_ID), eq(dbTransaction), eq(120L));
        }

        @Test
        @DisplayName("Выбрасывает исключение, если транзакция не найдена")
        void getTransactionById_ShouldThrowException_WhenNotFound() {
            Long nonExistentId = 999L;

            when(redisService.get("payment:" + nonExistentId, PaymentTransaction.class))
                    .thenReturn(null);
            when(paymentTransactionRepository.findById(nonExistentId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.getTransactionById(nonExistentId))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Транзакция не найдена: " + nonExistentId);
        }
    }

    @Nested
    @DisplayName("Тесты getTransactionsByOrderId()")
    class GetTransactionsByOrderIdTests {

        @Test
        @DisplayName("Возвращает из кэша, если он есть")
        void getTransactionsByOrderId_ShouldReturnFromCache_WhenExists() {
            List<PaymentTransaction> cached = List.of(
                    createTransaction(1L, TEST_ORDER_ID, TEST_USER_ID,
                            BigDecimal.valueOf(1000), "SUCCESS")
            );

            when(redisService.getList("payments:order:" + TEST_ORDER_ID, PaymentTransaction.class))
                    .thenReturn(cached);

            List<PaymentTransaction> result = paymentService.getTransactionsByOrderId(TEST_ORDER_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getOrderId()).isEqualTo(TEST_ORDER_ID);

            verify(paymentTransactionRepository, never()).findByOrderId(anyLong());
        }

        @Test
        @DisplayName("Загружает из БД, если кэш пуст")
        void getTransactionsByOrderId_ShouldLoadFromDB_WhenCacheEmpty() {
            List<PaymentTransaction> dbTransactions = List.of(
                    createTransaction(1L, TEST_ORDER_ID, TEST_USER_ID,
                            BigDecimal.valueOf(1000), "SUCCESS"),
                    createTransaction(2L, TEST_ORDER_ID, TEST_USER_ID,
                            BigDecimal.valueOf(2000), "FAILED")
            );

            when(redisService.getList("payments:order:" + TEST_ORDER_ID, PaymentTransaction.class))
                    .thenReturn(null);
            when(paymentTransactionRepository.findByOrderId(TEST_ORDER_ID))
                    .thenReturn(dbTransactions);
            doNothing().when(redisService).saveWithExpire(
                    eq("payments:order:" + TEST_ORDER_ID),
                    eq(dbTransactions),
                    eq(120L)
            );

            List<PaymentTransaction> result = paymentService.getTransactionsByOrderId(TEST_ORDER_ID);

            assertThat(result).hasSize(2);
            assertThat(result).extracting("orderId").containsOnly(TEST_ORDER_ID);

            verify(paymentTransactionRepository).findByOrderId(TEST_ORDER_ID);
            verify(redisService).saveWithExpire(
                    eq("payments:order:" + TEST_ORDER_ID),
                    eq(dbTransactions),
                    eq(120L)
            );
        }

        @Test
        @DisplayName("Возвращает пустой список, если транзакций нет")
        void getTransactionsByOrderId_ShouldReturnEmptyList_WhenNotFound() {
            Long nonExistentOrderId = 9999L;

            when(redisService.getList("payments:order:" + nonExistentOrderId, PaymentTransaction.class))
                    .thenReturn(null);
            when(paymentTransactionRepository.findByOrderId(nonExistentOrderId))
                    .thenReturn(List.of());
            doNothing().when(redisService).saveWithExpire(
                    eq("payments:order:" + nonExistentOrderId),
                    eq(List.of()),
                    eq(120L)
            );

            List<PaymentTransaction> result = paymentService.getTransactionsByOrderId(nonExistentOrderId);

            assertThat(result).isEmpty();
            verify(redisService).saveWithExpire(
                    eq("payments:order:" + nonExistentOrderId),
                    eq(List.of()),
                    eq(120L)
            );
        }
    }

    @Nested
    @DisplayName("Тесты getTransactionsByUserId()")
    class GetTransactionsByUserIdTests {

        @Test
        @DisplayName("Возвращает из кэша, если он есть")
        void getTransactionsByUserId_ShouldReturnFromCache_WhenExists() {
            List<PaymentTransaction> cached = List.of(
                    createTransaction(1L, TEST_ORDER_ID, TEST_USER_ID,
                            BigDecimal.valueOf(1000), "SUCCESS")
            );

            when(redisService.getList("payments:user:" + TEST_USER_ID, PaymentTransaction.class))
                    .thenReturn(cached);

            List<PaymentTransaction> result = paymentService.getTransactionsByUserId(TEST_USER_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getUserId()).isEqualTo(TEST_USER_ID);

            verify(paymentTransactionRepository, never()).findByUserId(anyLong());
        }

        @Test
        @DisplayName("Загружает из БД, если кэш пуст")
        void getTransactionsByUserId_ShouldLoadFromDB_WhenCacheEmpty() {
            List<PaymentTransaction> dbTransactions = List.of(
                    createTransaction(1L, TEST_ORDER_ID, TEST_USER_ID,
                            BigDecimal.valueOf(1000), "SUCCESS"),
                    createTransaction(2L, TEST_ORDER_ID + 1, TEST_USER_ID,
                            BigDecimal.valueOf(2000), "FAILED")
            );

            when(redisService.getList("payments:user:" + TEST_USER_ID, PaymentTransaction.class))
                    .thenReturn(null);
            when(paymentTransactionRepository.findByUserId(TEST_USER_ID))
                    .thenReturn(dbTransactions);
            doNothing().when(redisService).saveWithExpire(
                    eq("payments:user:" + TEST_USER_ID),
                    eq(dbTransactions),
                    eq(120L)
            );

            List<PaymentTransaction> result = paymentService.getTransactionsByUserId(TEST_USER_ID);

            assertThat(result).hasSize(2);
            assertThat(result).extracting("userId").containsOnly(TEST_USER_ID);

            verify(paymentTransactionRepository).findByUserId(TEST_USER_ID);
            verify(redisService).saveWithExpire(
                    eq("payments:user:" + TEST_USER_ID),
                    eq(dbTransactions),
                    eq(120L)
            );
        }

        @Test
        @DisplayName("Возвращает пустой список, если транзакций нет")
        void getTransactionsByUserId_ShouldReturnEmptyList_WhenNotFound() {
            Long nonExistentUserId = 9999L;

            when(redisService.getList("payments:user:" + nonExistentUserId, PaymentTransaction.class))
                    .thenReturn(null);
            when(paymentTransactionRepository.findByUserId(nonExistentUserId))
                    .thenReturn(List.of());
            doNothing().when(redisService).saveWithExpire(
                    eq("payments:user:" + nonExistentUserId),
                    eq(List.of()),
                    eq(120L)
            );

            List<PaymentTransaction> result = paymentService.getTransactionsByUserId(nonExistentUserId);

            assertThat(result).isEmpty();
            verify(redisService).saveWithExpire(
                    eq("payments:user:" + nonExistentUserId),
                    eq(List.of()),
                    eq(120L)
            );
        }
    }

    @Nested
    @DisplayName("Тесты saveTransaction() - сохранение в БД")
    class SaveTransactionTests {

        @Test
        @DisplayName("Сохраняет транзакцию с правильными полями")
        void saveTransaction_ShouldSaveWithCorrectFields() {
            OrderCreatedEvent event = createOrderEvent(TEST_ORDER_ID, TEST_USER_ID, TEST_AMOUNT);
            PaymentProcessedEvent result = new PaymentProcessedEvent(
                    TEST_ORDER_ID, "SUCCESS", "Платёж одобрен"
            );

            // при сохранении используем реальное сообщение из события
            when(paymentTransactionRepository.save(any(PaymentTransaction.class)))
                    .thenAnswer(invocation -> {
                        PaymentTransaction tx = invocation.getArgument(0);
                        return PaymentTransaction.builder()
                                .id(1L)
                                .orderId(tx.getOrderId())
                                .userId(tx.getUserId())
                                .amount(tx.getAmount())
                                .statusPayment(tx.getStatusPayment())
                                .message(tx.getMessage())
                                .createdAt(tx.getCreatedAt())
                                .build();
                    });

            PaymentTransaction resultTx = paymentService.saveTransaction(event, result);

            assertThat(resultTx).isNotNull();
            assertThat(resultTx.getId()).isEqualTo(1L);
            assertThat(resultTx.getOrderId()).isEqualTo(TEST_ORDER_ID);
            assertThat(resultTx.getUserId()).isEqualTo(TEST_USER_ID);
            assertThat(resultTx.getAmount()).isEqualByComparingTo(TEST_AMOUNT);
            assertThat(resultTx.getStatusPayment()).isEqualTo("SUCCESS");
            assertThat(resultTx.getMessage()).isEqualTo("Платёж одобрен");
            assertThat(resultTx.getCreatedAt()).isNotNull();

            verify(paymentTransactionRepository).save(any(PaymentTransaction.class));
        }

        @Test
        @DisplayName("Сохраняет транзакцию со статусом FAILED")
        void saveTransaction_ShouldSaveWithFailedStatus() {
            OrderCreatedEvent event = createOrderEvent(TEST_ORDER_ID, TEST_USER_ID, BigDecimal.valueOf(15000));
            PaymentProcessedEvent result = new PaymentProcessedEvent(
                    TEST_ORDER_ID, "FAILED", "Сумма превышает лимит"
            );

            // ИСПРАВЛЕНО: при сохранении используем реальное сообщение из события
            when(paymentTransactionRepository.save(any(PaymentTransaction.class)))
                    .thenAnswer(invocation -> {
                        PaymentTransaction tx = invocation.getArgument(0);
                        return PaymentTransaction.builder()
                                .id(1L)
                                .orderId(tx.getOrderId())
                                .userId(tx.getUserId())
                                .amount(tx.getAmount())
                                .statusPayment(tx.getStatusPayment())
                                .message(tx.getMessage())
                                .createdAt(tx.getCreatedAt())
                                .build();
                    });

            PaymentTransaction resultTx = paymentService.saveTransaction(event, result);

            assertThat(resultTx.getStatusPayment()).isEqualTo("FAILED");
            assertThat(resultTx.getMessage()).isEqualTo("Сумма превышает лимит");
        }
    }

    @Nested
    @DisplayName("Тесты очистки кэшей")
    class CacheClearTests {

        @Test
        @DisplayName("Очищает все кэши списков при добавлении транзакции")
        void clearListCaches_ShouldDeleteAllListCaches() {
            OrderCreatedEvent event = createOrderEvent(TEST_ORDER_ID, TEST_USER_ID, TEST_AMOUNT);
            PaymentProcessedEvent result = new PaymentProcessedEvent(
                    TEST_ORDER_ID, "SUCCESS", "Платёж одобрен"
            );

            PaymentTransaction savedTransaction = createTransaction(1L, TEST_ORDER_ID, TEST_USER_ID,
                    TEST_AMOUNT, "SUCCESS", "Платёж одобрен");

            when(paymentTransactionRepository.save(any(PaymentTransaction.class)))
                    .thenReturn(savedTransaction);
            when(kafkaTemplate.send(anyString(), any()))
                    .thenReturn(CompletableFuture.completedFuture(null));

            doNothing().when(redisService).saveWithExpire(anyString(), any(), anyLong());
            doNothing().when(redisService).delete(anyString());

            paymentService.processPayment(event);

            verify(redisService).delete("payments:all");
            verify(redisService).delete("payments:user:" + TEST_USER_ID);
            verify(redisService).delete("payments:order:" + TEST_ORDER_ID);
        }
    }
}