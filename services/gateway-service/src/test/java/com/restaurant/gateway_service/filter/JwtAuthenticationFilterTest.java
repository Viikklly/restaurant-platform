/*
package com.restaurant.gateway_service.filter;

import com.restaurant.gateway_service.model.AuthResponse;
import com.restaurant.gateway_service.service.AuthValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "app.gateway.jwt-filter-enabled=true",
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "spring.security.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration,org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration",
        // Отключаем discovery для тестов
        "spring.cloud.discovery.enabled=false"
})
@DisplayName("Интеграционные тесты JwtAuthenticationFilter")
class JwtAuthenticationFilterTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @MockBean
    private AuthValidationService authValidationService;

    private static final String VALID_TOKEN = "valid.jwt.token";
    private static final String INVALID_TOKEN = "invalid.jwt.token";

    @BeforeEach
    void setUp() {
        AuthResponse validResponse = new AuthResponse();
        validResponse.setValid(true);
        validResponse.setUserId("123");
        validResponse.setEmail("user@example.com");
        validResponse.setRole("USER");
        validResponse.setMessage("Token valid");

        AuthResponse invalidResponse = new AuthResponse();
        invalidResponse.setValid(false);
        invalidResponse.setMessage("Invalid token");

        when(authValidationService.validateToken(VALID_TOKEN))
                .thenReturn(Mono.just(validResponse));

        when(authValidationService.validateToken(INVALID_TOKEN))
                .thenReturn(Mono.just(invalidResponse));
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    @Test
    @DisplayName("Публичный эндпоинт /auth/** - доступен без токена")
    void publicEndpoint_ShouldBeAccessible() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/auth/login",
                String.class
        );
        // Может вернуть 404, но не 401
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Защищённый эндпоинт без токена - возвращает 401")
    void protectedEndpoint_WithoutToken_ShouldReturn401() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/api/orders",
                String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Защищённый эндпоинт с невалидным токеном - возвращает 401")
    void protectedEndpoint_WithInvalidToken_ShouldReturn401() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + INVALID_TOKEN);

        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/api/orders",
                String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Защищённый эндпоинт с валидным токеном - пропускает")
    void protectedEndpoint_WithValidToken_ShouldPass() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN);

        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/api/orders",
                String.class
        );
        // Может вернуть 404, но не 401
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Неверный формат Authorization заголовка - возвращает 401")
    void protectedEndpoint_WithInvalidAuthHeader_ShouldReturn401() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Basic token");

        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/api/orders",
                String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}*/
