package com.restaurant.kitchen_service.service;

import com.restaurant.common.events.KitchenOrderReadyEvent;
import com.restaurant.common.events.OrderPaidEvent;
import com.restaurant.kitchen_service.entity.Ticket;
import com.restaurant.kitchen_service.enums.TicketStatusEnum;
import com.restaurant.kitchen_service.metrics.KafkaMetrics;
import com.restaurant.kitchen_service.repository.TicketRepository;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;

import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
@RequiredArgsConstructor
@Slf4j
public class KitchenService {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final TicketRepository ticketRepository;

    private final KitchenRedisService redisService; /// REDIS

    private final KafkaMetrics kafkaMetrics;    /// Метрики

    /// отдельный бин для транзакций
    private final CookingProcessor cookingProcessor;

    @Value("${kitchen.cooking-time-ms:5000}")
    private long cookingTimeMs;


    /// Очередь заказов
    private final ConcurrentLinkedQueue<Long> orderQueue = new ConcurrentLinkedQueue<>();

    /**
     * Получаем оплаченный заказ от Order Service
     * Топик: order-service.order.paid
     */
    @KafkaListener(topics = "order-service.order.paid", groupId = "kitchen-group")
    public void acceptOrder(OrderPaidEvent event) {

        log.info("КУХНЯ: Получен оплаченный заказ #{}", event.getOrderId());

        ///  Запускаем таймер для метрик
        Timer.Sample timer = kafkaMetrics.startProcessingTimer();


        try {

            kafkaMetrics.incrementConsumed();       /// Метрики полученное сообщение Kafka +1
            kafkaMetrics.incrementOrderReceived();  /// Метрики Заказ принят kafka +1

            /// Создаём тикет со статусом OPEN
            Ticket ticket = Ticket.builder()
                    .orderId(event.getOrderId())
                    .items(event.getOrderItemsList())
                    .status(TicketStatusEnum.OPEN)
                    .build();


            // Сохраняем через cookingProcessor
            Ticket savedTicket = cookingProcessor.saveTicket(ticket);
            log.info("Создан тикет для заказа #{}, статус: {}", ticket.getOrderId(), ticket.getStatus());

            /// Сохраняем в REDIS
            /// Безопасное сохранение в Redis (не падаем при ошибке)
            cacheTicketSafe(event.getOrderId(), savedTicket);

            ///  Добавляем в очередь заказов
            orderQueue.offer(event.getOrderId());
            log.info("Заказ #{} добавлен в очередь. Всего в очереди: {}", event.getOrderId(), orderQueue.size());

            /// Отправляем событие, что кухня начала готовить (для статуса PREPARING)
            kafkaTemplate.send("kitchen-service.cooking.started", event.getOrderId());
            log.info("Отправлено событие 'kitchen-service.cooking.started' для заказа #{}", event.getOrderId());

        } catch (Exception e) {
            ///  Метрики ошибка при заказе +1
            kafkaMetrics.incrementCookingError();

            log.error("Ошибка при принятии заказа #{}", event.getOrderId(), e);
            throw e;  /// Kafka Сообщение остается в очереди для повторной обработки

        } finally {
            /// Останавливаем таймер для метрик
            kafkaMetrics.stopProcessingTimer(timer);
        }
    }

    /**
     * Завершаем готовку (IN_PROGRESS → READY) и отправляем событие
     */
    public void completeCooking(Long orderId) {
        log.info("Завершение готовки для заказа #{}", orderId);


        try {
            /// Вызов через cookingProcessor
            Ticket updatedTicket = cookingProcessor.completeCooking(orderId);

            /// Метрики Сообщение отправлено в kafka +1
            kafkaMetrics.incrementProduced();


            /// Обновляем в REDIS
            cacheTicketSafe(orderId, updatedTicket);

            log.info(" Заказ #{}: статус {} - готов к выдаче!", orderId, updatedTicket.getStatus());

            /// ОТПРАВЛЯЕМ СОБЫТИЕ В KAFKA
            KitchenOrderReadyEvent event = new KitchenOrderReadyEvent(
                    orderId,
                    "ticket-" + orderId,
                    updatedTicket.getItems(),
                    "READY"
            );
            kafkaTemplate.send("kitchen-service.order.ready", event);
            log.info("Отправлено событие KitchenOrderReadyEvent в топик 'kitchen-service.order.ready'");
        } catch (Exception e) {
            /// Метрики ошибка готовки заказа +1
            kafkaMetrics.incrementCookingError();

            log.error("Ошибка при завершении готовки заказа #{}", orderId, e);
            throw e;
        }
    }

