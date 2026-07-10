package com.restaurant.kitchen_service.monitoring;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Мониторинг состояния пула потоков кухни
 * Логирует загрузку каждую минуту
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class KitchenThreadPoolMonitor {

    private final Executor kitchenExecutor;

    /**
     * Проверка состояния пула потоков каждые 60 секунд
     */
    @Scheduled(fixedDelay = 60000)
    public void logThreadPoolStatus() {
        if (!(kitchenExecutor instanceof ThreadPoolExecutor pool)) {
            return;
        }

        /// Основные метрики
        int activeThreads = pool.getActiveCount();           // Активные повара
        int poolSize = pool.getPoolSize();                   // Всего потоков в пуле
        int coreSize = pool.getCorePoolSize();               // Базовое количество
        int maxSize = pool.getMaximumPoolSize();             // Максимум
        int queueSize = pool.getQueue().size();              // Заказов в очереди
        long completedOrders = pool.getCompletedTaskCount(); // Выполнено заказов

        /// Загрузка в процентах
        double loadPercentage = poolSize > 0
                ? (double) activeThreads / poolSize * 100
                : 0.0;


        log.info("""
            КУХНЯ: Потоки {}/{} активны, очередь: {}, выполнено: {} заказов, загрузка: {:.0f}%""",
                activeThreads,
                poolSize,
                queueSize,
                completedOrders,
                loadPercentage
        );

        /// Предупреждение при перегрузке
        if (activeThreads == maxSize && queueSize > 0) {
            log.warn("Все повара заняты! {} заказов в очереди ждут", queueSize);
        }
    }
}