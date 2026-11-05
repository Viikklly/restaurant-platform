package com.restaurant.payment_service.config;

import org.apache.kafka.common.errors.SerializationException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.EnableKafkaRetryTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 *  Конфигурация Kafka для order-service.
 * Включает non-blocking retry через @RetryableTopic и DLT.
 */
@Configuration
@EnableKafka
@EnableKafkaRetryTopic
@Profile("!test")   //НЕ активировать в тестах
public class KafkaConfig {

    /**
     * TaskScheduler — обязателен для @EnableKafkaRetryTopic.
     * Без него Spring Kafka падает с:
     * "Either a RetryTopicSchedulerWrapper or TaskScheduler bean is required"
     * Используется для отложенных retry (backoff между попытками).
     */
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("kafka-retry-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(10);
        scheduler.initialize();
        return scheduler;
    }

    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<String, Object> kafkaTemplate) {

        /// ExponentialBackOff: 1s → 2s → 4s → 8s → 10s (max)
        ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
        backOff.setMaxInterval(10_000L);
        backOff.setMaxElapsedTime(30_000L);

        /// Recoverer: после исчерпания попыток отправляет в `<topic>-dlt`
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);

        /// Не ретраить фатальные ошибки — retry не поможет
        handler.addNotRetryableExceptions(
                SerializationException.class,
                ClassCastException.class,
                org.springframework.kafka.support.serializer.DeserializationException.class
        );

        return handler;
    }
}