    /**
     *  Отмена заказа
     */
    @KafkaListener(topics = "order-service.order.cancelled", groupId = "kitchen-group")
    public void cancelOrder(Long orderId) {
        log.info("КУХНЯ: Получена отмена заказа #{}", orderId);

        try {
            /// Вызов через cookingProcessor
            boolean cancelled = cookingProcessor.cancelTicket(orderId);

            if (cancelled) {
                kafkaTemplate.send("kitchen-service.order.cancelled", orderId);
                log.info("Отправлено событие об отмене заказа #{}", orderId);
            }
        } catch (Exception e) {
            kafkaMetrics.incrementCookingError();
            log.error("Ошибка при отмене заказа #{}", orderId, e);
            throw e;
        }
    }


    /**
     * Асинхронный запуск готовки
     */
    @Async("kitchenExecutor")
    ///@Transactional(propagation = Propagation.REQUIRES_NEW)
    public void startCookingAsync(Long orderId) {
        log.info("Начало готовки для заказа #{} (поток: {})",
                orderId, Thread.currentThread().getName());

        try {
            ///Вызов через cookingProcessor
            Ticket ticket = cookingProcessor.updateTicketStatus(
                    orderId,
                    TicketStatusEnum.IN_PROGRESS
            );

            /// Если тикет сразу READY (нет блюд) — отправляем событие
            if (ticket.getStatus() == TicketStatusEnum.READY) {
                log.info("Заказ #{} без блюд, сразу отправляем READY", orderId);
                completeCooking(orderId);
                return;
            }

            kafkaMetrics.incrementOrderPreparing();
            log.info("Заказ #{}: статус {} - начали готовить", orderId, ticket.getStatus());

            // Готовим каждое блюдо
            cookDishes(orderId, ticket.getItems());

            // Завершаем заказ
            completeCooking(orderId);

        } catch (Exception e) {
            kafkaMetrics.incrementCookingError();
            log.error("Ошибка при старте готовки заказа #{}", orderId, e);
            /// Откат через cookingProcessor
            cookingProcessor.rollbackCooking(orderId);
            throw new RuntimeException("Ошибка при готовке заказа " + orderId, e);
        }
    }


