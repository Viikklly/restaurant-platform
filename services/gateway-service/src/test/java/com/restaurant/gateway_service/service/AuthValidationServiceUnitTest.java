package com.restaurant.gateway_service.service;

import com.restaurant.gateway_service.model.AuthResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit-тесты AuthValidationService")
class AuthValidationServiceUnitTest {

    @Mock
    private TokenValidationClient tokenValidationClient;

    @InjectMocks
    private AuthValidationService authValidationService;

    private final String TEST_TOKEN = "valid.jwt.token";

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
    @DisplayName("validateToken() - успешная валидация токена")
    void validateToken_ShouldReturnValidResponse() {
        ///клиент валидации возвращает успешный ответ для токена
        AuthResponse expectedResponse = createValidAuthResponse();
        when(tokenValidationClient.validateToken(TEST_TOKEN))
                .thenReturn(Mono.just(expectedResponse));

        /// вызывается метод validateToken с валидным токеном
        Mono<AuthResponse> result = authValidationService.validateToken(TEST_TOKEN);

        /// возвращается успешный ответ с данными пользователя
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.isValid()).isTrue();
                    assertThat(response.getUserId()).isEqualTo("1");
                    assertThat(response.getEmail()).isEqualTo("test@example.com");
                    assertThat(response.getRole()).isEqualTo("USER");
                })
                .verifyComplete();

        verify(tokenValidationClient).validateToken(TEST_TOKEN);
    }

    @Test
    @DisplayName("validateToken() - невалидный токен")
    void validateToken_ShouldReturnInvalidResponse() {
        ///клиент валидации возвращает отрицательный ответ для токена
        AuthResponse expectedResponse = createInvalidAuthResponse();
        when(tokenValidationClient.validateToken(TEST_TOKEN))
                .thenReturn(Mono.just(expectedResponse));

        /// вызывается метод validateToken с невалидным токеном
        Mono<AuthResponse> result = authValidationService.validateToken(TEST_TOKEN);

        /// возвращается ответ с флагом valid = false
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.isValid()).isFalse();
                    assertThat(response.getMessage()).isEqualTo("Invalid token");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("validateToken() - пробрасывает ошибку, если клиент вернул ошибку")
    void validateToken_ShouldPropagateError_WhenClientFails() {
        /// клиент валидации возвращает ошибку
        when(tokenValidationClient.validateToken(TEST_TOKEN))
                .thenReturn(Mono.error(new RuntimeException("Auth service unavailable")));

        ///вызывается метод validateToken
        Mono<AuthResponse> result = authValidationService.validateToken(TEST_TOKEN);

        /// ошибка пробрасывается дальше (Circuit Breaker не активен в unit-тесте)
        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();
    }



    @Test
    @DisplayName("fallbackValidateToken() - возвращает fallback ответ")
    void fallbackValidateToken_ShouldReturnFallbackResponse() throws Exception {
        ///получаем приватный метод fallbackValidateToken через рефлексию
        Method fallbackMethod = AuthValidationService.class.getDeclaredMethod(
                "fallbackValidateToken", String.class, Throwable.class);
        fallbackMethod.setAccessible(true);

        /// вызываем fallback метод
        Object result = fallbackMethod.invoke(
                authValidationService,
                TEST_TOKEN,
                new RuntimeException("Auth service unavailable")
        );

        /// возвращается Mono с AuthResponse
        @SuppressWarnings("unchecked")
        Mono<AuthResponse> resultMono = (Mono<AuthResponse>) result;

        StepVerifier.create(resultMono)
                .assertNext(response -> {
                    assertThat(response.isValid()).isFalse();
                    assertThat(response.getMessage())
                            .isEqualTo("Auth service temporarily unavailable. Please try again later.");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("fallbackValidateToken() - работает с любым Throwable")
    void fallbackValidateToken_ShouldWorkWithAnyThrowable() throws Exception {
        ///получаем приватный метод fallbackValidateToken через рефлексию
        Method fallbackMethod = AuthValidationService.class.getDeclaredMethod(
                "fallbackValidateToken", String.class, Throwable.class);
        fallbackMethod.setAccessible(true);

        /// вызываем fallback метод с разными исключениями
        Object result1 = fallbackMethod.invoke(
                authValidationService,
                TEST_TOKEN,
                new RuntimeException("Any error")
        );

        Object result2 = fallbackMethod.invoke(
                authValidationService,
                TEST_TOKEN,
                new IllegalStateException("State error")
        );

        /// оба возвращают fallback ответ
        @SuppressWarnings("unchecked")
        Mono<AuthResponse> resultMono1 = (Mono<AuthResponse>) result1;

        StepVerifier.create(resultMono1)
                .assertNext(response -> {
                    assertThat(response.isValid()).isFalse();
                    assertThat(response.getMessage())
                            .isEqualTo("Auth service temporarily unavailable. Please try again later.");
                })
                .verifyComplete();

        @SuppressWarnings("unchecked")
        Mono<AuthResponse> resultMono2 = (Mono<AuthResponse>) result2;

        StepVerifier.create(resultMono2)
                .assertNext(response -> {
                    assertThat(response.isValid()).isFalse();
                    assertThat(response.getMessage())
                            .isEqualTo("Auth service temporarily unavailable. Please try again later.");
                })
                .verifyComplete();
    }
}