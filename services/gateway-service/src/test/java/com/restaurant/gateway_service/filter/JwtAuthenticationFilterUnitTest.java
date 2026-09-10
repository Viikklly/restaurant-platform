/*
package com.restaurant.gateway_service.filter;

import com.restaurant.gateway_service.model.AuthResponse;
import com.restaurant.gateway_service.service.AuthValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit-тесты JwtAuthenticationFilter")
class JwtAuthenticationFilterUnitTest {

    @Mock
    private AuthValidationService authValidationService;

    @Mock
    private GatewayFilterChain chain;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    private GatewayFilter gatewayFilter;

    @BeforeEach
    void setUp() {
        gatewayFilter = filter.apply(new JwtAuthenticationFilter.Config());
    }

    private ServerWebExchange createExchange(String path, String authHeader) {
        /// Создаем билдер запроса
        MockServerHttpRequest.BaseBuilder<?> requestBuilder = MockServerHttpRequest
                .get(path)
                .header(HttpHeaders.CONTENT_TYPE, "application/json");

        if (authHeader != null) {
            requestBuilder.header(HttpHeaders.AUTHORIZATION, authHeader);
        }

        /// Строим запрос и создаем exchange
        MockServerHttpRequest request = requestBuilder.build();
        return MockServerWebExchange.from(request);
    }

    private AuthResponse createValidAuthResponse() {
        AuthResponse response = new AuthResponse();
        response.setValid(true);
        response.setUserId("1");
        response.setEmail("test@example.com");
        response.setRole("USER");
        response.setMessage("Valid token");
        return response;
    }

    private AuthResponse createInvalidAuthResponse() {
        AuthResponse response = new AuthResponse();
        response.setValid(false);
        response.setMessage("Invalid token");
        return response;
    }

    @Test
    @DisplayName("Публичный эндпоинт /auth/ - пропускает без проверки JWT")
    void shouldSkipAuthForPublicEndpoint() {
        ///запрос к публичному эндпоинту /auth/login без токена
        ServerWebExchange exchange = createExchange("/auth/login", null);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        ///фильтр обрабатывает запрос
        StepVerifier.create(gatewayFilter.filter(exchange, chain))
                .verifyComplete();

        /// проверка JWT не вызывается, запрос проходит дальше
        verify(authValidationService, never()).validateToken(anyString());
        verify(chain).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("Публичный эндпоинт /actuator/ - пропускает без проверки JWT")
    void shouldSkipAuthForActuatorEndpoint() {
        /// запрос к публичному эндпоинту /actuator/health без токена
        ServerWebExchange exchange = createExchange("/actuator/health", null);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        /// фильтр обрабатывает запрос
        StepVerifier.create(gatewayFilter.filter(exchange, chain))
                .verifyComplete();

        /// проверка JWT не вызывается, запрос проходит дальше
        verify(authValidationService, never()).validateToken(anyString());
        verify(chain).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("Публичный эндпоинт /fallback/ - пропускает без проверки JWT")
    void shouldSkipAuthForFallbackEndpoint() {
        /// запрос к публичному эндпоинту /fallback/orders без токена
        ServerWebExchange exchange = createExchange("/fallback/orders", null);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        /// фильтр обрабатывает запрос
        StepVerifier.create(gatewayFilter.filter(exchange, chain))
                .verifyComplete();

        ///проверка JWT не вызывается, запрос проходит дальше
        verify(authValidationService, never()).validateToken(anyString());
        verify(chain).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("Защищенный эндпоинт без Authorization заголовка - возвращает 401")
    void shouldReturnUnauthorizedWhenNoAuthHeader() {
        ///запрос к защищенному эндпоинту /api/orders без заголовка Authorization
        ServerWebExchange exchange = createExchange("/api/orders", null);

        ///фильтр обрабатывает запрос
        StepVerifier.create(gatewayFilter.filter(exchange, chain))
                .verifyComplete();

        /// возвращается статус 401 Unauthorized, проверка JWT не вызывается
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(authValidationService, never()).validateToken(anyString());
        verify(chain, never()).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("Защищенный эндпоинт с неверным форматом Authorization - возвращает 401")
    void shouldReturnUnauthorizedWhenInvalidAuthFormat() {
        ///запрос к защищенному эндпоинту с заголовком Authorization неверного формата
        ServerWebExchange exchange = createExchange("/api/orders", "Invalid token");

        ///фильтр обрабатывает запрос
        StepVerifier.create(gatewayFilter.filter(exchange, chain))
                .verifyComplete();

        ///возвращается статус 401 Unauthorized, проверка JWT не вызывается
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(authValidationService, never()).validateToken(anyString());
        verify(chain, never()).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("Защищенный эндпоинт с валидным токеном - пропускает и добавляет заголовки")
    void shouldPassWithValidTokenAndAddHeaders() {
        ///запрос к защищенному эндпоинту с валидным JWT токеном
        String token = "valid.jwt.token";
        ServerWebExchange exchange = createExchange("/api/orders", "Bearer " + token);
        AuthResponse authResponse = createValidAuthResponse();

        when(authValidationService.validateToken(token))
                .thenReturn(Mono.just(authResponse));
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        /// фильтр обрабатывает запрос
        StepVerifier.create(gatewayFilter.filter(exchange, chain))
                .verifyComplete();

        ///токен валидируется, запрос проходит дальше
        verify(authValidationService).validateToken(token);
        verify(chain).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("Защищенный эндпоинт с невалидным токеном - возвращает 401")
    void shouldReturnUnauthorizedWhenInvalidToken() {
        /// запрос к защищенному эндпоинту с невалидным JWT токеном
        String token = "invalid.jwt.token";
        ServerWebExchange exchange = createExchange("/api/orders", "Bearer " + token);
        AuthResponse authResponse = createInvalidAuthResponse();

        when(authValidationService.validateToken(token))
                .thenReturn(Mono.just(authResponse));

        /// фильтр обрабатывает запрос
        StepVerifier.create(gatewayFilter.filter(exchange, chain))
                .verifyComplete();

        /// возвращается статус 401 Unauthorized, запрос не проходит дальше
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(authValidationService).validateToken(token);
        verify(chain, never()).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("Ошибка при валидации токена - возвращает 503")
    void shouldReturnServiceUnavailableWhenValidationFails() {
        ///запрос к защищенному эндпоинту, сервис валидации недоступен
        String token = "some.token";
        ServerWebExchange exchange = createExchange("/api/orders", "Bearer " + token);

        when(authValidationService.validateToken(token))
                .thenReturn(Mono.error(new RuntimeException("Auth service unavailable")));

        /// фильтр обрабатывает запрос
        StepVerifier.create(gatewayFilter.filter(exchange, chain))
                .verifyComplete();

        /// возвращается статус 503 Service Unavailable
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        verify(chain, never()).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("Множественные заголовки Authorization - использует первый")
    void shouldUseFirstAuthHeaderWhenMultiple() {
        /// запрос с несколькими заголовками Authorization
        String token = "valid.jwt.token";
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token, "Bearer another.token")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        AuthResponse authResponse = createValidAuthResponse();

        when(authValidationService.validateToken(token))
                .thenReturn(Mono.just(authResponse));
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        /// фильтр обрабатывает запрос
        StepVerifier.create(gatewayFilter.filter(exchange, chain))
                .verifyComplete();

        /// используется первый заголовок для валидации
        verify(authValidationService).validateToken(token);
    }
}*/
