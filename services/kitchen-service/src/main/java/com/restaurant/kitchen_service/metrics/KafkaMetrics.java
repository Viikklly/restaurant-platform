package com.restaurant.kitchen_service.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * KAFKA МЕТРИКИ ДЛЯ KITCHEN SERVICE
 */
@Component
@Slf4j
public class KafkaMetrics {


    private final Counter messagesConsumedCounter;                  /// Счетчик полученных сообщений из Kafka
    private final Counter messagesProducedCounter;                  /// Счетчик отправленных сообщений в Kafka
    private final Timer messageProcessingTimer;                     /// Таймер обработки сообщения


    private final Counter orderReceivedCounter;                     /// Заказы, принятые кухней (OPEN)
    private final Counter orderPreparingCounter;                    /// Заказы в процессе готовки (IN_PROGRESS)
    private final Counter orderReadyCounter;                        ///  Готовые заказы (READY)
    private final Counter cookingErrorCounter;


    private final MeterRegistry meterRegistry;
    private final String serviceName;




    public KafkaMetrics(MeterRegistry meterRegistry, @Value("${spring.application.name}") String serviceName) {


        this.meterRegistry = meterRegistry;
        this.serviceName = serviceName;

        this.messagesConsumedCounter = Counter.builder("kafka.messages.consumed.total")
                .description("Общее количество потребленных сообщений из Kafka")
                .tag("service", serviceName)
                .register(meterRegistry);

        this.messagesProducedCounter = Counter.builder("kafka.messages.produced.total")
                .description("Общее количество отправленных сообщений в Kafka")
                .tag("service", serviceName)
                .register(meterRegistry);

        this.messageProcessingTimer = Timer.builder("kafka.message.processing.duration")
                .description("Время обработки сообщения из Kafka")
                .tag("service", serviceName)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);


        this.orderReceivedCounter = Counter.builder("kitchen.orders.received.total")
                .description("Количество полученных заказов")
                .tag("service", serviceName)
                .register(meterRegistry);

        this.orderPreparingCounter = Counter.builder("kitchen.orders.preparing.total")
                .description("Количество заказов в процессе готовки")
                .tag("service", serviceName)
                .register(meterRegistry);

        this.orderReadyCounter = Counter.builder("kitchen.orders.ready.total")
                .description("Количество готовых заказов")
                .tag("service", serviceName)
                .register(meterRegistry);

        this.cookingErrorCounter = Counter.builder("kitchen.orders.errors.total")
                .description("Количество ошибок при готовке")
                .tag("service", serviceName)
                .register(meterRegistry);
    }


    public void incrementConsumed() {
        messagesConsumedCounter.increment();
    }

    public void incrementProduced() {
        messagesProducedCounter.increment();
    }

    public Timer.Sample startProcessingTimer() {
        return Timer.start(meterRegistry);
    }

    public void stopProcessingTimer(Timer.Sample sample) {
        if (sample != null) {
            sample.stop(messageProcessingTimer);
        }
    }

    public void incrementOrderReceived() {
        orderReceivedCounter.increment();
    }

    public void incrementOrderPreparing() {
        orderPreparingCounter.increment();
    }

    public void incrementOrderReady() {
        orderReadyCounter.increment();
    }

    public void incrementCookingError() {
        cookingErrorCounter.increment();
    }
}