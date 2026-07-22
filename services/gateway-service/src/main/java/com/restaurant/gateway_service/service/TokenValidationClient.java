package com.restaurant.gateway_service.service;

import com.restaurant.gateway_service.model.AuthRequest;
import com.restaurant.gateway_service.model.AuthResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

/**
 * HTTP-клиент для валидации JWT-токенов через вызов AUTH-SERVICE.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenValidationClient {

    private final WebClient authServiceWebClient;

    private static final String VALIDATE_TOKEN_PATH = "/auth/validate";

    /**
     * Отправляет запрос на валидацию JWT-токена в AUTH-SERVICE.
     * Выполняет валидацию JWT-токенов с автоматическими повторными попытками
     * при временных сбоях (503, ConnectException) — до 3 попыток с задержкой 500 мс.
     * @param token
     * @return
     */
    public Mono<AuthResponse> validateToken(String token) {
        log.debug("Validating token with auth-service"); /// Лог, что начали валидацию токена

        return authServiceWebClient                         /// Берем настроенный WebClient (из WebClientConfig)
                .post()                                     /// Устанавливаем HTTP метод POST
                .uri(VALIDATE_TOKEN_PATH)                   /// Устанавливаем путь (URL после baseUrl)
                .header("Content-Type", "application/json")              /// Добавляем HTTP заголовок Content-Type: application/json
                .bodyValue(new AuthRequest(token))          /// Создаем объект AuthRequest с токеном и отправляет его в теле запроса
                .retrieve()                                 /// Отправляем запрос и начинаем получать ответ (без указания типа ответа)
                .onStatus(                                  /// Проверяем, является ли статус ошибкой (4xx или 5xx)
                        status -> status.is4xxClientError() || status.is5xxServerError(),
                        clientResponse -> {
                            if (clientResponse.statusCode() == HttpStatus.UNAUTHORIZED) {
                                log.warn("Token validation failed: Unauthorized");
                                // ВАЖНО: возвращаем Mono.error с RuntimeException("Invalid token")
                                return Mono.error(new RuntimeException("Invalid token"));
                            }
                            log.error("Auth service returned error: {}", clientResponse.statusCode());
                            return clientResponse.bodyToMono(String.class)
                                    .flatMap(body -> Mono.error(new RuntimeException(
                                            "Auth service error: " + clientResponse.statusCode() + " - " + body)));
                        }
                )
                .bodyToMono(AuthResponse.class)                     /// Превращаем JSON ответ в объект AuthResponse
                .retryWhen(Retry.backoff(3, Duration.ofMillis(500))                     /// Максимум попыток 3	Начальная задержка 0.5 секунды
                        .filter(throwable -> {
                            if (throwable instanceof WebClientResponseException) {
                                WebClientResponseException ex = (WebClientResponseException) throwable;
                                return ex.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE;
                            }
                            return throwable instanceof java.net.ConnectException;
                        })
                        .doBeforeRetry(retrySignal ->
                                log.warn("Retrying token validation, attempt: {}",
                                        retrySignal.totalRetriesInARow() + 1)))  /// Логируем попытки повторных запросов
                .doOnSuccess(response -> log.debug("Token validation successful for user: {}", response.getUserId()))   /// При успешном ответе логируем userId
                .doOnError(error -> {
                    // При любой ошибке логируем, но не логируем как ошибку "Invalid token" (это ожидаемое поведение)
                    if (!(error instanceof RuntimeException && "Invalid token".equals(error.getMessage()))) {
                        log.error("Token validation failed: {}", error.getMessage());
                    }
                });
    }
}