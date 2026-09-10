package com.restaurant.gateway_service.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

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
        // вызывается fallback для Order Service
        Mono<Map<String, Object>> result = fallbackController.ordersFallback();

        // проверяем реактивный ответ
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.get("status")).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
                    assertThat(response.get("message")).isEqualTo("Сервис заказов временно недоступен. Попробуйте позже.");
                    assertThat(response.get("type")).isEqualTo("FALLBACK_RESPONSE");
                    assertThat(response.get("timestamp")).isNotNull();
                    assertThat(response.get("error")).isEqualTo("Service Unavailable");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("paymentsFallback() - возвращает корректный fallback ответ")
    void paymentsFallback_ShouldReturnCorrectResponse() {
        Mono<Map<String, Object>> result = fallbackController.paymentsFallback();

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.get("status")).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
                    assertThat(response.get("message")).isEqualTo("Платежный сервис временно недоступен.");
                    assertThat(response.get("type")).isEqualTo("FALLBACK_RESPONSE");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("kitchenFallback() - возвращает корректный fallback ответ")
    void kitchenFallback_ShouldReturnCorrectResponse() {
        Mono<Map<String, Object>> result = fallbackController.kitchenFallback();

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.get("status")).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
                    assertThat(response.get("message")).isEqualTo("Кухня временно недоступна.");
                    assertThat(response.get("type")).isEqualTo("FALLBACK_RESPONSE");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("notificationsFallback() - возвращает корректный fallback ответ")
    void notificationsFallback_ShouldReturnCorrectResponse() {
        Mono<Map<String, Object>> result = fallbackController.notificationsFallback();

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.get("status")).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
                    assertThat(response.get("message")).isEqualTo("Служба уведомлений временно недоступна.");
                    assertThat(response.get("type")).isEqualTo("FALLBACK_RESPONSE");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("authFallback() - возвращает корректный fallback ответ")
    void authFallback_ShouldReturnCorrectResponse() {
        Mono<Map<String, Object>> result = fallbackController.authFallback();

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.get("status")).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
                    assertThat(response.get("message"))
                            .isEqualTo("Сервис авторизации временно недоступен. Пожалуйста, повторите попытку позже.");
                    assertThat(response.get("type")).isEqualTo("FALLBACK_RESPONSE");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Все fallback ответы имеют одинаковую структуру")
    void allFallbackResponses_ShouldHaveSameStructure() {
        Mono<Map<String, Object>> result = fallbackController.ordersFallback();

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response).containsKeys("status", "error", "message", "timestamp", "type");
                    assertThat(response.get("type")).isEqualTo("FALLBACK_RESPONSE");
                    assertThat(response.get("status")).isEqualTo(503);
                    assertThat(response.get("error")).isEqualTo("Service Unavailable");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Все fallback ответы содержат timestamp")
    void allFallbackResponses_ShouldContainTimestamp() {
        Mono<Map<String, Object>> result = fallbackController.ordersFallback();

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.get("timestamp")).isNotNull();
                    assertThat(response.get("timestamp")).isInstanceOf(Long.class);
                    assertThat((Long) response.get("timestamp")).isPositive();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Статус всегда 503 Service Unavailable")
    void allFallbackResponses_ShouldHave503Status() {
        // Проверяем все методы
        StepVerifier.create(fallbackController.ordersFallback())
                .assertNext(r -> assertThat(r.get("status")).isEqualTo(503))
                .verifyComplete();

        StepVerifier.create(fallbackController.paymentsFallback())
                .assertNext(r -> assertThat(r.get("status")).isEqualTo(503))
                .verifyComplete();

        StepVerifier.create(fallbackController.kitchenFallback())
                .assertNext(r -> assertThat(r.get("status")).isEqualTo(503))
                .verifyComplete();

        StepVerifier.create(fallbackController.notificationsFallback())
                .assertNext(r -> assertThat(r.get("status")).isEqualTo(503))
                .verifyComplete();

        StepVerifier.create(fallbackController.authFallback())
                .assertNext(r -> assertThat(r.get("status")).isEqualTo(503))
                .verifyComplete();
    }
}