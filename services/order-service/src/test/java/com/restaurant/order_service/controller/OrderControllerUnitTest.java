package com.restaurant.order_service.controller;

import com.restaurant.order_service.dto.OrderCreateRequestDTO;
import com.restaurant.order_service.dto.OrderResponseDTO;
import com.restaurant.order_service.metrics.OrderMetrics;
import com.restaurant.order_service.service.OrderService;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit-тесты OrderController")
class OrderControllerUnitTest {

    @Mock
    private OrderService orderService;

    @Mock
    private OrderMetrics orderMetrics;

    @Mock
    private Timer.Sample timerSample;

    @InjectMocks
    private OrderController orderController;

    private final Long TEST_USER_ID = 1001L;
    private final Long TEST_ORDER_ID = 1L;

    @BeforeEach
    void setUp() {
        lenient().when(orderMetrics.startTimer()).thenReturn(timerSample);
        lenient().doNothing().when(orderMetrics).stopTimer(any(Timer.Sample.class));
        lenient().doNothing().when(orderMetrics).incrementOrdersCreated();
    }

    private OrderCreateRequestDTO createOrderRequest() {
        OrderCreateRequestDTO request = new OrderCreateRequestDTO();
        request.setUserId(TEST_USER_ID);

        OrderCreateRequestDTO.ItemRequestDTO item = new OrderCreateRequestDTO.ItemRequestDTO();
        item.setProductName("Пицца Маргарита");
        item.setQuantity(2);
        request.setItems(List.of(item));

        return request;
    }

    private OrderResponseDTO createOrderResponse() {
        return OrderResponseDTO.builder()
                .id(TEST_ORDER_ID)
                .userId(TEST_USER_ID)
                .status("PENDING")
                .items(List.of("Пицца Маргарита", "Пицца Маргарита"))
                .totalAmount(BigDecimal.valueOf(1000))
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("POST /api/orders - успешное создание заказа")
    void createOrder_ShouldReturnCreated() {

        OrderCreateRequestDTO request = createOrderRequest();
        OrderResponseDTO response = createOrderResponse();

        when(orderService.createOrder(any(OrderCreateRequestDTO.class))).thenReturn(response);


        ResponseEntity<OrderResponseDTO> result = orderController.createOrder(request, TEST_USER_ID);

        /// Проверки
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getId()).isEqualTo(TEST_ORDER_ID);
        assertThat(result.getBody().getUserId()).isEqualTo(TEST_USER_ID);
        assertThat(result.getBody().getStatus()).isEqualTo("PENDING");

        verify(orderService).createOrder(any(OrderCreateRequestDTO.class));
        verify(orderMetrics).incrementOrdersCreated();
        verify(orderMetrics).startTimer();
        verify(orderMetrics).stopTimer(any(Timer.Sample.class));
    }

    @Test
    @DisplayName("POST /api/orders - проверяет, что userId установлен из заголовка")
    void createOrder_ShouldSetUserIdFromHeader() {

        OrderCreateRequestDTO request = createOrderRequest();
        OrderResponseDTO response = createOrderResponse();

        /// Убедимся, что userId в запросе равен null
        request.setUserId(null);

        when(orderService.createOrder(any(OrderCreateRequestDTO.class))).thenReturn(response);


        orderController.createOrder(request, TEST_USER_ID);

        /// Проверки
        /// Проверяем, что userId был установлен в запросе перед передачей в сервис
        assertThat(request.getUserId()).isEqualTo(TEST_USER_ID);
        verify(orderService).createOrder(request);
    }

    @Test
    @DisplayName("GET /api/orders/{id} - возвращает заказ по ID")
    void getOrder_ShouldReturnOrder() {

        OrderResponseDTO response = createOrderResponse();

        when(orderService.getFullOrder(TEST_ORDER_ID)).thenReturn(response);


        ResponseEntity<OrderResponseDTO> result = orderController.getOrder(TEST_ORDER_ID);

        /// Проверки
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getId()).isEqualTo(TEST_ORDER_ID);
        assertThat(result.getBody().getUserId()).isEqualTo(TEST_USER_ID);

        verify(orderService).getFullOrder(TEST_ORDER_ID);
        /// Проверяем, что метрики НЕ вызываются для GET запроса
        verify(orderMetrics, never()).startTimer();
        verify(orderMetrics, never()).stopTimer(any(Timer.Sample.class));
        verify(orderMetrics, never()).incrementOrdersCreated();
    }
}