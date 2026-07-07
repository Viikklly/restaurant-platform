package com.restaurant.payment_service.filter;


import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(1)   /// Устанавливает порядок выполнения компонента
public class CorrelationIdFilter implements Filter {
    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final String CORRELATION_ID_MDC = "correlationId";


    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;


        String correlationId = httpRequest.getHeader(CORRELATION_ID_HEADER);  /// Извлекаем Correlation ID из заголовка


        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = UUID.randomUUID().toString();           /// Генерация ID, если его нет
        }

        try {
            MDC.put(CORRELATION_ID_MDC, correlationId);                         /// Сохранение в MDC (для логирования)
            httpResponse.setHeader(CORRELATION_ID_HEADER, correlationId);       /// Добавляем в ответ
            chain.doFilter(request, response);
        } finally {
            MDC.remove(CORRELATION_ID_MDC);                                     /// Если не очистить, то следующий запрос получит чужой Correlation ID
        }
    }
}
