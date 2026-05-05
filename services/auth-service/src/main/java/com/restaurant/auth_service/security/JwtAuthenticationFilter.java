package com.restaurant.auth_service.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;


@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    /// OncePerRequestFilter — гарантирует, что фильтр выполнится один раз за запрос
    /// один запрос -> один вызов фильтра

    private final JwtService jwtService;                /// Генерация и валидация токенов
    private final UserDetailsService userDetailsService;/// Загрузка пользователя из БД по email

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,    /// Входящий запрос
            @NonNull HttpServletResponse response,  /// Исходящий ответ (можем менять)
            @NonNull FilterChain filterChain       /// Цепочка фильтров (для передачи дальше)
    ) throws ServletException, IOException {

        /// Проверяем, не обращение ли к /auth/**
        /// /auth/register и /auth/login доступны без JWT
        final String requestPath = request.getServletPath();
        if (requestPath.startsWith("/auth/")) {
            filterChain.doFilter(request, response);
            return;
        }

        /// Извлекаем JWT из заголовка Authorization
        /// GET /api/orders
        /// Authorization: Bearer ---.---.---
        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.debug("No JWT token found in request headers");
            filterChain.doFilter(request, response);
            return;
        }

        /// Извлекаем сам токен (без Bearer)
        final String jwt = authHeader.substring(7);
        log.debug("Extracted JWT: {}...", jwt.substring(0, Math.min(jwt.length(), 20)));
        try {
            /// Извлекаем email (username) из токена
            final String userEmail = jwtService.extractUsername(jwt);
            log.debug("Extracted username from JWT: {}", userEmail);

            /// Проверяем, нет ли уже аутентификации в контексте
            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                /// Загружаем пользователя из БД
                /// UserDetailsService ищет пользователя в БД и возвращает объект UserDetails (сущность User)
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);

                /// Проверяем валидность токена
                /// isTokenValid()
                /// Email в токене совпадает с email пользователя из БД
                /// Токен не просрочен (exp > текущее время)
                /// Подпись валидна
                if (jwtService.isTokenValid(jwt, userDetails)) {
                    log.debug("JWT token is valid for user: {}", userEmail);

                    /// Создаём объект аутентификации
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,  /// credentials (уже проверили)
                            userDetails.getAuthorities() /// права доступа (роли)
                    );

                    /// Устанавливаем аутентификацию в контекст Spring Security
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                } else {
                    log.warn("Invalid JWT token for user: {}", userEmail);
                }
            }
            /// Иначе ошибка
        } catch (Exception e) {
            log.error("Cannot authenticate user via JWT: {}", e.getMessage());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired token");
            return;
        }

        /// Пропускаем запрос дальше по цепочке фильтров
        /// Всегда
        /// SPRING SECURITY сам решит, что нужно вернуть 403/401.
        filterChain.doFilter(request, response);
    }
}
