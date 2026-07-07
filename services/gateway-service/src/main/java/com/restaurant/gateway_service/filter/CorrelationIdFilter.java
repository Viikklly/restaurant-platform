package com.restaurant.gateway_service.filter;

import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@Order(1)
public class CorrelationIdFilter implements WebFilter {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final String CORRELATION_ID_MDC = "correlationId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();

        // Получаем correlationId из заголовка
        String correlationId = request.getHeaders().getFirst(CORRELATION_ID_HEADER);

        // Если нет — генерируем новый
        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = UUID.randomUUID().toString();
        }

        // Кладем в MDC (для логирования)
        MDC.put(CORRELATION_ID_MDC, correlationId);

        // Добавляем заголовок в запрос для дальнейшего проброса
        ServerHttpRequest mutatedRequest = request.mutate()
                .header(CORRELATION_ID_HEADER, correlationId)
                .build();

        // Добавляем заголовок в ответ
        response.getHeaders().add(CORRELATION_ID_HEADER, correlationId);

        // Продолжаем обработку
        return chain.filter(exchange.mutate().request(mutatedRequest).build())
                .doFinally(signalType -> MDC.remove(CORRELATION_ID_MDC));
    }
}