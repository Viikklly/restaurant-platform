package com.restaurant.gateway_service.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.UUID;

@WebFluxTest
@Import(CorrelationIdFilter.class)
@TestPropertySource(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "spring.security.enabled=false",
        "app.gateway.jwt-filter-enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration,org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration"
})
@DisplayName("Интеграционные тесты CorrelationIdFilter")
class CorrelationIdFilterTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    @DisplayName("Должен сгенерировать correlationId, если заголовок отсутствует")
    void shouldGenerateCorrelationId_WhenHeaderMissing() {
        webTestClient.get()
                .uri("/test")
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().exists("X-Correlation-Id");
    }

    @Test
    @DisplayName("Должен сохранить correlationId, если заголовок передан")
    void shouldPreserveCorrelationId_WhenHeaderPresent() {
        String correlationId = UUID.randomUUID().toString();

        webTestClient.get()
                .uri("/test")
                .header("X-Correlation-Id", correlationId)
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().valueEquals("X-Correlation-Id", correlationId);
    }
}