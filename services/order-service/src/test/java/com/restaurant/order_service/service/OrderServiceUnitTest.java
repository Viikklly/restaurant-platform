package com.restaurant.order_service.service;

import com.restaurant.common.events.KitchenOrderReadyEvent;
import com.restaurant.common.events.OrderCreatedEvent;
import com.restaurant.common.events.OrderPaidEvent;
import com.restaurant.common.events.PaymentProcessedEvent;
import com.restaurant.order_service.dto.OrderCreateRequestDTO;
import com.restaurant.order_service.dto.OrderResponseDTO;
import com.restaurant.order_service.entity.Item;
import com.restaurant.order_service.entity.Order;
import com.restaurant.order_service.entity.OrderItem;
import com.restaurant.order_service.entity.OrderStatus;
import com.restaurant.order_service.exception.OrderNotFoundException;
import com.restaurant.order_service.metrics.KafkaMetrics;
import com.restaurant.order_service.repository.ItemRepository;
import com.restaurant.order_service.repository.OrderItemRepository;
import com.restaurant.order_service.repository.OrderRepository;
import io.micrometer.core.instrument.Timer;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit-тесты OrderService")
class OrderServiceUnitTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private KafkaMetrics kafkaMetrics;

    @Mock
    private Timer.Sample timerSample;

    @InjectMocks
    private OrderService orderService;

    private final Long TEST_ORDER_ID = 1L;
    private final Long TEST_USER_ID = 1001L;
    private final String TEST_ITEM_NAME = "Пицца Маргарита";
    private final BigDecimal TEST_ITEM_PRICE = BigDecimal.valueOf(500);
    private final BigDecimal TEST_TOTAL_AMOUNT = BigDecimal.valueOf(1000);

    @BeforeEach
    void setUp() {
        lenient().when(kafkaMetrics.startProcessingTimer()).thenReturn(timerSample);
        lenient().doNothing().when(kafkaMetrics).stopProcessingTimer(any(Timer.Sample.class));
        lenient().doNothing().when(kafkaMetrics).incrementProduced();
        lenient().doNothing().when(kafkaMetrics).incrementConsumed();
        lenient().doNothing().when(kafkaMetrics).incrementOrderCreated();
        lenient().doNothing().when(kafkaMetrics).incrementOrderPaid();
        lenient().doNothing().when(kafkaMetrics).incrementOrderCancelled();
        lenient().doNothing().when(kafkaMetrics).incrementOrderReady();
        lenient().doNothing().when(kafkaMetrics).incrementProcessingError();
    }



    private Item createTestItem(Long id, String name, BigDecimal price) {
        return Item.builder()
                .id(id)
                .itemName(name)
                .itemPrice(price)
                .isAvailable(true)
                .build();
    }

    private Order createTestOrder(Long id, Long userId, String status, BigDecimal totalAmount) {
        Order order = new Order();
        order.setId(id);
        order.setUserId(userId);
        order.setStatus(status);
        order.setTotalAmount(totalAmount);
        order.setCreatedAt(LocalDateTime.now());
        return order;
    }

    private OrderCreateRequestDTO createOrderRequest(Long userId, String itemName, Integer quantity) {
        OrderCreateRequestDTO request = new OrderCreateRequestDTO();
        request.setUserId(userId);

        OrderCreateRequestDTO.ItemRequestDTO item = new OrderCreateRequestDTO.ItemRequestDTO();
        item.setProductName(itemName);
        item.setQuantity(quantity);

        request.setItems(List.of(item));
        return request;
    }

    private OrderCreateRequestDTO createOrderRequestWithMultipleItems(Long userId) {
        OrderCreateRequestDTO request = new OrderCreateRequestDTO();
        request.setUserId(userId);

        OrderCreateRequestDTO.ItemRequestDTO item1 = new OrderCreateRequestDTO.ItemRequestDTO();
        item1.setProductName("Пицца Маргарита");
        item1.setQuantity(2);

        OrderCreateRequestDTO.ItemRequestDTO item2 = new OrderCreateRequestDTO.ItemRequestDTO();
        item2.setProductName("Паста Карбонара");
        item2.setQuantity(1);

        request.setItems(List.of(item1, item2));
        return request;
    }

    private OrderCreateRequestDTO createOrderRequestWithNullFields(Long userId) {
        OrderCreateRequestDTO request = new OrderCreateRequestDTO();
        request.setUserId(userId);

        OrderCreateRequestDTO.ItemRequestDTO item1 = new OrderCreateRequestDTO.ItemRequestDTO();
        item1.setProductName(null);
        item1.setQuantity(1);

        OrderCreateRequestDTO.ItemRequestDTO item2 = new OrderCreateRequestDTO.ItemRequestDTO();
        item2.setProductName("Паста Карбонара");
        item2.setQuantity(null);

        request.setItems(List.of(item1, item2));
        return request;
    }

    private OrderItem createOrderItem(Order order, Item item) {
        OrderItem orderItem = new OrderItem();
        orderItem.setId(1L);
        orderItem.setOrder(order);
        orderItem.setItem(item);
        return orderItem;
    }

    ///createOrder()

    @Nested
    @DisplayName("Тесты createOrder() - создание заказа")
    class CreateOrderTests {

        @Test
        @DisplayName("Успешное создание заказа с одним блюдом")
        void createOrder_ShouldCreateOrderSuccessfully() {

            OrderCreateRequestDTO request = createOrderRequest(TEST_USER_ID, TEST_ITEM_NAME, 2);
            Item item = createTestItem(1L, TEST_ITEM_NAME, TEST_ITEM_PRICE);
            Order savedOrder = createTestOrder(TEST_ORDER_ID, TEST_USER_ID, OrderStatus.PENDING.name(), TEST_TOTAL_AMOUNT);

            when(itemRepository.findAllByItemNameIn(anyList())).thenReturn(List.of(item));
            when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
            when(kafkaTemplate.send(anyString(), any())).thenReturn(null);


            OrderResponseDTO response = orderService.createOrder(request);

            /// Проверки
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(TEST_ORDER_ID);
            assertThat(response.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));
            assertThat(response.getItems()).containsExactly(TEST_ITEM_NAME, TEST_ITEM_NAME);

            ArgumentCaptor<List<OrderItem>> orderItemsCaptor = ArgumentCaptor.forClass(List.class);
            verify(orderItemRepository).saveAll(orderItemsCaptor.capture());
            List<OrderItem> savedOrderItems = orderItemsCaptor.getValue();
            assertThat(savedOrderItems).hasSize(2);
            assertThat(savedOrderItems).allMatch(oi -> oi.getItem().getItemName().equals(TEST_ITEM_NAME));

            verify(kafkaMetrics).incrementProduced();
            verify(kafkaMetrics).incrementOrderCreated();
        }

        @Test
        @DisplayName("Успешное создание заказа с несколькими блюдами")
        void createOrder_ShouldCreateOrderWithMultipleItems() {

            OrderCreateRequestDTO request = createOrderRequestWithMultipleItems(TEST_USER_ID);
            Item item1 = createTestItem(1L, "Пицца Маргарита", BigDecimal.valueOf(500));
            Item item2 = createTestItem(2L, "Паста Карбонара", BigDecimal.valueOf(450));
            Order savedOrder = createTestOrder(TEST_ORDER_ID, TEST_USER_ID, OrderStatus.PENDING.name(), BigDecimal.valueOf(1450));

            when(itemRepository.findAllByItemNameIn(anyList())).thenReturn(List.of(item1, item2));
            when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
            when(kafkaTemplate.send(anyString(), any())).thenReturn(null);


            OrderResponseDTO response = orderService.createOrder(request);

            /// Проверки
            assertThat(response.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(1450));
            assertThat(response.getItems()).containsExactly("Пицца Маргарита", "Пицца Маргарита", "Паста Карбонара");

            ArgumentCaptor<List<OrderItem>> orderItemsCaptor = ArgumentCaptor.forClass(List.class);
            verify(orderItemRepository).saveAll(orderItemsCaptor.capture());
            List<OrderItem> savedOrderItems = orderItemsCaptor.getValue();
            assertThat(savedOrderItems).hasSize(3);

            var itemsByName = savedOrderItems.stream()
                    .collect(Collectors.groupingBy(oi -> oi.getItem().getItemName()));
            assertThat(itemsByName.get("Пицца Маргарита")).hasSize(2);
            assertThat(itemsByName.get("Паста Карбонара")).hasSize(1);
        }

        @Test
        @DisplayName("Создание заказа с пустым списком блюд - выбрасывает исключение")
        void createOrder_ShouldThrowException_WhenItemsEmpty() {

            OrderCreateRequestDTO request = new OrderCreateRequestDTO();
            request.setUserId(TEST_USER_ID);
            request.setItems(Collections.emptyList());


            assertThatThrownBy(() -> orderService.createOrder(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Список блюд не может быть пустым");

            /// Проверки
            verify(orderRepository, never()).save(any(Order.class));
            verify(orderItemRepository, never()).saveAll(anyList());
            verify(kafkaTemplate, never()).send(anyString(), any());
        }

        @Test
        @DisplayName("Создание заказа с null полями в ItemRequest - пропускает их")
        void createOrder_ShouldSkipItemsWithNullFields() {

            OrderCreateRequestDTO request = createOrderRequestWithNullFields(TEST_USER_ID);
            Item item2 = createTestItem(2L, "Паста Карбонара", BigDecimal.valueOf(450));
            Order savedOrder = createTestOrder(TEST_ORDER_ID, TEST_USER_ID, OrderStatus.PENDING.name(), BigDecimal.valueOf(0));

            when(itemRepository.findAllByItemNameIn(anyList())).thenReturn(List.of(item2));
            when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
            when(kafkaTemplate.send(anyString(), any())).thenReturn(null);


            OrderResponseDTO response = orderService.createOrder(request);

            /// Проверки
            assertThat(response).isNotNull();
            // OrderItem НЕ создаются, item-ы имеют null поля
            verify(orderItemRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("Создание заказа с несуществующим блюдом - выбрасывает исключение")
        void createOrder_ShouldThrowException_WhenItemNotFoundInMenu() {

            OrderCreateRequestDTO request = createOrderRequest(TEST_USER_ID, "Несуществующее блюдо", 1);

            when(itemRepository.findAllByItemNameIn(anyList())).thenReturn(Collections.emptyList());

            /// Проверки
            assertThatThrownBy(() -> orderService.createOrder(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Цена не найдена");

            verify(orderRepository, never()).save(any(Order.class));
            verify(orderItemRepository, never()).saveAll(anyList());
        }
    }

    /// createOrderItems

    @Nested
    @DisplayName("Тесты createOrderItems() - создание связей OrderItem")
    class CreateOrderItemsTests {

        @Test
        @DisplayName("Создает правильное количество OrderItem для каждого блюда")
        void createOrderItems_ShouldCreateCorrectNumberOfOrderItems() {

            OrderCreateRequestDTO request = createOrderRequestWithMultipleItems(TEST_USER_ID);
            Item item1 = createTestItem(1L, "Пицца Маргарита", BigDecimal.valueOf(500));
            Item item2 = createTestItem(2L, "Паста Карбонара", BigDecimal.valueOf(450));
            Order savedOrder = createTestOrder(TEST_ORDER_ID, TEST_USER_ID, OrderStatus.PENDING.name(), BigDecimal.valueOf(1450));

            when(itemRepository.findAllByItemNameIn(anyList())).thenReturn(List.of(item1, item2));
            when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
            when(kafkaTemplate.send(anyString(), any())).thenReturn(null);


            orderService.createOrder(request);

            /// Проверки
            ArgumentCaptor<List<OrderItem>> orderItemsCaptor = ArgumentCaptor.forClass(List.class);
            verify(orderItemRepository).saveAll(orderItemsCaptor.capture());

            List<OrderItem> savedOrderItems = orderItemsCaptor.getValue();
            assertThat(savedOrderItems).hasSize(3);
            savedOrderItems.forEach(oi -> assertThat(oi.getOrder().getId()).isEqualTo(TEST_ORDER_ID));

            var itemCount = savedOrderItems.stream()
                    .collect(Collectors.groupingBy(
                            oi -> oi.getItem().getItemName(),
                            Collectors.counting()
                    ));
            assertThat(itemCount.get("Пицца Маргарита")).isEqualTo(2);
            assertThat(itemCount.get("Паста Карбонара")).isEqualTo(1);
        }

        @Test
        @DisplayName("Правильно обрабатывает одинаковые блюда с разными количествами")
        void createOrderItems_ShouldHandleDuplicateItemsCorrectly() {

            OrderCreateRequestDTO request = new OrderCreateRequestDTO();
            request.setUserId(TEST_USER_ID);

            OrderCreateRequestDTO.ItemRequestDTO item1 = new OrderCreateRequestDTO.ItemRequestDTO();
            item1.setProductName("Пицца Маргарита");
            item1.setQuantity(2);

            OrderCreateRequestDTO.ItemRequestDTO item2 = new OrderCreateRequestDTO.ItemRequestDTO();
            item2.setProductName("Пицца Маргарита");
            item2.setQuantity(3);

            request.setItems(List.of(item1, item2));

            Item pizza = createTestItem(1L, "Пицца Маргарита", BigDecimal.valueOf(500));
            Order savedOrder = createTestOrder(TEST_ORDER_ID, TEST_USER_ID, OrderStatus.PENDING.name(), BigDecimal.valueOf(2500));

            when(itemRepository.findAllByItemNameIn(anyList())).thenReturn(List.of(pizza));
            when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
            when(kafkaTemplate.send(anyString(), any())).thenReturn(null);


            orderService.createOrder(request);

            /// Проверки
            ArgumentCaptor<List<OrderItem>> orderItemsCaptor = ArgumentCaptor.forClass(List.class);
            verify(orderItemRepository).saveAll(orderItemsCaptor.capture());

            List<OrderItem> savedOrderItems = orderItemsCaptor.getValue();
            assertThat(savedOrderItems).hasSize(5);
            savedOrderItems.forEach(oi ->
                    assertThat(oi.getItem().getItemName()).isEqualTo("Пицца Маргарита")
            );
        }

        @Test
        @DisplayName("Пропускает OrderItem с quantity = 0")
        void createOrderItems_ShouldSkipOrderItem_WhenQuantityIsZero() {

            OrderCreateRequestDTO request = new OrderCreateRequestDTO();
            request.setUserId(TEST_USER_ID);

            OrderCreateRequestDTO.ItemRequestDTO item = new OrderCreateRequestDTO.ItemRequestDTO();
            item.setProductName("Пицца Маргарита");
            item.setQuantity(0);
            request.setItems(List.of(item));

            Item pizza = createTestItem(1L, "Пицца Маргарита", BigDecimal.valueOf(500));
            Order savedOrder = createTestOrder(TEST_ORDER_ID, TEST_USER_ID, OrderStatus.PENDING.name(), BigDecimal.valueOf(0));

            when(itemRepository.findAllByItemNameIn(anyList())).thenReturn(List.of(pizza));
            when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
            when(kafkaTemplate.send(anyString(), any())).thenReturn(null);

            /// вызываем
            orderService.createOrder(request);

            /// Проверки
            /// OrderItem НЕ создаются, т.к. quantity = 0
            verify(orderItemRepository, never()).saveAll(anyList());
        }
    }

    /// getFullOrder()

    @Nested
    @DisplayName("Тесты getFullOrder()")
    class GetFullOrderTests {

        @Test
        @DisplayName("Возвращает заказ по ID")
        void getFullOrder_ShouldReturnOrder() {

            Order order = createTestOrder(TEST_ORDER_ID, TEST_USER_ID, OrderStatus.PENDING.name(), TEST_TOTAL_AMOUNT);
            List<OrderItem> orderItems = List.of(
                    createOrderItem(order, createTestItem(1L, TEST_ITEM_NAME, TEST_ITEM_PRICE))
            );

            when(orderRepository.findById(TEST_ORDER_ID)).thenReturn(Optional.of(order));
            when(orderItemRepository.findByOrderId(TEST_ORDER_ID)).thenReturn(orderItems);

            /// вызываем
            OrderResponseDTO response = orderService.getFullOrder(TEST_ORDER_ID);

            /// Проверки
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(TEST_ORDER_ID);
            assertThat(response.getItems()).containsExactly(TEST_ITEM_NAME);

            verify(orderRepository).findById(TEST_ORDER_ID);
            verify(orderItemRepository).findByOrderId(TEST_ORDER_ID);
        }

        @Test
        @DisplayName("Выбрасывает OrderNotFoundException при отсутствии заказа")
        void getFullOrder_ShouldThrowOrderNotFoundException_WhenOrderNotFound() {

            Long nonExistentId = 999L;
            when(orderRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            /// Проверки
            assertThatThrownBy(() -> orderService.getFullOrder(nonExistentId))
                    .isInstanceOf(OrderNotFoundException.class)
                    .hasMessage("Заказ не найден: " + nonExistentId);
        }
    }

    /// getOrderItemsList()

    @Nested
    @DisplayName("Тесты getOrderItemsList()")
    class GetOrderItemsListTests {

        @Test
        @DisplayName("Возвращает список названий блюд для заказа")
        void getOrderItemsList_ShouldReturnItemNames() {

            Order order = createTestOrder(TEST_ORDER_ID, TEST_USER_ID, OrderStatus.PENDING.name(), TEST_TOTAL_AMOUNT);
            Item item1 = createTestItem(1L, "Пицца Маргарита", BigDecimal.valueOf(500));
            Item item2 = createTestItem(2L, "Паста Карбонара", BigDecimal.valueOf(450));

            List<OrderItem> orderItems = List.of(
                    createOrderItem(order, item1),
                    createOrderItem(order, item1),
                    createOrderItem(order, item2)
            );

            when(orderItemRepository.findByOrderId(TEST_ORDER_ID)).thenReturn(orderItems);


            List<String> result = orderService.getOrderItemsList(TEST_ORDER_ID);

            /// Проверки
            assertThat(result).hasSize(3);
            assertThat(result).containsExactly("Пицца Маргарита", "Пицца Маргарита", "Паста Карбонара");
        }

        @Test
        @DisplayName("Возвращает пустой список, если OrderItem содержит null Item")
        void getOrderItemsList_ShouldReturnEmptyList_WhenItemIsNull() {

            Order order = createTestOrder(TEST_ORDER_ID, TEST_USER_ID, OrderStatus.PENDING.name(), TEST_TOTAL_AMOUNT);

            OrderItem orderItem = new OrderItem();
            orderItem.setId(1L);
            orderItem.setOrder(order);
            orderItem.setItem(null);

            when(orderItemRepository.findByOrderId(TEST_ORDER_ID)).thenReturn(List.of(orderItem));


            List<String> result = orderService.getOrderItemsList(TEST_ORDER_ID);

            /// Проверки
            assertThat(result).isEmpty();
        }
    }

    /// KAFKA CONSUMERS

    @Nested
    @DisplayName("Тесты Kafka Consumers")
    class KafkaConsumerTests {

        @Test
        @DisplayName("handlePaymentSuccess() - меняет статус на PAID")
        void handlePaymentSuccess_ShouldChangeStatusToPaid() {

            Order order = createTestOrder(TEST_ORDER_ID, TEST_USER_ID, OrderStatus.PENDING.name(), TEST_TOTAL_AMOUNT);
            PaymentProcessedEvent event = new PaymentProcessedEvent(TEST_ORDER_ID, "SUCCESS", "OK");

            when(orderRepository.findById(TEST_ORDER_ID)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenReturn(order);
            when(orderItemRepository.findByOrderId(TEST_ORDER_ID)).thenReturn(List.of());
            when(kafkaTemplate.send(anyString(), any())).thenReturn(null);


            orderService.handlePaymentSuccess(event);

            /// Проверки
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID.name());
            verify(orderRepository).save(order);
            verify(kafkaMetrics).incrementConsumed();
            verify(kafkaMetrics).incrementOrderPaid();
            verify(kafkaTemplate).send(eq("order-service.order.paid"), any(OrderPaidEvent.class));
        }

        @Test
        @DisplayName("handlePaymentFailed() - меняет статус на CANCELLED")
        void handlePaymentFailed_ShouldChangeStatusToCancelled() {

            Order order = createTestOrder(TEST_ORDER_ID, TEST_USER_ID, OrderStatus.PENDING.name(), TEST_TOTAL_AMOUNT);
            PaymentProcessedEvent event = new PaymentProcessedEvent(TEST_ORDER_ID, "FAILED", "Ошибка оплаты");

            when(orderRepository.findById(TEST_ORDER_ID)).thenReturn(Optional.of(order));


            orderService.handlePaymentFailed(event);

            /// Проверки
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED.name());
            verify(orderRepository).save(order);
            verify(kafkaMetrics).incrementConsumed();
            verify(kafkaMetrics).incrementOrderCancelled();
        }

        @Test
        @DisplayName("handleCookingStarted() - меняет статус на PREPARING")
        void handleCookingStarted_ShouldChangeStatusToPreparing() {

            Order order = createTestOrder(TEST_ORDER_ID, TEST_USER_ID, OrderStatus.PAID.name(), TEST_TOTAL_AMOUNT);

            when(orderRepository.findById(TEST_ORDER_ID)).thenReturn(Optional.of(order));


            orderService.handleCookingStarted(TEST_ORDER_ID);

            /// Проверки
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PREPARING.name());
            verify(orderRepository).save(order);
            verify(kafkaMetrics).incrementConsumed();
        }

        @Test
        @DisplayName("handleOrderReady() - меняет статус на READY")
        void handleOrderReady_ShouldChangeStatusToReady() {

            Order order = createTestOrder(TEST_ORDER_ID, TEST_USER_ID, OrderStatus.PREPARING.name(), TEST_TOTAL_AMOUNT);
            KitchenOrderReadyEvent event = new KitchenOrderReadyEvent(TEST_ORDER_ID, "ticket-123", "READY");

            when(orderRepository.findById(TEST_ORDER_ID)).thenReturn(Optional.of(order));


            orderService.handleOrderReady(event);

            /// Проверки
            assertThat(order.getStatus()).isEqualTo(OrderStatus.READY.name());
            verify(orderRepository).save(order);
            verify(kafkaMetrics).incrementConsumed();
            verify(kafkaMetrics).incrementOrderReady();
        }

        @Test
        @DisplayName("handleOrderDelivered() - меняет статус на DELIVERED")
        void handleOrderDelivered_ShouldChangeStatusToDelivered() {

            Order order = createTestOrder(TEST_ORDER_ID, TEST_USER_ID, OrderStatus.READY.name(), TEST_TOTAL_AMOUNT);

            when(orderRepository.findById(TEST_ORDER_ID)).thenReturn(Optional.of(order));


            orderService.handleOrderDelivered(TEST_ORDER_ID);

            /// Проверки
            assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED.name());
            verify(orderRepository).save(order);
            verify(kafkaMetrics).incrementConsumed();
        }

        @Test
        @DisplayName("Все обработчики логируют ошибку при отсутствии заказа")
        void allHandlers_ShouldLogError_WhenOrderNotFound() {

            Long nonExistentId = 999L;
            when(orderRepository.findById(nonExistentId)).thenReturn(Optional.empty());


            orderService.handleCookingStarted(nonExistentId);
            orderService.handleOrderReady(new KitchenOrderReadyEvent(nonExistentId, "ticket", "READY"));
            orderService.handleOrderDelivered(nonExistentId);
            orderService.handlePaymentFailed(new PaymentProcessedEvent(nonExistentId, "FAILED", "Error"));

            /// Проверки
            verify(kafkaMetrics, times(4)).incrementProcessingError();
            verify(orderRepository, never()).save(any(Order.class));
        }
    }

    //  КЭШИРОВАНИЕ

    @Nested
    @DisplayName("Тесты кэширования")
    class CacheTests {

        @Test
        @DisplayName("@Cacheable - кэширует результат getFullOrder()")
        void getFullOrder_ShouldCacheResult() {

            Order order = createTestOrder(TEST_ORDER_ID, TEST_USER_ID, OrderStatus.PENDING.name(), TEST_TOTAL_AMOUNT);
            List<OrderItem> orderItems = List.of(
                    createOrderItem(order, createTestItem(1L, TEST_ITEM_NAME, TEST_ITEM_PRICE))
            );

            when(orderRepository.findById(TEST_ORDER_ID)).thenReturn(Optional.of(order));
            when(orderItemRepository.findByOrderId(TEST_ORDER_ID)).thenReturn(orderItems);

            /// вызываем дважды
            OrderResponseDTO response1 = orderService.getFullOrder(TEST_ORDER_ID);
            OrderResponseDTO response2 = orderService.getFullOrder(TEST_ORDER_ID);

            /// Проверки
            assertThat(response1).isEqualTo(response2);
            // Метод вызывается 2 раза (кэш не работает в unit-тестах)
            verify(orderRepository, times(2)).findById(TEST_ORDER_ID);
            verify(orderItemRepository, times(2)).findByOrderId(TEST_ORDER_ID);
        }
    }
}