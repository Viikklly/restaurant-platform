package com.restaurant.gateway_service.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * FallbackController - возвращает ответы когда основной сервис недоступен.
 * Все пути должны совпадать с fallbackUri в конфигурации Gateway
 */
@RestController
public class FallbackController {

    private static final Logger log = LoggerFactory.getLogger(FallbackController.class);

    /**
     * Fallback для Order Service
     * Путь: /fallback/orders (совпадает с fallbackUri в конфиге)
     */
    @GetMapping("/fallback/orders")
    public Mono<Map<String, Object>> ordersFallback() {
        log.warn("CircuitBreaker activated for Order Service");
        return createFallbackResponse("Сервис заказов временно недоступен. Попробуйте позже.");
    }

    /**
     * Fallback для Payment Service
     */
    @GetMapping("/fallback/payments")
    public Mono<Map<String, Object>> paymentsFallback() {
        log.warn("CircuitBreaker activated for Payment Service");
        return createFallbackResponse("Платежный сервис временно недоступен.");
    }

    /**
     * Fallback для Kitchen Service
     */
    @GetMapping("/fallback/kitchen")
    public Mono<Map<String, Object>> kitchenFallback() {
        log.warn("CircuitBreaker activated for Kitchen Service");
        return createFallbackResponse("Кухня временно недоступна.");
    }

    /**
     * Fallback для Notification Service
     */
    @GetMapping("/fallback/notifications")
    public Mono<Map<String, Object>> notificationsFallback() {
        log.warn("CircuitBreaker activated for Notification Service");
        return createFallbackResponse("Служба уведомлений временно недоступна.");
    }

    /**
     * Fallback для Auth Service
     */
    @GetMapping("/fallback/auth")
    public Mono<Map<String, Object>> authFallback() {
        log.warn("CircuitBreaker activated for Auth Service");
        return createFallbackResponse(
                "Сервис авторизации временно недоступен. Пожалуйста, повторите попытку позже."
        );
    }

    /**
     * Универсальный метод создания fallback-ответа
     */
    private Mono<Map<String, Object>> createFallbackResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        response.put("error", HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase());
        response.put("message", message);
        response.put("timestamp", System.currentTimeMillis());
        response.put("type", "FALLBACK_RESPONSE");

        return Mono.just(response);
    }
}