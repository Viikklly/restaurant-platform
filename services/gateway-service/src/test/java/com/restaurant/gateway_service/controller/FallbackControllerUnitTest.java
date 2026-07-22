package com.restaurant.gateway_service.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Unit-тесты FallbackController")
class FallbackControllerUnitTest {

    private FallbackController fallbackController;

    @BeforeEach
    void setUp() {
        fallbackController = new FallbackController();
    }

    @Test
    @DisplayName("ordersFallback() - возвращает корректный fallback ответ")
    void ordersFallback_ShouldReturnCorrectResponse() {
        /// вызывается fallback для Order Service
        ResponseEntity<Map<String, Object>> response = fallbackController.ordersFallback();

        /// возвращается ответ с статусом 503 и сообщением о недоступности
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo(503);
        assertThat(response.getBody().get("message")).isEqualTo("Order service is temporarily unavailable");
        assertThat(response.getBody().get("type")).isEqualTo("FALLBACK_RESPONSE");
        assertThat(response.getBody().get("timestamp")).isNotNull();
    }

    @Test
    @DisplayName("paymentsFallback() - возвращает корректный fallback ответ")
    void paymentsFallback_ShouldReturnCorrectResponse() {
        /// вызывается fallback для Payment Service
        ResponseEntity<Map<String, Object>> response = fallbackController.paymentsFallback();

        /// возвращается ответ с статусом 503 и сообщением о недоступности
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("message")).isEqualTo("Payment service is temporarily unavailable");
    }

    @Test
    @DisplayName("kitchenFallback() - возвращает корректный fallback ответ")
    void kitchenFallback_ShouldReturnCorrectResponse() {
        /// вызывается fallback для Kitchen Service
        ResponseEntity<Map<String, Object>> response = fallbackController.kitchenFallback();

        /// возвращается ответ с статусом 503 и сообщением о недоступности
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("message")).isEqualTo("Kitchen service is temporarily unavailable");
    }

    @Test
    @DisplayName("notificationsFallback() - возвращает корректный fallback ответ")
    void notificationsFallback_ShouldReturnCorrectResponse() {
        /// вызывается fallback для Notification Service
        ResponseEntity<Map<String, Object>> response = fallbackController.notificationsFallback();

        /// возвращается ответ с статусом 503 и сообщением о недоступности
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("message")).isEqualTo("Notification service is temporarily unavailable");
    }

    @Test
    @DisplayName("authFallback() - возвращает корректный fallback ответ")
    void authFallback_ShouldReturnCorrectResponse() {
        /// вызывается fallback для Auth Service
        ResponseEntity<Map<String, Object>> response = fallbackController.authFallback();

        /// возвращается ответ с статусом 503 и сообщением о недоступности
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("message"))
                .isEqualTo("Auth service is temporarily unavailable. Please try again later.");
    }

    @Test
    @DisplayName("Все fallback ответы имеют одинаковую структуру")
    void allFallbackResponses_ShouldHaveSameStructure() {
        /// вызывается любой fallback
        ResponseEntity<Map<String, Object>> response = fallbackController.ordersFallback();
        Map<String, Object> body = response.getBody();

        /// все ответы имеют одинаковую структуру
        assertThat(body).containsKeys("status", "error", "message", "timestamp", "type");
        assertThat(body.get("type")).isEqualTo("FALLBACK_RESPONSE");
        assertThat(body.get("status")).isEqualTo(503);
        assertThat(body.get("error")).isEqualTo("Service Unavailable");
    }
}