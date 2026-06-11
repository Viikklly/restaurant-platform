package com.restaurant.kitchen_service.service;

import com.restaurant.common.events.KitchenOrderReadyEvent;
import com.restaurant.common.events.OrderPaidEvent;
import com.restaurant.kitchen_service.entity.Ticket;
import com.restaurant.kitchen_service.enums.TicketStatusEnum;
import com.restaurant.kitchen_service.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class KitchenService {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final TicketRepository ticketRepository;

    @Value("${kitchen.cooking-time-ms:5000}")
    private long cookingTimeMs;

    /**
     * Получаем оплаченный заказ от Order Service
     * Топик: order-service.order.paid
     */
    @KafkaListener(topics = "order-service.order.paid", groupId = "kitchen-group")
    public void acceptOrder(OrderPaidEvent event) {
        log.info("КУХНЯ: Получен оплаченный заказ #{}", event.getOrderId());

        /// Создаём тикет со статусом OPEN
        Ticket ticket = Ticket.builder()
                .orderId(event.getOrderId())
                .items(event.getOrderItemsList())
                .status(TicketStatusEnum.OPEN)
                .build();

        ticketRepository.save(ticket);
        log.info("Создан тикет для заказа #{}, статус: {}", ticket.getOrderId(), ticket.getStatus());


        /// Отправляем событие, что кухня начала готовить (для статуса PREPARING)
        kafkaTemplate.send("kitchen-service.cooking.started", event.getOrderId());
        log.info("Отправлено событие 'kitchen-service.cooking.started' для заказа #{}", event.getOrderId());


        /// Имитируем готовку
        startCooking(event.getOrderId());
    }

    /**
     *  Начинаем готовку (OPEN → IN_PROGRESS)
     */
    @Transactional
    public void startCooking(Long orderId) {
        Ticket ticket = ticketRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Тикет не найден для заказа: " + orderId));

        ticket.setStatus(TicketStatusEnum.IN_PROGRESS);

        ticketRepository.save(ticket);
        log.info("Заказ #{}: статус {} - начали готовить", orderId, ticket.getStatus());

        /// Запускаем процесс готовки в отдельном потоке
        completeCookingAfterDelay(orderId);
    }

    /**
     *  Завершаем готовку с задержкой
     */
    private void completeCookingAfterDelay(Long orderId) {
        new Thread(() -> {
            try {
                log.info("Заказ #{}: готовка... ({} мс)", orderId, cookingTimeMs);
                Thread.sleep(cookingTimeMs);
                completeCooking(orderId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Готовка прервана для заказа #{}", orderId);
            }
        }).start();
    }

    /**
     * Завершаем готовку (IN_PROGRESS → READY) и отправляем событие
     */
    @Transactional
    public void completeCooking(Long orderId) {
        Ticket ticket = ticketRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Тикет не найден для заказа: " + orderId));

        ticket.setStatus(TicketStatusEnum.READY);

        ticketRepository.save(ticket);

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
    }

    /**
     *  Отмена заказа
     */
    @KafkaListener(topics = "order-service.order.cancelled", groupId = "kitchen-group")
    @Transactional
    public void cancelOrder(Long orderId) {
        log.info(" КУХНЯ: Получена отмена заказа #{}", orderId);

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

        log.info("Заказ #{}: статус {} - отменён", orderId, ticket.getStatus());
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
    public Ticket getTicketByOrderId(Long orderId) {
        log.info("Получение тикета по заказу: {}", orderId);
        return ticketRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Тикет не найден для заказа: " + orderId));
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
        return ticketRepository.findAll().stream()
                .filter(ticket -> ticket.getStatus() == status)
                .collect(Collectors.toList());
    }

    /**
     * Получить открытые тикеты (OPEN)
     */
    @Transactional(readOnly = true)
    public List<Ticket> getOpenTickets() {
        log.info("Получение открытых тикетов");
        return getTicketsByStatus(TicketStatusEnum.OPEN);
    }

    /**
     * Получить тикеты в работе (IN_PROGRESS)
     */
    @Transactional(readOnly = true)
    public List<Ticket> getInProgressTickets() {
        log.info("Получение тикетов в работе");
        return getTicketsByStatus(TicketStatusEnum.IN_PROGRESS);
    }

    /**
     * Получить готовые тикеты (READY)
     */
    @Transactional(readOnly = true)
    public List<Ticket> getReadyTickets() {
        log.info("Получение готовых тикетов");
        return getTicketsByStatus(TicketStatusEnum.READY);
    }

    /**
     * Получить список блюд тикета
     */
        public List<String> getItemsListForTicketId(Long ticketId) {
        log.info("Получение готовых тикетов");
            Ticket ticketById = ticketRepository.findById(ticketId)
                    .orElseThrow(() -> new RuntimeException("Тикет не найден: " + ticketId));
            return ticketById.getItems();
    }


}