    /**
     * Готовим каждое блюдо
     */
    private void cookDishes(Long orderId, List<String> items) {

        if (items == null || items.isEmpty()) {
            log.warn("Заказ #{} не содержит блюд", orderId);
            return;
        }


        log.info("Начинаем готовить {} блюд для заказа #{}", items.size(), orderId);

        for (int i = 0; i < items.size(); i++) {
            String dish = items.get(i);

            log.info("Блюдо {}/{}: '{}' - начали готовить", i + 1, items.size(), dish);

            try {
                Thread.sleep(cookingTimeMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Готовка блюда '{}' прервана", dish);
                throw new RuntimeException("Готовка прервана", e);
            }

            log.info("Блюдо {}/{}: '{}' - готово!",
                    i + 1, items.size(), dish);
        }

        log.info("Все {} блюд для заказа #{} готовы!", items.size(), orderId);
    }




    /// Обработчик очереди
    @Scheduled(fixedDelay = 3000)
    public void processQueue() {
        Long orderId = orderQueue.poll();
        if (orderId != null) {
            log.info("Заказ #{} взят из очереди в работу. Осталось: {}",
                    orderId, orderQueue.size());
            startCookingAsync(orderId);
        }
    }


    /**
     * Получить все тикеты
     */
    @Transactional(readOnly = true)
    public List<Ticket> getAllTickets() {
        log.info("Получение всех тикетов");
        return ticketRepository.findAll();
    }

    /**
     * Получить тикет по ID заказа
     */
    @Transactional(readOnly = true)
    public List<String> getItemsListForOrderId(Long orderId) {
        log.info("Получение списка блюд для заказа #{}", orderId);
        Ticket ticket = getTicketByOrderId(orderId);
        return ticket.getItems();
    }

    /**
     * Получить тикет по ID
     */
    @Transactional(readOnly = true)
    public Ticket getTicketById(Long id) {
        log.info("Получение тикета по ID: {}", id);
        return ticketRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Тикет не найден: " + id));
    }

    /**
     * Получить тикеты по статусу
     */
    @Transactional(readOnly = true)
    public List<Ticket> getTicketsByStatus(TicketStatusEnum status) {
        log.info("Получение тикетов по статусу: {}", status);
        return ticketRepository.findByStatus(status);
    }

    /**
     * Получить открытые тикеты (OPEN)
     */
    @Transactional(readOnly = true)
    public List<Ticket> getOpenTickets() {
        log.info("Получение открытых тикетов");
        return ticketRepository.findByStatus(TicketStatusEnum.OPEN);
    }

    /**
     * Получить тикеты в работе (IN_PROGRESS)
     */
    @Transactional(readOnly = true)
    public List<Ticket> getInProgressTickets() {
        log.info("Получение тикетов в работе");
        return ticketRepository.findByStatus(TicketStatusEnum.IN_PROGRESS);
    }

    /**
     * Получить готовые тикеты (READY)
     */
    @Transactional(readOnly = true)
    public List<Ticket> getReadyTickets() {
        log.info("Получение готовых тикетов");
        return ticketRepository.findByStatus(TicketStatusEnum.READY);
    }

    /**
     *  Получить список блюд по ID тикета
     */
        public List<String> getItemsListForTicketId(Long ticketId) {
            log.info("Получение списка блюд для тикета #{}", ticketId);
            Ticket ticket = ticketRepository.findById(ticketId)
                    .orElseThrow(() -> new RuntimeException("Тикет не найден: " + ticketId));
            return ticket.getItems();
    }


    /**
     * Проверка статуса готовки
     */
    @Transactional(readOnly = true)
    public boolean isCookingComplete(Long orderId) {
        return ticketRepository.findByOrderId(orderId)
                .map(ticket -> ticket.getStatus() == TicketStatusEnum.READY)
                .orElse(false);
    }


    /**
     * Безопасное сохранение в Redis (не падаем при ошибке)
     */
    private void cacheTicketSafe(Long orderId, Ticket ticket) {
        try {
            redisService.cacheTicket(orderId, ticket);
        } catch (Exception e) {
            log.warn("Не удалось сохранить в Redis для заказа #{}", orderId, e);
        }
    }


    @Transactional(readOnly = true)
    public Ticket getTicketByOrderId(Long orderId) {
        log.info("Получение тикета по заказу: {}", orderId);

        /// Проверяем REDIS
        Optional<Object> cachedTicket = redisService.getCachedTicket(orderId);
        if (cachedTicket.isPresent()) {
            try {
                return (Ticket) cachedTicket.get();
            } catch (ClassCastException e) {
                log.error("Ошибка приведения типа для orderId: {}", orderId, e);
                redisService.evictTicket(orderId);
            }
        }

        /// Загружаем из БД
        log.info("Тикет для заказа #{} не найден в Redis, загружаем из БД", orderId);
        Ticket ticket = ticketRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Тикет не найден для заказа: " + orderId));

        /// Сохраняем в REDIS
        cacheTicketSafe(orderId, ticket);

        return ticket;
    }

}