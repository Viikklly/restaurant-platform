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

            Ticket savedTicket = ticketRepository.save(ticket);
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
    @Transactional
    public void completeCooking(Long orderId) {
        log.info("Завершение готовки для заказа #{}", orderId);


        try {
            Ticket ticket = ticketRepository.findByOrderId(orderId)
                    .orElseThrow(() -> new RuntimeException("Тикет не найден для заказа: " + orderId));

            ticket.setStatus(TicketStatusEnum.READY);

            Ticket updatedTicket = ticketRepository.save(ticket);


            /// Метрики счетчика готово +1
            kafkaMetrics.incrementOrderReady();
            /// Метрики Сообщение отправлено в kafka +1
            kafkaMetrics.incrementProduced();


            /// Обновляем в REDIS
            cacheTicketSafe(orderId, updatedTicket);

            log.info(" Заказ #{}: статус {} - готов к выдаче!", orderId, ticket.getStatus());

            /// ОТПРАВЛЯЕМ СОБЫТИЕ В KAFKA
            KitchenOrderReadyEvent event = new KitchenOrderReadyEvent(
                    orderId,
                    "ticket-" + orderId,
                    ticket.getItems(),
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
    @Transactional
    public void cancelOrder(Long orderId) {
        log.info(" КУХНЯ: Получена отмена заказа #{}", orderId);


        try {
            Ticket ticket = ticketRepository.findByOrderId(orderId).orElse(null);
            if (ticket == null) {
                log.warn("Тикет для заказа #{} не найден", orderId);
                return;
            }

            if (ticket.getStatus() == TicketStatusEnum.READY) {
                log.warn("Заказ #{} уже готов, отмена невозможна", orderId);
                return;
            }

            ticket.setStatus(TicketStatusEnum.CANCELLED);


            ticketRepository.save(ticket);


            /// Удаляем из REDIS
            redisService.evictTicket(orderId);

            /// Отправляем событие об отмене
            kafkaTemplate.send("kitchen-service.order.cancelled", orderId);
            log.info(" Отправлено событие об отмене заказа #{}", orderId);

            log.info("Заказ #{}: статус {} - отменён", orderId, ticket.getStatus());


        } catch (Exception e) {
            /// Метрики ошибка заказа +1
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

        log.info("Начало готовки для заказа #{} (поток: {})", orderId, Thread.currentThread().getName());


        try {

            /// Вынесли транзакционные методы в отдельный метод
            Ticket ticket = updateTicketStatusTransactional(orderId, TicketStatusEnum.IN_PROGRESS);

            if (ticket == null) {
                log.info("Заказ #{} пустой, готовка не требуется", orderId);
                return;
            }

            kafkaMetrics.incrementOrderPreparing();
            log.info("Заказ #{}: статус {} - начали готовить", orderId, ticket.getStatus());

            /// Готовим каждое блюдо по очереди
            cookDishes(orderId, ticket.getItems());

            /// Завершаем заказ
            completeCooking(orderId);

        } catch (Exception e) {
            kafkaMetrics.incrementCookingError();
            log.error("Ошибка при старте готовки заказа #{}", orderId, e);
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

    /**
     * Асинхронный процесс готовки (имитация)
     */
    public void processCooking(Long orderId) {
        log.info(" Заказ #{}: готовка началась ({} мс)", orderId, cookingTimeMs);

        try {
            /// Имитация готовки
            Thread.sleep(cookingTimeMs);

            /// Завершаем готовку
            completeCooking(orderId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Готовка прервана для заказа #{}", orderId, e);

            /// Возвращаем статус обратно, если готовка прервана
            rollbackCooking(orderId);

        } catch (Exception e) {
            kafkaMetrics.incrementCookingError();
            log.error("Ошибка при готовке заказа #{}", orderId, e);
            throw new RuntimeException("Ошибка при готовке заказа " + orderId, e);
        }
    }


    /**
     * Транзакционная логика для
     * Вызывается из асинхронного метода, так как могут быть проблемы при использовании
     * @Transactional + @Async
     */
    @Transactional
    public Ticket updateTicketStatusTransactional(Long orderId, TicketStatusEnum newStatus) {
        log.info("Обновление статуса заказа #{} -> {}", orderId, newStatus);

        Ticket ticket = ticketRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Тикет не найден: " + orderId));

        /// Проверка на пустые блюда
        if (ticket.getItems() == null || ticket.getItems().isEmpty()) {
            log.warn("Заказ #{} не содержит блюд, завершаем готовку", orderId);

            ticket.setStatus(TicketStatusEnum.READY);
            Ticket updated = ticketRepository.save(ticket);
            cacheTicketSafe(orderId, updated);

            return null;
        }

        ticket.setStatus(newStatus);
        Ticket updatedTicket = ticketRepository.save(ticket);
        cacheTicketSafe(orderId, updatedTicket);

        return updatedTicket;
    }


    /// Откат готовки для заказа
    @Transactional
    public void rollbackCooking(Long orderId) {
        log.info("Откат готовки для заказа #{}", orderId);

        try {
            Ticket ticket = ticketRepository.findByOrderId(orderId).orElse(null);
            if (ticket == null) {
                log.warn("Тикет для заказа #{} не найден", orderId);
                return;
            }

            /// Возвращаем статус обратно в OPEN
            if (ticket.getStatus() == TicketStatusEnum.IN_PROGRESS) {
                ticket.setStatus(TicketStatusEnum.OPEN);
                ticketRepository.save(ticket);
                cacheTicketSafe(orderId, ticket);
                log.info("Статус заказа #{} возвращён в OPEN", orderId);
            }

        } catch (Exception e) {
            log.error("Ошибка при откате готовки заказа #{}", orderId, e);
        }
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