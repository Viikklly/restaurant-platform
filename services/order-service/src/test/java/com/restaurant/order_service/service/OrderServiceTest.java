package com.restaurant.order_service.service;

import com.restaurant.common.events.OrderCreatedEvent;
import com.restaurant.common.events.OrderPaidEvent;
import com.restaurant.common.events.PaymentProcessedEvent;
import com.restaurant.order_service.dto.ItemDTO;
import com.restaurant.order_service.dto.OrderCreateRequestDTO;
import com.restaurant.order_service.dto.OrderResponseDTO;
import com.restaurant.order_service.entity.Item;
import com.restaurant.order_service.entity.Order;
import com.restaurant.order_service.entity.OrderStatus;
import com.restaurant.order_service.repository.ItemRepository;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    @Mock
    private OrderRepository orderRepository;

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
        /// Создаём запрос на заказ
        request = new OrderCreateRequestDTO();
        request.setUserId(1L);

        ItemDTO itemDTO = new ItemDTO();
        itemDTO.setProductName("Пицца");
        itemDTO.setQuantity(2);
        request.setItems(List.of(itemDTO));

        /// Создаём блюдо
        pizza = Item.builder()
                .id(1L)
                .itemName("Пицца")
                .itemPrice(new BigDecimal("499.99"))
                .isAvailable(true)
                .build();

        /// Создаём заказ
        order = new Order();
        order.setId(1L);
        order.setUserId(1L);
        order.setStatus(OrderStatus.PENDING.name());
        order.setTotalAmount(new BigDecimal("999.98"));
        order.setCreatedAt(LocalDateTime.now());
    }

    /// Тесты создания заказа
    @Test
    void createOrder_ShouldSuccess_WhenItemsExist() {

        when(itemRepository.findAllByItemNameIn(List.of("Пицца"))).thenReturn(List.of(pizza));
        when(orderRepository.save(any(Order.class))).thenReturn(order);


        OrderResponseDTO response = orderService.createOrder(request);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getStatus()).isEqualTo("PENDING");
        assertThat(response.getTotalAmount()).isEqualTo(new BigDecimal("999.98"));

        verify(orderRepository).save(any(Order.class));
        verify(kafkaTemplate).send(eq("order-service.order.created"), any(OrderCreatedEvent.class));
    }

    @Test
    void createOrder_ShouldThrowException_WhenItemNotFound() {

        when(itemRepository.findAllByItemNameIn(List.of("Пицца"))).thenReturn(List.of());

        
        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Блюдо не найдено в меню");
    }

    /// Обработка платежей

    @Test
    void handlePaymentSuccess_ShouldChangeStatusToPaid() {

        PaymentProcessedEvent event = new PaymentProcessedEvent(1L, "SUCCESS", "OK");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

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
    void handlePaymentSuccess_ShouldThrowException_WhenOrderNotFound() {

        PaymentProcessedEvent event = new PaymentProcessedEvent(999L, "SUCCESS", "OK");
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.handlePaymentSuccess(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Заказ не найден");
    }


    /// Получение заказа

    @Test
    void getOrderEntity_ShouldReturnOrder_WhenExists() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        Order found = orderService.getOrderEntity(1L);

        assertThat(found.getId()).isEqualTo(1L);
        assertThat(found.getUserId()).isEqualTo(1L);
    }

    @Test
    void getOrderEntity_ShouldThrowException_WhenNotFound() {
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderEntity(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Заказ не найден");

    }

}