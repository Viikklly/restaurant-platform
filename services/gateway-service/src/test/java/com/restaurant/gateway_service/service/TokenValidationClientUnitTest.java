package com.restaurant.gateway_service.service;

import com.restaurant.gateway_service.model.AuthRequest;
import com.restaurant.gateway_service.model.AuthResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.ConnectException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit-тесты TokenValidationClient")
class TokenValidationClientUnitTest {

    @Mock
    private WebClient authServiceWebClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private TokenValidationClient tokenValidationClient;

    private final String TEST_TOKEN = "valid.jwt.token";

    @BeforeEach
    void setUp() {
        tokenValidationClient = new TokenValidationClient(authServiceWebClient);
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

    private WebClientResponseException createWebClientException(HttpStatus status, String message) {
        return WebClientResponseException.create(
                status.value(),
                message,
                null,
                message.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                null
        );
    }

    private void setupWebClientMock() {
        when(authServiceWebClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any(AuthRequest.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
    }

    @Test
    @DisplayName("validateToken() - успешная валидация")
    void validateToken_ShouldReturnValidResponse() {
        AuthResponse expectedResponse = createValidAuthResponse();

        setupWebClientMock();
        when(responseSpec.bodyToMono(AuthResponse.class)).thenReturn(Mono.just(expectedResponse));

        Mono<AuthResponse> result = tokenValidationClient.validateToken(TEST_TOKEN);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.isValid()).isTrue();
                    assertThat(response.getUserId()).isEqualTo("1");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("validateToken() - 401 → Invalid token")
    void validateToken_ShouldReturnInvalidTokenError() {
        WebClientResponseException exception = createWebClientException(
                HttpStatus.UNAUTHORIZED, "Unauthorized");

        setupWebClientMock();
        when(responseSpec.bodyToMono(AuthResponse.class))
                .thenReturn(Mono.error(exception));

        Mono<AuthResponse> result = tokenValidationClient.validateToken(TEST_TOKEN);

        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();
    }



    @Test
    @DisplayName("validateToken() - 500 не retry")
    void validateToken_ShouldNotRetry_WhenInternalServerError() {
        WebClientResponseException internalError = createWebClientException(
                HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error");

        setupWebClientMock();
        when(responseSpec.bodyToMono(AuthResponse.class))
                .thenReturn(Mono.error(internalError));

        Mono<AuthResponse> result = tokenValidationClient.validateToken(TEST_TOKEN);

        StepVerifier.create(result)
                .expectError(WebClientResponseException.class)
                .verify();
    }
}