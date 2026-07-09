package com.restaurant.order_service.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 *  KAFKA МЕТРИКИ ДЛЯ ORDER SERVICE
 */
@Component
public class KafkaMetrics {

    private final Counter messagesConsumedCounter;      /// Счетчик полученных сообщений из Kafka
    private final Counter messagesProducedCounter;      /// Счетчик отправленных сообщений в Kafka
    private final Timer messageProcessingTimer;         /// Время обработки каждого сообщения из Kafka


    private final Counter orderCreatedCounter;           /// Заказ создан
    private final Counter orderPaidCounter;              /// Заказ оплачен
    private final Counter orderCancelledCounter;         /// Заказ отменен
    private final Counter orderReadyCounter;             /// Заказ готов
    private final Counter processingErrorCounter;        /// Ошибки заказа

    private final MeterRegistry meterRegistry;



    public KafkaMetrics(MeterRegistry meterRegistry, @Value("${spring.application.name}") String serviceName
    ) {

        this.meterRegistry = meterRegistry;

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


        this.orderCreatedCounter = Counter.builder("order.kafka.created.total")
                .description("Количество созданных заказов")
                .tag("service", serviceName)
                .register(meterRegistry);

        this.orderPaidCounter = Counter.builder("order.kafka.paid.total")
                .description("Количество оплаченных заказов")
                .tag("service", serviceName)
                .register(meterRegistry);

        this.orderCancelledCounter = Counter.builder("order.kafka.cancelled.total")
                .description("Количество отмененных заказов")
                .tag("service", serviceName)
                .register(meterRegistry);

        this.orderReadyCounter = Counter.builder("order.kafka.ready.total")
                .description("Количество готовых заказов")
                .tag("service", serviceName)
                .register(meterRegistry);

        this.processingErrorCounter = Counter.builder("order.kafka.processing.errors.total")
                .description("Количество ошибок при обработке Kafka сообщений")
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

    public void incrementOrderCreated() {
        orderCreatedCounter.increment();
    }

    public void incrementOrderPaid() {
        orderPaidCounter.increment();
    }

    public void incrementOrderCancelled() {
        orderCancelledCounter.increment();
    }

    public void incrementOrderReady() {
        orderReadyCounter.increment();
    }

    public void incrementProcessingError() {
        processingErrorCounter.increment();
    }
}