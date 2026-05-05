package com.restaurant.auth_service.service;

import com.restaurant.auth_service.dto.LoginRequestDTO;
import com.restaurant.auth_service.dto.LoginResponseDTO;
import com.restaurant.auth_service.dto.RegisterRequestDTO;
import com.restaurant.auth_service.entity.Role;
import com.restaurant.auth_service.entity.User;
import com.restaurant.auth_service.repository.UserRepository;
import com.restaurant.auth_service.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;                /// БД
    private final PasswordEncoder passwordEncoder;              /// Spring Security	Хеширование паролей
    private final JwtService jwtService;                        /// Генерация и валидация JWT
    private final AuthenticationManager authenticationManager;  /// Проверка логина/пароля

    /**
     * Регистрация нового пользователя
     */
    @Transactional
    public LoginResponseDTO register(RegisterRequestDTO request) {
        log.info("Registering new user with email: {}", request.getEmail());

        /// Не занят ли email
        if (userRepository.existsByEmail(request.getEmail())) { /// Проверяем, есть ли в БД пользователь с таким email
            log.warn("Email already exists: {}", request.getEmail());
            throw new RuntimeException("Email already registered");
        }

        /// Не занят ли username
        if (userRepository.existsByUsername(request.getUsername())) {
            log.warn("Username already exists: {}", request.getUsername());
            throw new RuntimeException("Username already taken");
        }

        /// Создание пользователя
        User user = User.builder()
                .email(request.getEmail())
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword())) /// passwordEncoder.encode() — хэшируем пароль и только потом сохраняем
                .role(Role.ROLE_USER)
                .build();

        /// Сохраняем в БД
        User savedUser = userRepository.save(user);
        log.info("User registered successfully with id: {}", savedUser.getId());

        /// Генерируем токены
        String accessToken = jwtService.generateToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        return new LoginResponseDTO(accessToken, refreshToken);
    }

    /**
     * Логин пользователя
     */
    public LoginResponseDTO login(LoginRequestDTO request) {
        log.info("Login attempt for email: {}", request.getEmail());

        /// Аутентифицируем пользователя
        Authentication authentication = authenticationManager.authenticate( /// authenticationManager.authenticate()	Запускает процесс проверки
                new UsernamePasswordAuthenticationToken(  /// UsernamePasswordAuthenticationToken	Обёртка для email и пароля
                        request.getEmail(),
                        request.getPassword()
                )
        );

        /// Получаем пользователя
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();

        /// Генерируем токены
        String accessToken = jwtService.generateToken(userDetails);
        String refreshToken = jwtService.generateRefreshToken(userDetails);

        log.info("User logged in successfully: {}", request.getEmail());

        return new LoginResponseDTO(accessToken, refreshToken);
    }

    /**
     * Обновление access-токена по refresh-токену
     */
    public LoginResponseDTO refreshToken(String refreshToken) {
        log.info("Refreshing token");

        /// Извлекаем email из refresh-токена
        String userEmail = jwtService.extractUsername(refreshToken);

        if (userEmail == null) {
            throw new RuntimeException("Invalid refresh token");
        }

        /// Загружаем пользователя
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        /// Проверяем валидность refresh-токена
        if (!jwtService.isTokenValid(refreshToken, user)) {
            throw new RuntimeException("Invalid refresh token");
        }

        /// Генерируем новый access-токен
        String newAccessToken = jwtService.generateToken(user);

        log.info("Token refreshed for user: {}", userEmail);

        return new LoginResponseDTO(newAccessToken, refreshToken);
    }
}
