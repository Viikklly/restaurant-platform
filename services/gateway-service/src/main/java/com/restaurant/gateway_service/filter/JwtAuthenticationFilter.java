package com.restaurant.gateway_service.filter;


import com.restaurant.gateway_service.service.AuthValidationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@Slf4j
@ConditionalOnProperty(name = "app.gateway.jwt-filter-enabled", havingValue = "true", matchIfMissing = false)
public class JwtAuthenticationFilter extends AbstractGatewayFilterFactory<JwtAuthenticationFilter.Config> {

    @Autowired
    private AuthValidationService authValidationService;

    public JwtAuthenticationFilter() {
        super(Config.class);
    }


    /**
     *  JWT валидация в Gateway.
     *  Gateway применяет этот код к каждому проходящему запросу.
     *  Этот фильтр - проверяет пропуск у каждого посетителя и записывает его имя перед тем, как пустить внутрь.
     */
    @Override
    public GatewayFilter apply(Config config) {
        ///  exchange - Объект запроса и ответа (ServerWebExchange)
        /// chain	Цепочка фильтров (следующий фильтр или целевой сервис)
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();  /// exchange.getRequest() - получаем объект HTTP запроса
            String path = request.getURI().getPath();           /// request.getURI().getPath() - извлекаем путь URL

            /// Если путь публичный (не требует токена) - пропускаем запрос без проверки
            if (isPublicEndpoint(path)) {
                log.debug("Public endpoint accessed: {}", path);
                return chain.filter(exchange);
            }

            /// Извлекаем все заголовки с именем Authorization
            List<String> authHeaders = request.getHeaders().get(HttpHeaders.AUTHORIZATION);

            /// Если заголовка нет - возвращаем ошибку 401
            if (authHeaders == null || authHeaders.isEmpty()) {
                log.warn("Missing Authorization header for path: {}", path);
                return onError(exchange, "Missing Authorization header", HttpStatus.UNAUTHORIZED);
            }

            /// Проверяем, что заголовок начинается с Bearer
            String authHeader = authHeaders.get(0);
            if (!authHeader.startsWith("Bearer ")) {
                log.warn("Invalid Authorization header format for path: {}", path);
                return onError(exchange, "Invalid Authorization header format. Expected: Bearer <token>",
                        HttpStatus.UNAUTHORIZED);
            }

            /// Отрезаем первые 7 символов ("Bearer "), оставляя только сам токен
            String token = authHeader.substring(7);

            /// Валидируем токен через auth-service
            return authValidationService.validateToken(token)
                    .flatMap(authResponse -> {
                        if (!authResponse.isValid()) {                      /// true - токен хороший, false - токен плохой
                            log.warn("Token validation failed for path {}: {}", path, authResponse.getMessage());
                            return onError(exchange, authResponse.getMessage(), HttpStatus.UNAUTHORIZED);
                        }

                        /// Лог об успешной валидации
                        log.debug("Token validated successfully - userId: {}, role: {}, path: {}",
                                authResponse.getUserId(), authResponse.getRole(), path);

                        /// Добавляем заголовки с информацией о пользователе.
                        ///  Gateway проверяет токен, видит userId=123
                        ///  Gateway перезаписывает: X-User-Id: 123
                        ServerHttpRequest mutatedRequest = request.mutate()
                                .header("X-User-Id", authResponse.getUserId())
                                .header("X-User-Email", authResponse.getEmail())
                                .header("X-User-Role", authResponse.getRole())
                                .header("X-Authenticated", "true")
                                .build();

                        /// Отправляет переписанный запрос к следующему фильтру или к целевому сервису
                        return chain.filter(exchange.mutate().request(mutatedRequest).build());
                    })
                    .onErrorResume(error -> {
                        log.error("Error during token validation for path {}: {}", path, error.getMessage());
                        return onError(exchange, "Authentication service error: " + error.getMessage(),
                                HttpStatus.SERVICE_UNAVAILABLE);
                    });
        };
    }

    /**
     *  Это фильтр, который решает:
     *  Нужно ли проверять JWT токен для этого запроса?
     *  Если нужен ответ пользователю ДО того, как у него есть токен: сделать публичным
     *  Если запрос модифицирует данные или требует авторизации: требовать токен
     */
    private boolean isPublicEndpoint(String path) {
        return path.startsWith("/auth/") ||
                path.startsWith("/actuator/") ||
                path.startsWith("/fallback/") ||
                path.equals("/health") ||
                path.equals("/");
    }


    /**
     * Формирует и отправляет JSON ответ об ошибке
     */
    private Mono<Void> onError(ServerWebExchange exchange, String message, HttpStatus status) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().add("Content-Type", "application/json");

        String errorBody = String.format("""
                        {
                            "status": %d,
                            "error": "%s",
                            "message": "%s",
                            "timestamp": %d
                        }
                        """,
                status.value(),
                status.getReasonPhrase(),
                message,
                System.currentTimeMillis());

        /// Превращает строку JSON в массив байтов
        byte[] bytes = errorBody.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return exchange.getResponse() /// Получаем объект HTTP ответа
                .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
    }



    /**
     * Требование API Spring Cloud Gateway
     * Родительский класс AbstractGatewayFilterFactory требует его
     */
    public static class Config {
        // Configuration properties if needed
    }
}
