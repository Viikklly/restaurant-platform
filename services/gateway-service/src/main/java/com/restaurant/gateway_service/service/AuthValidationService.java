package com.restaurant.gateway_service.service;


import com.restaurant.gateway_service.model.AuthResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthValidationService {

    private final TokenValidationClient tokenValidationClient;


    /// Вызывает валидацию токена через HTTP запрос к Auth Service

    /// @CircuitBreaker	Включает защитный механизм "автоматический выключатель"
    /// name = "authService"	Имя этого выключателя (для мониторинга)
    /// fallbackMethod = "fallbackValidateToken"	Какой метод вызывать при ошибке
    @CircuitBreaker(name = "authService", fallbackMethod = "fallbackValidateToken")
    public Mono<AuthResponse> validateToken(String token) {
        return tokenValidationClient.validateToken(token);
    }

    /// Fallback метод при разомкнутом Circuit Breaker
    private Mono<AuthResponse> fallbackValidateToken(String token, Throwable throwable) {
        log.warn("Circuit breaker open or auth service unavailable: {}", throwable.getMessage());


        /// Создаем "запасной" ответ без вызова реального сервиса
        AuthResponse fallbackResponse = new AuthResponse();
        fallbackResponse.setValid(false);
        fallbackResponse.setMessage("Auth service temporarily unavailable. Please try again later.");

        return Mono.just(fallbackResponse);
    }
}