package com.restaurant.gateway_service.filter;

import com.restaurant.gateway_service.service.AuthValidationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@Slf4j
@ConditionalOnProperty(name = "app.gateway.jwt-filter-enabled", havingValue = "true", matchIfMissing = false)
public class JwtAuthenticationFilter implements WebFilter {

    @Autowired
    private AuthValidationService authValidationService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // Публичные эндпоинты
        if (path.startsWith("/auth/") ||
                path.startsWith("/actuator/") ||
                path.startsWith("/fallback/") ||
                path.equals("/health") ||
                path.equals("/")) {
            log.debug("Public endpoint accessed: {}", path);
            return chain.filter(exchange);
        }

        // Проверка Authorization header
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing or invalid Authorization header for path: {}", path);
            return onError(exchange, "Missing or invalid Authorization header", HttpStatus.UNAUTHORIZED);
        }

        String token = authHeader.substring(7);

        return authValidationService.validateToken(token)
                .flatMap(authResponse -> {
                    if (!authResponse.isValid()) {
                        log.warn("Token validation failed for path {}: {}", path, authResponse.getMessage());
                        return onError(exchange, authResponse.getMessage(), HttpStatus.UNAUTHORIZED);
                    }

                    log.debug("Token validated successfully - userId: {}, role: {}, path: {}",
                            authResponse.getUserId(), authResponse.getRole(), path);

                    ServerHttpRequest mutatedRequest = request.mutate()
                            .header("X-User-Id", authResponse.getUserId())
                            .header("X-User-Email", authResponse.getEmail())
                            .header("X-User-Role", authResponse.getRole())
                            .header("X-Authenticated", "true")
                            .build();

                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                })
                .onErrorResume(error -> {
                    log.error("Error during token validation for path {}: {}", path, error.getMessage());
                    return onError(exchange, "Authentication service error", HttpStatus.SERVICE_UNAVAILABLE);
                });
    }

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

        byte[] bytes = errorBody.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return exchange.getResponse()
                .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
    }
}