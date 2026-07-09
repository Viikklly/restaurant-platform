package com.restaurant.payment_service.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * KAFKA МЕТРИКИ ДЛЯ PAYMENT SERVICE
 */
@Component
public class KafkaMetrics {


    private final Counter messagesConsumedCounter;
    private final Counter messagesProducedCounter;
    private final Timer messageProcessingTimer;


    private final Counter paymentRequestedCounter;    // Запрос на оплату
    private final Counter paymentSuccessCounter;      // Оплата успешна
    private final Counter paymentFailedCounter;       // Оплата неудачна
    private final Counter paymentRefundCounter;       // Возврат оплаты

    private final MeterRegistry meterRegistry;

    public KafkaMetrics(MeterRegistry meterRegistry, @Value("${spring.application.name}") String serviceName) {

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


        this.paymentRequestedCounter = Counter.builder("payment.kafka.requested.total")
                .description("Количество запросов на оплату из Kafka")
                .tag("service", serviceName)
                .register(meterRegistry);

        this.paymentSuccessCounter = Counter.builder("payment.kafka.success.total")
                .description("Количество успешных оплат")
                .tag("service", serviceName)
                .register(meterRegistry);

        this.paymentFailedCounter = Counter.builder("payment.kafka.failed.total")
                .description("Количество неудачных оплат")
                .tag("service", serviceName)
                .register(meterRegistry);

        this.paymentRefundCounter = Counter.builder("payment.kafka.refund.total")
                .description("Количество возвратов оплаты")
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


    public void incrementPaymentRequested() {
        paymentRequestedCounter.increment();
    }

    public void incrementPaymentSuccess() {
        paymentSuccessCounter.increment();
    }

    public void incrementPaymentFailed() {
        paymentFailedCounter.increment();
    }

    public void incrementPaymentRefund() {
        paymentRefundCounter.increment();
    }
}