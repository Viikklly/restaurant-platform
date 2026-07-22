package com.restaurant.gateway_service.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit-тесты CorrelationIdFilter")
class CorrelationIdFilterUnitTest {

    private CorrelationIdFilter filter;

    @Mock
    private WebFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new CorrelationIdFilter();
        MDC.clear();
    }

    @Test
    @DisplayName("Использует существующий Correlation ID из заголовка")
    void shouldUseExistingCorrelationId() {
        /// запрос с заголовком X-Correlation-Id
        String correlationId = "existing-1234-5678";
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/test")
                        .header("X-Correlation-Id", correlationId)
                        .build()
        );

        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        ///фильтр обрабатывает запрос
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        ///Correlation ID сохраняется в запросе, ответе и MDC
        ServerWebExchange mutatedExchange = captureMutatedExchange();
        assertThat(mutatedExchange.getRequest().getHeaders().getFirst("X-Correlation-Id"))
                .isEqualTo(correlationId);
        assertThat(mutatedExchange.getResponse().getHeaders().getFirst("X-Correlation-Id"))
                .isEqualTo(correlationId);
        assertThat(MDC.get("correlationId")).isNull(); // MDC очищен после завершения
    }

    @Test
    @DisplayName("Генерирует новый Correlation ID, если заголовок отсутствует")
    void shouldGenerateNewCorrelationId_WhenHeaderMissing() {
        /// запрос без заголовка X-Correlation-Id
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/test").build()
        );

        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        /// фильтр обрабатывает запрос
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        /// генерируется новый Correlation ID
        ServerWebExchange mutatedExchange = captureMutatedExchange();
        String generatedCorrelationId = mutatedExchange.getRequest()
                .getHeaders().getFirst("X-Correlation-Id");

        assertThat(generatedCorrelationId).isNotNull();
        assertThat(UUID.fromString(generatedCorrelationId)).isNotNull(); // Валидный UUID
        assertThat(mutatedExchange.getResponse().getHeaders().getFirst("X-Correlation-Id"))
                .isEqualTo(generatedCorrelationId);
    }

    @Test
    @DisplayName("Генерирует новый Correlation ID при пустом заголовке")
    void shouldGenerateNewCorrelationId_WhenHeaderEmpty() {
        /// запрос с пустым заголовком X-Correlation-Id
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/test")
                        .header("X-Correlation-Id", "")
                        .build()
        );

        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        /// фильтр обрабатывает запрос
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        ///генерируется новый Correlation ID
        ServerWebExchange mutatedExchange = captureMutatedExchange();
        String generatedCorrelationId = mutatedExchange.getRequest()
                .getHeaders().getFirst("X-Correlation-Id");

        assertThat(generatedCorrelationId).isNotNull();
        assertThat(generatedCorrelationId).isNotEmpty();
        assertThat(UUID.fromString(generatedCorrelationId)).isNotNull();
    }

    @Test
    @DisplayName("Очищает MDC после обработки запроса")
    void shouldClearMDCAfterProcessing() {
        ///запрос с заголовком X-Correlation-Id
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/test")
                        .header("X-Correlation-Id", "test-id")
                        .build()
        );

        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        /// фильтр обрабатывает запрос
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        /// MDC очищен после завершения обработки
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    @DisplayName("Устанавливает Correlation ID в заголовок ответа")
    void shouldSetCorrelationIdInResponseHeader() {
        /// запрос с заголовком X-Correlation-Id
        String correlationId = "response-header-test";
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/test")
                        .header("X-Correlation-Id", correlationId)
                        .build()
        );

        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        /// фильтр обрабатывает запрос
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        /// Correlation ID добавлен в заголовок ответа
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Correlation-Id"))
                .isEqualTo(correlationId);
    }

    @Test
    @DisplayName("Сохраняет Correlation ID в MDC во время обработки")
    void shouldPutCorrelationIdInMDCDuringProcessing() {
        /// запрос с заголовком X-Correlation-Id
        String correlationId = "mdc-test-id";
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/test")
                        .header("X-Correlation-Id", correlationId)
                        .build()
        );

        // Перехватываем момент, когда MDC установлен
        when(chain.filter(any(ServerWebExchange.class))).thenAnswer(invocation -> {
            assertThat(MDC.get("correlationId")).isEqualTo(correlationId);
            return Mono.empty();
        });

        /// фильтр обрабатывает запрос
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        /// MDC очищен после завершения
        assertThat(MDC.get("correlationId")).isNull();
    }

    private ServerWebExchange captureMutatedExchange() {
        var captor = org.mockito.ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        return captor.getValue();
    }
}