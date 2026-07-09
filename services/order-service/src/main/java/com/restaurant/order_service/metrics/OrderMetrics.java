package com.restaurant.order_service.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class OrderMetrics {

    private final Counter ordersCreatedCounter;
    private final Timer orderProcessingTimer;
    private final MeterRegistry meterRegistry;

    public OrderMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        /// Счетчик
        /// Counter - монотонно возрастающий счетчик
        this.ordersCreatedCounter = Counter.builder("orders.created.total")        /// Имя метрики
                .description("Общее количество созданных заказов")                       /// Описание
                .tag("service", "order-service")                                        /// Тег (для фильтрации)
                .register(meterRegistry);

        /// таймер
        this.orderProcessingTimer = Timer.builder("orders.processing.duration")
                .description("Время обработки заказа")
                .tag("service", "order-service")
                .publishPercentiles(0.5, 0.95, 0.99)                                    /// Перцентили
                .publishPercentileHistogram()                                           /// Гистограмма
                .register(meterRegistry);                                               /// Регистрируем
    }

    /// Увеличивает счетчик созданных заказов на 1
    public void incrementOrdersCreated() {
        ordersCreatedCounter.increment();
    }

    /// Запуск таймера
    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }

    /// Остановка таймера
    public void stopTimer(Timer.Sample sample) {
        sample.stop(orderProcessingTimer);
    }
}
