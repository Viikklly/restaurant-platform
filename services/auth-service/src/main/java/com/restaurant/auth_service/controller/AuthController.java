package com.restaurant.auth_service.controller;

import com.restaurant.auth_service.dto.AuthResponse;
import com.restaurant.auth_service.dto.AuthRequest;
import com.restaurant.auth_service.dto.LoginRequestDTO;
import com.restaurant.auth_service.dto.LoginResponseDTO;
import com.restaurant.auth_service.dto.RegisterRequestDTO;
import com.restaurant.auth_service.security.JwtService;
import com.restaurant.auth_service.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    private final JwtService jwtService;

    /**
     * Регистрация нового пользователя
     * POST /auth/register
     */
    @PostMapping("/register")
    /**
     * @Valid Проверяет DTO на валидность (email формат, размер пароля)
     * @RequestBody Берёт JSON из тела запроса и превращает в объект RegisterRequestDTO
     */
    public ResponseEntity<LoginResponseDTO> register(@Valid @RequestBody RegisterRequestDTO request) {
        log.info("REST request to register user: {}", request.getEmail());
        LoginResponseDTO response = authService.register(request);
        return ResponseEntity.ok(response); /// = HTTP 200 OK
    }

    /**
     * Логин пользователя
     * POST /auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        log.info("REST request to login user: {}", request.getEmail());
        LoginResponseDTO response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Обновление access-токена
     * POST /auth/refresh
     */
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponseDTO> refresh(@RequestBody Map<String, String> request) {
        String refreshToken = request.get("refreshToken");
        log.info("REST request to refresh token");
        LoginResponseDTO response = authService.refreshToken(refreshToken);
        return ResponseEntity.ok(response);
    }

    /**
     * Тестовый эндпоинт
     * GET /auth/test
     * Проверяет, жив ли сервис. Не требует токена (так как /auth/** открыт в SecurityConfig)
     */
    @GetMapping("/test")
    public ResponseEntity<String> test() {
        return ResponseEntity.ok("Auth service is working!");
    }


    @PostMapping("/validate")
    public ResponseEntity<AuthResponse> validateToken(@RequestBody AuthRequest request) {
        log.debug("Validating token");

        try {
            /// строка с токеном из объекта request
            String token = request.getToken();

            ///  проверяем валидность токена
            boolean isValid = jwtService.isTokenValid(token);

            if (!isValid) {
                log.warn("Invalid token provided");
                return ResponseEntity
                        .status(HttpStatus.UNAUTHORIZED)
                        .body(new AuthResponse(false, null, null, null, "Token is invalid or expired"));
            }

            /// Извлекаем данные из токена
            String userId = jwtService.extractUserId(token);
            String email = jwtService.extractEmail(token);
            String role = jwtService.extractRole(token);

            log.debug("Token validated - userId: {}, email: {}, role: {}", userId, email, role);

            /// Возвращает успешный ответ с HTTP статусом 200 (OK)
            return ResponseEntity.ok(new AuthResponse(true, userId, email, role, "Token is valid"));

        } catch (Exception e) {
            log.error("Token validation error: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthResponse(false, null, null, null, "Token validation failed: " + e.getMessage()));
        }
    }
}
