package com.restaurant.gateway_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration                      /// класс содержит настройки Spring
@EnableWebFluxSecurity              /// безопасность
public class SecurityConfig {


    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)  /// CSRF - Защита от межсайтовой подделки запросов. Отключаем
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))     /// CORS (разрешает запросы с других доменов)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(      /// Пути доступные без аутентификации
                                "/auth/**",      /// Регистрация, логин
                                "/actuator/**", /// Docker/K8s проверяют жив ли сервис
                                "/fallback/**", /// Gateway
                                "/health",      /// Балансировщик нагрузки
                                "http://localhost:8081/auth/test")

                        .permitAll()
                        .anyExchange().authenticated()
                )
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)   /// Логин и пароль в заголовке. Не надо тк используем JWT
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)   /// отключаем тк REST API с JWT
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();      /// CORS настройки
        configuration.setAllowedOrigins(List.of("*"));              /// Разрешает запросы с ЛЮБЫХ доменов
        configuration.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"      /// Разрешает использовать указанные HTTP методы
        ));
        configuration.setAllowedHeaders(Arrays.asList(                  /// Разрешает использовать указанные заголовки в запросах
                "Authorization", "Content-Type", "X-User-Id", "X-User-Email", "X-User-Role",
                "X-Correlation-Id", "X-Request-Source"
        ));
        configuration.setExposedHeaders(List.of("X-User-Id", "X-Correlation-Id"));  /// Разрешает клиенту (браузеру) читать эти заголовки в ответе

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();     ///источник CORS настроек
        source.registerCorsConfiguration("/**", configuration);                         /// Применяем CORS настройки ко ВСЕМ путям
        return source;
    }
}
