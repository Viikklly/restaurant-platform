package com.restaurant.auth_service.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * KAFKA МЕТРИКИ ДЛЯ AUTH SERVICE
 */
@Component
public class KafkaMetrics {

    private final Counter messagesProducedCounter;          /// Считает сколько сообщений мы отправили в Kafka


    private final Counter loginEventsCounter;               /// Считает сколько раз пользователи входили в систему
    private final Counter registerEventsCounter;            /// Считает сколько раз пользователи регистрировались
    private final Counter authErrorCounter;                 /// Считает сколько ошибок авторизации произошло

    public KafkaMetrics( MeterRegistry meterRegistry, @Value("${spring.application.name}") String serviceName) {

        /// Счетчик отправленных сообщений
        this.messagesProducedCounter = Counter.builder("kafka.messages.produced.total")
                .description("Общее количество отправленных сообщений в Kafka")
                .tag("service", serviceName)
                .register(meterRegistry);


        /// События входа
        this.loginEventsCounter = Counter.builder("auth.kafka.login.events.total")
                .description("Количество событий входа в систему из Kafka")
                .tag("service", serviceName)
                .register(meterRegistry);

        /// События регистрации
        this.registerEventsCounter = Counter.builder("auth.kafka.register.events.total")
                .description("Количество событий регистрации из Kafka")
                .tag("service", serviceName)
                .register(meterRegistry);

        /// Ошибки авторизации
        this.authErrorCounter = Counter.builder("auth.kafka.error.events.total")
                .description("Количество ошибок авторизации из Kafka")
                .tag("service", serviceName)
                .register(meterRegistry);
    }


    ///  При логировании
    public void incrementProduced() {
        messagesProducedCounter.increment();
    }

    /// При успешном логине
    public void incrementLogin() {
        loginEventsCounter.increment();
    }

    /// При успешной регистрации
    public void incrementRegister() {
        registerEventsCounter.increment();
    }

    public void incrementAuthError() {
        authErrorCounter.increment();
    }
}