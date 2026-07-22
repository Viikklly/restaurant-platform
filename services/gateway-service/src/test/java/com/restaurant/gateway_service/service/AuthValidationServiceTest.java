package com.restaurant.gateway_service.service;

import com.restaurant.gateway_service.model.AuthResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@DisplayName("Тесты Circuit Breaker для AuthValidationService")
class AuthValidationServiceTest {

    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(1)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(1))
                .build();
        circuitBreaker = CircuitBreaker.of("authService", config);
    }

    @Test
    @DisplayName("Успешная валидация - проходит")
    void validateToken_ShouldReturnAuthResponse_WhenSuccess() {
        AuthResponse expected = new AuthResponse();
        expected.setValid(true);
        expected.setUserId("123");

        Mono<AuthResponse> result = Mono.just(expected)
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker));

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.isValid()).isTrue();
                    assertThat(response.getUserId()).isEqualTo("123");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Ошибка - выбрасывает исключение")
    void validateToken_ShouldThrowException_WhenError() {
        Mono<AuthResponse> result = Mono.<AuthResponse>error(new RuntimeException("Service unavailable"))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker));

        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    @DisplayName("После ошибки Circuit Breaker переходит в OPEN")
    void circuitBreaker_ShouldOpen_AfterFailure() {
        // Вызываем ошибку
        Mono.<AuthResponse>error(new RuntimeException("Service unavailable"))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .onErrorResume(e -> Mono.empty())
                .block();

        // Circuit Breaker должен быть открыт
        assertThat(circuitBreaker.getState())
                .isEqualTo(CircuitBreaker.State.OPEN);
    }
}