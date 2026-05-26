package com.restaurant.gateway_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Класс содержит настройки Spring
 */
@Configuration                      /// класс содержит настройки Spring
@EnableWebFluxSecurity              /// безопасность
public class SecurityConfig {


    /**
     * Настраивает цепочку фильтров безопасности (Spring Security WebFlux)
     * Проверка JWT не включена, может быть добавлена отдельным фильтром JwtAuthenticationFilter
     * при включении свойства app.gateway.jwt-filter-enabled
     * ТАК КАК ПРОЕКТ УЧЕБНЫЙ И ДЛЯ УПРОЩЕНИЯ РАЗРАБОТКИ
     * @param http
     * @return SecurityWebFilterChain
     */
    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)  /// CSRF - Защита от межсайтовой подделки запросов. Отключаем
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))     /// CORS (разрешает запросы с других доменов)
                // Проверку JWT на маршрутах можно включить фильтром JwtAuthenticationFilter и
                // app.gateway.jwt-filter-enabled=true (см. gateway-service.yml в config-repo).
                .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)   /// Логин и пароль в заголовке. Не надо тк используем JWT
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)   /// отключаем тк REST API с JWT
                .build();
    }

    /**
     * Создаёт и настраивает источник CORS-конфигурации
     *
     *  Разрешённые HTTP методы: GET, POST, PUT, DELETE, PATCH, OPTIONS.<br>
     *  Разрешённые заголовки запроса: Authorization, Content-Type, X-User-Id, X-User-Email,
     *  X-User-Role, X-Correlation-Id, X-Request-Source.<br>
     *  Клиенту (браузеру) доступны для чтения заголовки ответа: X-User-Id, X-Correlation-Id.
     *
     *  Конфигурация применяется ко всем путям (/**).
     * @return CorsConfigurationSource (CORS-настройки, используемые в цепочке безопасности)
     */
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
