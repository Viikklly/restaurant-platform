package com.restaurant.auth_service.config;


import com.restaurant.auth_service.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;


/*
SecurityConfig — это конфигурация Spring Security, которая определяет:
Какие URL доступны без токена
/auth/register, /auth/login — открыты для всех
Какие URL требуют аутентификации
Всё остальное — нужно быть залогиненным
Как проверять пароль
Как загружать пользователя из БД
Подключаем наш CustomUserDetailsService
Как обрабатывать ошибки доступа
Возвращать 401 вместо страницы логина
*/

@Configuration
@EnableWebSecurity      /// Включает Spring Security для веб-приложения
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;


    /// Это цепочка фильтров, через которые проходит каждый HTTP-запрос
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {


        System.out.println("=== SECURITY CONFIG IS BEING LOADED ===");

        http
                /// ОТКЛЮЧАЕМ так как
                /// CSRF защита нужна для браузерных приложений с cookies
                /// А приложение REST API с JWT, токен не хранится в cookies автоматически
                .csrf(AbstractHttpConfigurer::disable)
                /// Настраиваем авторизацию
                .authorizeHttpRequests(auth -> auth
                        /// Открытые эндпоинты (без токена)
//                        .requestMatchers(
//                                "/auth/*",   // для удобства тестирования разработки
//                                "/auth/register",
//                                "/auth/login",
//                                "/auth/refresh",
//                                "/auth/test",
//                                "/actuator/health",
//                                "/actuator/info"
//                        )
                                /// Всё остальное требует аутентификации
                        .anyRequest()///  разрешаем все запросы без аутентификации временно, для разработки                        .permitAll()

                        .authenticated()
                )

                /// Ставим STATELESS (не храним сессии, так как REST API с JWT)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                /// Регистрируем AuthenticationProvider
                /// Это механизм, который проверяет логин и пароль.
                .authenticationProvider(authenticationProvider())
                /// Добавляем JWT фильтр ПЕРЕД стандартным фильтром аутентификации
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);



//        http
//                .csrf(AbstractHttpConfigurer::disable)
//                .authorizeHttpRequests(auth ->
//                        auth
//                                .anyRequest().permitAll()  // ← открываем всё
//                );

        return http.build();
    }

    /// Проверяет логин и пароль.
    /// Связывает UserDetailsService и PasswordEncoder
    @Bean
    public AuthenticationProvider authenticationProvider() {
        /// DaoAuthenticationProvider	    Проверка по БД (UserDetailsService + PasswordEncoder)
        /// LdapAuthenticationProvider	    Проверка через LDAP/Active Directory
        /// JaasAuthenticationProvider	    Java Authentication and Authorization Service
        /// CustomAuthenticationProvider	Своя логика (например, SMS-код)
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    /// аутентификации Spring Security
    /// может управлять несколькими AuthenticationProvider'ами
    /// Пример: Можно настроить, чтобы пользователь мог войти:
    /// Через email/пароль (Provider 1)
    /// Через номер телефона/SMS (Provider 2)
    /// Через QR-код (Provider 3)
    /// AuthenticationManager попробует провайдеров по очереди, пока один не подтвердит.
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();   /// Spring Boot автоматически создаёт AuthenticationManager, который:
                                                    /// Обнаруживает все AuthenticationProvider бины
                                                    ///Связывает их с глобальной конфигурацией
                                                    ///Настраивает цепочки провайдеров
    }

    /// Алгоритм хеширования паролей.
    /// Защищает пароли в БД
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
