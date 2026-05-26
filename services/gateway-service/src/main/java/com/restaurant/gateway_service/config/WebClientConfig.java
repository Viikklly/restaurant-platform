package com.restaurant.gateway_service.config;


import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/// Gateway будет вызывать Auth Service через HTTP, чтобы проверить JWT токен

/**
 * Конфигурация WebClient для вызова AUTH-SERVICE через Eureka с таймаутами 5 секунд.
 */
@Configuration
public class WebClientConfig {

    /**
     * Создаёт билдер для WebClient с поддержкой балансировки нагрузки через Eureka.
     *  Этот метод готовит "фабрику" для создания HTTP клиентов, которые умеют находить другие
     *  сервисы через Eureka и распределять между ними нагрузку.
     */

    @Bean
    @LoadBalanced  /// Включает клиентскую балансировку нагрузки. Запрос идет напрямую на localhost:8081
    public WebClient.Builder loadBalancedWebClientBuilder() {
        return WebClient.builder();
    }


    /**
     * Предоставляет настроенные экземпляры WebClient для вызова микросервисов,
     * с целью проверки JWT-токенов.
     * @param builder
     * @return
     */
    @Bean
    public WebClient authServiceWebClient(WebClient.Builder builder) {
        /// Настройки таймаутов для WebClient
        HttpClient httpClient = HttpClient.create()     /// Создает асинхронный сетевой клиент, который умеет обрабатывать много соединений в одном потоке
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)  /// таймаут подключения - 5 секунд
                .responseTimeout(Duration.ofSeconds(5))               /// 5 секунд на ответ
                .doOnConnected(conn ->                      /// Добавляет действия, которые выполняются после установления соединения
                        conn.addHandlerLast(new ReadTimeoutHandler(5, TimeUnit.SECONDS)) /// Таймаут на чтение данных - 5 секунд
                                .addHandlerLast(new WriteTimeoutHandler(5, TimeUnit.SECONDS)));/// Таймаут на запись данных - 5 секунд

        return builder
                .clientConnector(new ReactorClientHttpConnector(httpClient)) /// Подключает настроенный HttpClient к WebClient'у. Это "адаптер", который соединяет высокоуровневый WebClient с низкоуровневым HttpClient
                .baseUrl("http://AUTH-SERVICE")  /// Устанавливает базовый URL для всех запросов. AUTH-SERVICE а не localhost:8081
                .build();
    }
}