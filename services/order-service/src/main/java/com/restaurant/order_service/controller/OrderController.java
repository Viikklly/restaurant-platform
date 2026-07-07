package com.restaurant.order_service.controller;


import com.restaurant.order_service.dto.OrderCreateRequestDTO;
import com.restaurant.order_service.dto.OrderDetailsResponseDTO;
import com.restaurant.order_service.dto.OrderResponseDTO;
import com.restaurant.order_service.metrics.OrderMetrics;
import com.restaurant.order_service.service.OrderService;
import io.micrometer.core.instrument.Timer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderService orderService;
    @Autowired
    private final OrderMetrics orderMetrics;  /// Метрики


    @PostMapping
    public ResponseEntity<OrderResponseDTO> createOrder(@Valid @RequestBody OrderCreateRequestDTO request,
                                                        @RequestHeader(value = "X-User-Id", required = true) Long userId) {

        Timer.Sample sample = orderMetrics.startTimer(); /// Для метрик

        log.info(" POST /api/orders. Создание заказа для userID {}", userId);
        /// Устанавливаем userId из заголовка, гарантируя поверенного пользователя
        try {
            request.setUserId(userId);
            OrderResponseDTO response = orderService.createOrder(request);

            orderMetrics.incrementOrdersCreated(); /// Счетчик +1. Неодходимо для метрик

            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } finally {
            orderMetrics.stopTimer(sample); /// Останавливаем замер
        }
    }



    /// Метод для отладки и тестирования
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponseDTO> getOrder(@PathVariable Long id) {
        log.info(" GET /api/orders/{}", id);
        OrderResponseDTO order = orderService.getFullOrder(id);  /// Redis используем кэшируемый метод
        return ResponseEntity.ok(order);
    }


}
