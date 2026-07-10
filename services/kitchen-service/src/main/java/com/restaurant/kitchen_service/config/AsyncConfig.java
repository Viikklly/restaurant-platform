package com.restaurant.kitchen_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "kitchenExecutor")
    public Executor kitchenExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Базовые настройки
        executor.setCorePoolSize(5);           // Минимальное количество потоков
        executor.setMaxPoolSize(15);           // Максимальное количество потоков
        executor.setQueueCapacity(100);        // Размер очереди задач

        // Настройки для graceful shutdown
        executor.setKeepAliveSeconds(60);      // Время жизни лишних потоков
        executor.setThreadNamePrefix("kitchen-"); // Префикс для потоков
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();
        return executor;
    }
}