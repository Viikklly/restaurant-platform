package com.restaurant.kitchen_service.service;

import com.restaurant.kitchen_service.entity.Ticket;
import com.restaurant.kitchen_service.enums.TicketStatusEnum;
import com.restaurant.kitchen_service.metrics.KafkaMetrics;
import com.restaurant.kitchen_service.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * CookingProcessor — отдельный бин для транзакционных операций с тикетами.
 * Так как
 * - @Transactional работает через Spring Proxy
 * - Вызов метода изнутри того же класса - прокси обходится - транзакция не открывается
 * Надо
 * - Вызов метода из ДРУГОГО бина - прокси работает - транзакция открывается
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CookingProcessor {

    private final TicketRepository ticketRepository;
    private final KitchenRedisService redisService;
    private final KafkaMetrics kafkaMetrics;


    /**
     * Сохранение нового тикета в транзакции.
     * Используется при принятии заказа от Kafka.
     */
    @Transactional
    public Ticket saveTicket(Ticket ticket) {
        log.info(" [PROCESSOR] Сохранение нового тикета для заказа #{}", ticket.getOrderId());
        Ticket saved = ticketRepository.save(ticket);
        log.info(" [PROCESSOR] Тикет #{} сохранен с ID: {}", ticket.getOrderId(), saved.getId());
        return saved;
    }

    /**
     * Обновление статуса тикета в транзакции.
     * Вызывается из KitchenService (через прокси → транзакция работает).
     */
    @Transactional
    public Ticket updateTicketStatus(Long orderId, TicketStatusEnum newStatus) {
        log.info(" [PROCESSOR] Обновление статуса заказа #{} -> {}", orderId, newStatus);

        Ticket ticket = ticketRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Тикет не найден: " + orderId));

        // Проверка на пустые блюда — если пусто, сразу READY
        if (ticket.getItems() == null || ticket.getItems().isEmpty()) {
            log.warn("Заказ #{} не содержит блюд, завершаем готовку", orderId);
            ticket.setStatus(TicketStatusEnum.READY);
            Ticket updated = ticketRepository.save(ticket);
            cacheTicketSafe(orderId, updated);
            return updated;
        }

        ticket.setStatus(newStatus);
        Ticket updatedTicket = ticketRepository.save(ticket);
        cacheTicketSafe(orderId, updatedTicket);

        log.info(" [PROCESSOR] Статус заказа #{} обновлен на {}", orderId, newStatus);
        return updatedTicket;
    }

    /**
     * Завершение готовки (IN_PROGRESS → READY).
     * Транзакция работает, так как вызывается из KitchenService.
     */
    @Transactional
    public Ticket completeCooking(Long orderId) {
        log.info(" [PROCESSOR] Завершение готовки для заказа #{}", orderId);

        Ticket ticket = ticketRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Тикет не найден для заказа: " + orderId));

        ticket.setStatus(TicketStatusEnum.READY);
        Ticket updatedTicket = ticketRepository.save(ticket);

        // Метрики
        kafkaMetrics.incrementOrderReady();

        // Обновляем в Redis
        cacheTicketSafe(orderId, updatedTicket);

        log.info(" [PROCESSOR] Заказ #{} готов к выдаче!", orderId);
        return updatedTicket;
    }

    /**
     * Откат готовки (IN_PROGRESS → OPEN).
     */
    @Transactional
    public void rollbackCooking(Long orderId) {
        log.info(" [PROCESSOR] Откат готовки для заказа #{}", orderId);

        Ticket ticket = ticketRepository.findByOrderId(orderId).orElse(null);
        if (ticket == null) {
            log.warn("Тикет для заказа #{} не найден", orderId);
            return;
        }

        if (ticket.getStatus() == TicketStatusEnum.IN_PROGRESS) {
            ticket.setStatus(TicketStatusEnum.OPEN);
            ticketRepository.save(ticket);
            cacheTicketSafe(orderId, ticket);
            log.info(" [PROCESSOR] Статус заказа #{} возвращён в OPEN", orderId);
        }
    }

    /**
     * Отмена заказа.
     */
    @Transactional
    public boolean cancelTicket(Long orderId) {
        log.info(" [PROCESSOR] Отмена тикета для заказа #{}", orderId);

        Ticket ticket = ticketRepository.findByOrderId(orderId).orElse(null);
        if (ticket == null) {
            log.warn("Тикет для заказа #{} не найден", orderId);
            return false;
        }

        if (ticket.getStatus() == TicketStatusEnum.READY) {
            log.warn("Заказ #{} уже готов, отмена невозможна", orderId);
            return false;
        }

        ticket.setStatus(TicketStatusEnum.CANCELLED);
        ticketRepository.save(ticket);

        // Удаляем из Redis
        redisService.evictTicket(orderId);

        log.info(" [PROCESSOR] Заказ #{} отменён", orderId);
        return true;
    }

    /**
     * Безопасное сохранение в Redis.
     */
    private void cacheTicketSafe(Long orderId, Ticket ticket) {
        try {
            redisService.cacheTicket(orderId, ticket);
        } catch (Exception e) {
            log.warn("Не удалось сохранить в Redis для заказа #{}", orderId, e);
        }
    }
}