package com.restaurant.order_service.service;

import com.restaurant.common.events.KitchenOrderReadyEvent;
import com.restaurant.common.events.OrderCreatedEvent;
import com.restaurant.common.events.PaymentProcessedEvent;
import com.restaurant.order_service.dto.OrderCreateRequestDTO;
import com.restaurant.order_service.dto.OrderResponseDTO;
import com.restaurant.order_service.entity.Item;
import com.restaurant.order_service.entity.Order;
import com.restaurant.order_service.entity.OrderStatus;
import com.restaurant.order_service.metrics.KafkaMetrics;
import com.restaurant.order_service.repository.ItemRepository;
import com.restaurant.order_service.repository.OrderRepository;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
@DisplayName("Интеграционные тесты OrderService (БД + Redis)")
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class OrderServiceIntegrationTest {

    @MockitoBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @MockitoBean
    private KafkaMetrics kafkaMetrics;

    @MockitoBean
    private Timer.Sample timerSample;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("order_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        cacheManager.getCacheNames().stream()
                .forEach(name -> cacheManager.getCache(name).clear());

        when(kafkaTemplate.send(anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        when(kafkaMetrics.startProcessingTimer()).thenReturn(timerSample);
        doNothing().when(kafkaMetrics).stopProcessingTimer(any(Timer.Sample.class));
        doNothing().when(kafkaMetrics).incrementProduced();
        doNothing().when(kafkaMetrics).incrementConsumed();
        doNothing().when(kafkaMetrics).incrementOrderCreated();
        doNothing().when(kafkaMetrics).incrementOrderPaid();
        doNothing().when(kafkaMetrics).incrementOrderCancelled();
        doNothing().when(kafkaMetrics).incrementOrderReady();
        doNothing().when(kafkaMetrics).incrementProcessingError();
    }

    private void createTestItems() {
        Item item1 = Item.builder()
                .itemName("Пицца Маргарита")
                .itemPrice(BigDecimal.valueOf(500))
                .isAvailable(true)
                .build();
        Item item2 = Item.builder()
                .itemName("Паста Карбонара")
                .itemPrice(BigDecimal.valueOf(450))
                .isAvailable(true)
                .build();
        Item item3 = Item.builder()
                .itemName("Цезарь")
                .itemPrice(BigDecimal.valueOf(300))
                .isAvailable(true)
                .build();
        itemRepository.saveAll(List.of(item1, item2, item3));
    }

    private OrderCreateRequestDTO createOrderRequest(Long userId, List<OrderCreateRequestDTO.ItemRequestDTO> items) {
        OrderCreateRequestDTO request = new OrderCreateRequestDTO();
        request.setUserId(userId);
        request.setItems(items);
        return request;
    }

    private OrderCreateRequestDTO.ItemRequestDTO createItemRequest(String productName, Integer quantity) {
        OrderCreateRequestDTO.ItemRequestDTO item = new OrderCreateRequestDTO.ItemRequestDTO();
        item.setProductName(productName);
        item.setQuantity(quantity);
        return item;
    }


    @Test
    @DisplayName("createOrder() - успешное создание заказа")
    void createOrder_ShouldCreateOrderSuccessfully() {
        /// в меню есть блюда
        createTestItems();
        List<OrderCreateRequestDTO.ItemRequestDTO> items = List.of(
                createItemRequest("Пицца Маргарита", 2),
                createItemRequest("Паста Карбонара", 1)
        );
        OrderCreateRequestDTO request = createOrderRequest(1001L, items);

        /// создаём заказ
        OrderResponseDTO response = orderService.createOrder(request);

        /// заказ создан
        assertThat(response).isNotNull();
        assertThat(response.getId()).isNotNull();
        assertThat(response.getTotalAmount())
                .usingComparator(BigDecimal::compareTo)
                .isEqualTo(BigDecimal.valueOf(1450));

        /// Kafka отправлено
        verify(kafkaTemplate, times(1)).send(
                eq("order-service.order.created"),
                any(OrderCreatedEvent.class)
        );
        verify(kafkaMetrics, times(1)).incrementProduced();
        verify(kafkaMetrics, times(1)).incrementOrderCreated();
    }


    @Test
    @DisplayName("handlePaymentSuccess() - меняет статус на PAID")
    void handlePaymentSuccess_ShouldChangeStatusToPaid() {
        /// создан заказ
        createTestItems();
        List<OrderCreateRequestDTO.ItemRequestDTO> items = List.of(
                createItemRequest("Пицца Маргарита", 1)
        );
        OrderCreateRequestDTO request = createOrderRequest(1001L, items);
        OrderResponseDTO created = orderService.createOrder(request);
        Long orderId = created.getId();

        /// приходит событие об успешной оплате
        PaymentProcessedEvent event = new PaymentProcessedEvent(orderId, "SUCCESS", "OK");
        orderService.handlePaymentSuccess(event);

        /// статус изменился на PAID
        Order updatedOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.PAID.name());

        /// проверяем вызовы метрик
        verify(kafkaMetrics, atLeastOnce()).incrementConsumed();
        verify(kafkaMetrics, times(1)).incrementOrderPaid();
        verify(kafkaTemplate, atLeastOnce()).send(anyString(), any());
    }


    @Test
    @DisplayName("handleCookingStarted() - меняет статус на PREPARING")
    void handleCookingStarted_ShouldChangeStatusToPreparing() {
        /// создан и оплачен заказ
        createTestItems();
        List<OrderCreateRequestDTO.ItemRequestDTO> items = List.of(
                createItemRequest("Пицца Маргарита", 1)
        );
        OrderCreateRequestDTO request = createOrderRequest(1001L, items);
        OrderResponseDTO created = orderService.createOrder(request);
        Long orderId = created.getId();

        orderService.handlePaymentSuccess(new PaymentProcessedEvent(orderId, "SUCCESS", "OK"));

        /// приходит событие о начале готовки
        orderService.handleCookingStarted(orderId);

        /// статус изменился на PREPARING
        Order updatedOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.PREPARING.name());

        /// проверяем вызовы метрик
        verify(kafkaMetrics, atLeastOnce()).incrementConsumed();
    }

    @Test
    @DisplayName("handleOrderReady() - меняет статус на READY")
    void handleOrderReady_ShouldChangeStatusToReady() {
        /// создан, оплачен и готовится заказ
        createTestItems();
        List<OrderCreateRequestDTO.ItemRequestDTO> items = List.of(
                createItemRequest("Пицца Маргарита", 1)
        );
        OrderCreateRequestDTO request = createOrderRequest(1001L, items);
        OrderResponseDTO created = orderService.createOrder(request);
        Long orderId = created.getId();

        orderService.handlePaymentSuccess(new PaymentProcessedEvent(orderId, "SUCCESS", "OK"));
        orderService.handleCookingStarted(orderId);

        /// приходит событие о готовности заказа
        KitchenOrderReadyEvent event = new KitchenOrderReadyEvent(orderId, "ticket-123", "READY");
        orderService.handleOrderReady(event);

        /// статус изменился на READY
        Order updatedOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.READY.name());

        /// проверяем вызовы метрик
        verify(kafkaMetrics, atLeastOnce()).incrementConsumed();
        verify(kafkaMetrics, times(1)).incrementOrderReady();
    }


    @Test
    @DisplayName("handlePaymentFailed() - меняет статус на CANCELLED")
    void handlePaymentFailed_ShouldChangeStatusToCancelled() {
        /// создан заказ
        createTestItems();
        List<OrderCreateRequestDTO.ItemRequestDTO> items = List.of(
                createItemRequest("Пицца Маргарита", 1)
        );
        OrderCreateRequestDTO request = createOrderRequest(1001L, items);
        OrderResponseDTO created = orderService.createOrder(request);
        Long orderId = created.getId();

        /// приходит событие об отклонении оплаты
        PaymentProcessedEvent event = new PaymentProcessedEvent(orderId, "FAILED", "Ошибка оплаты");
        orderService.handlePaymentFailed(event);

        /// статус изменился на CANCELLED
        Order updatedOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED.name());

        /// проверяем вызовы метрик
        verify(kafkaMetrics, atLeastOnce()).incrementConsumed();
        verify(kafkaMetrics, times(1)).incrementOrderCancelled();
    }

    @Test
    @DisplayName("Полный жизненный цикл заказа: PENDING → PAID → PREPARING → READY → DELIVERED")
    void fullOrderLifecycle_ShouldUpdateStatusesCorrectly() {
        /// создан заказ
        createTestItems();
        List<OrderCreateRequestDTO.ItemRequestDTO> items = List.of(
                createItemRequest("Пицца Маргарита", 2)
        );
        OrderCreateRequestDTO request = createOrderRequest(1001L, items);
        OrderResponseDTO created = orderService.createOrder(request);
        Long orderId = created.getId();

        /// Проверяем начальный статус
        assertThat(orderRepository.findById(orderId).get().getStatus())
                .isEqualTo(OrderStatus.PENDING.name());

        ///  PENDING → PAID
        orderService.handlePaymentSuccess(new PaymentProcessedEvent(orderId, "SUCCESS", "OK"));
        assertThat(orderRepository.findById(orderId).get().getStatus())
                .isEqualTo(OrderStatus.PAID.name());

        /// PAID → PREPARING
        orderService.handleCookingStarted(orderId);
        assertThat(orderRepository.findById(orderId).get().getStatus())
                .isEqualTo(OrderStatus.PREPARING.name());

        /// PREPARING → READY
        orderService.handleOrderReady(new KitchenOrderReadyEvent(orderId, "ticket-123", "READY"));
        assertThat(orderRepository.findById(orderId).get().getStatus())
                .isEqualTo(OrderStatus.READY.name());

        /// READY → DELIVERED
        orderService.handleOrderDelivered(orderId);
        assertThat(orderRepository.findById(orderId).get().getStatus())
                .isEqualTo(OrderStatus.DELIVERED.name());

        /// incrementConsumed() вызывался минимум 4 раза (для каждого события)
        verify(kafkaMetrics, atLeast(4)).incrementConsumed();
    }
}