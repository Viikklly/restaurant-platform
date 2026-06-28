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
import com.restaurant.order_service.repository.ItemRepository;
import com.restaurant.order_service.repository.OrderItemRepository;
import com.restaurant.order_service.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private OrderService orderService;

    private OrderCreateRequestDTO request;
    private Order order;
    private Item pizza;

    @BeforeEach
    void setUp() {
        // Создаём запрос на заказ
        request = new OrderCreateRequestDTO();
        request.setUserId(1L);

        OrderCreateRequestDTO.ItemRequestDTO itemDTO = new OrderCreateRequestDTO.ItemRequestDTO();
        itemDTO.setProductName("Пицца");
        itemDTO.setQuantity(2);
        request.setItems(List.of(itemDTO));

        // Создаём блюдо
        pizza = Item.builder()
                .id(1L)
                .itemName("Пицца")
                .itemPrice(new BigDecimal("499.99"))
                .isAvailable(true)
                .build();

        // Создаём заказ
        order = new Order();
        order.setId(1L);
        order.setUserId(1L);
        order.setStatus(OrderStatus.PENDING.name());
        order.setTotalAmount(new BigDecimal("999.98"));
        order.setCreatedAt(LocalDateTime.now());
    }

    // Тесты создания заказа

    @Test
    void createOrder_ShouldSuccess_WhenItemsExist() {
        when(itemRepository.findAllByItemNameIn(List.of("Пицца"))).thenReturn(List.of(pizza));
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        OrderResponseDTO response = orderService.createOrder(request);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getStatus()).isEqualTo("PENDING");
        assertThat(response.getTotalAmount()).isEqualTo(new BigDecimal("999.98"));
        assertThat(response.getItems()).containsExactly("Пицца", "Пицца");

        verify(orderRepository).save(any(Order.class));
        verify(kafkaTemplate).send(eq("order-service.order.created"), any(OrderCreatedEvent.class));
    }

    @Test
    void createOrder_ShouldThrowException_WhenItemNotFound() {
        when(itemRepository.findAllByItemNameIn(List.of("Пицца"))).thenReturn(List.of());

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Цена не найдена для блюда");
    }

    @Test
    void createOrder_ShouldReturnZeroTotal_WhenItemsEmpty() {
        request.setItems(List.of());
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        OrderResponseDTO response = orderService.createOrder(request);

        assertThat(response.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // Тесты получения заказа

    @Test
    void getFullOrder_ShouldReturnOrder_WhenExists() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(1L)).thenReturn(List.of());

        OrderResponseDTO response = orderService.getFullOrder(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(response.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void getFullOrder_ShouldThrowException_WhenNotFound() {
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getFullOrder(999L))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining("Заказ не найден");
    }

    //  Тесты обработки платежей

    @Test
    void handlePaymentSuccess_ShouldChangeStatusToPaid() {
        PaymentProcessedEvent event = new PaymentProcessedEvent(1L, "SUCCESS", "OK");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(1L)).thenReturn(List.of());

        orderService.handlePaymentSuccess(event);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID.name());
        verify(kafkaTemplate).send(eq("order-service.order.paid"), any(OrderPaidEvent.class));
    }

    @Test
    void handlePaymentFailed_ShouldChangeStatusToCancelled() {
        PaymentProcessedEvent event = new PaymentProcessedEvent(1L, "FAILED", "Ошибка");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        orderService.handlePaymentFailed(event);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED.name());
        verify(kafkaTemplate, never()).send(eq("order-service.order.paid"), any());
    }

    @Test
    void handlePaymentSuccess_ShouldNotThrow_WhenOrderNotFound() {
        PaymentProcessedEvent event = new PaymentProcessedEvent(999L, "SUCCESS", "OK");
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        // Метод теперь обрабатывает исключение внутри, не выбрасывает наружу
        orderService.handlePaymentSuccess(event);

        verify(kafkaTemplate, never()).send(anyString(), any());
    }

    //  Тесты обновления статусов

    @Test
    void handleCookingStarted_ShouldChangeStatusToPreparing() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        orderService.handleCookingStarted(1L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PREPARING.name());
    }

    @Test
    void handleOrderReady_ShouldChangeStatusToReady() {
        KitchenOrderReadyEvent event = new KitchenOrderReadyEvent(1L, "ticket-1", "READY");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        orderService.handleOrderReady(event);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.READY.name());
    }

    @Test
    void handleOrderDelivered_ShouldChangeStatusToDelivered() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        orderService.handleOrderDelivered(1L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED.name());
    }

    @Test
    void handleCookingStarted_ShouldNotThrow_WhenOrderNotFound() {
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        orderService.handleCookingStarted(999L);

        // Метод обрабатывает исключение внутри
        verify(orderRepository, never()).save(any());
    }

    // Тесты получения списка блюд

    @Test
    void getOrderItemsList_ShouldReturnItems_WhenExists() {
        // Создаём OrderItem
        OrderItem orderItem = new OrderItem();
        orderItem.setItem(pizza);
        orderItem.setOrder(order);

        when(orderItemRepository.findByOrderId(1L)).thenReturn(List.of(orderItem));

        List<String> items = orderService.getOrderItemsList(1L);

        assertThat(items).containsExactly("Пицца");
    }

    @Test
    void getOrderItemsList_ShouldReturnEmptyList_WhenNoItems() {
        when(orderItemRepository.findByOrderId(1L)).thenReturn(List.of());

        List<String> items = orderService.getOrderItemsList(1L);

        assertThat(items).isEmpty();
    }
}