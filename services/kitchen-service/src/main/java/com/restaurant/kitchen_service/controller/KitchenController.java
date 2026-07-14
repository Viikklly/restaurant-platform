package com.restaurant.kitchen_service.controller;

import com.restaurant.kitchen_service.entity.Ticket;
import com.restaurant.kitchen_service.enums.TicketStatusEnum;
import com.restaurant.kitchen_service.service.KitchenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/kitchen")
@RequiredArgsConstructor
@Slf4j
public class KitchenController {

    private final KitchenService kitchenService;

    /**
     * Получить все тикеты
     */
    @GetMapping("/tickets")
    public ResponseEntity<List<Ticket>> getAllTickets() {
        log.info("GET /api/kitchen/tickets");
        return ResponseEntity.ok(kitchenService.getAllTickets());
    }

    /**
     * Получить тикет по ID
     */
    @GetMapping("/tickets/{id}")
    public ResponseEntity<Ticket> getTicketById(@PathVariable Long id) {
        log.info("GET /api/kitchen/tickets/{}", id);
        return ResponseEntity.ok(kitchenService.getTicketById(id));
    }


    /**
     * Получить тикет по ID заказа
     */
    @GetMapping("/tickets/order/{orderId}/items")
    public ResponseEntity<Ticket> getTicketByOrderId(@PathVariable Long orderId) {
        log.info("GET /api/kitchen/tickets/order/{}", orderId);
        return ResponseEntity.ok(kitchenService.getTicketByOrderId(orderId));
    }

    /**
     * Получить тикеты по статусу
     */
    @GetMapping("/tickets/status/{status}")
    public ResponseEntity<List<Ticket>> getTicketsByStatus(@PathVariable String status) {
        log.info("GET /api/kitchen/tickets/status/{}", status);
        TicketStatusEnum statusEnum = TicketStatusEnum.valueOf(status.toUpperCase());
        return ResponseEntity.ok(kitchenService.getTicketsByStatus(statusEnum));
    }


    /**
     * Получить все открытые тикеты
     */
    @GetMapping("/tickets/status/open")
    public ResponseEntity<List<Ticket>> getOpenTickets() {
        log.info("GET /api/kitchen/tickets/status/open");
        return ResponseEntity.ok(kitchenService.getOpenTickets());
    }

    /**
     * Получить тикеты в работе
     */
    @GetMapping("/tickets/status/in-progress")
    public ResponseEntity<List<Ticket>> getInProgressTickets() {
        log.info("GET /api/kitchen/tickets/status/in-progress");
        return ResponseEntity.ok(kitchenService.getInProgressTickets());
    }

    /**
     * Получить готовые тикеты
     */
    @GetMapping("/tickets/status/ready")
    public ResponseEntity<List<Ticket>> getReadyTickets() {
        log.info("GET /api/kitchen/tickets/status/ready");
        return ResponseEntity.ok(kitchenService.getReadyTickets());
    }

    /**
     * Получить список блюд по ID тикета
     */
    @GetMapping("/tickets/{ticketId}/items")
    public ResponseEntity<List<String>> getItemsListByTicketId(@PathVariable Long ticketId) {
        log.info("GET /api/kitchen/tickets/{}/items", ticketId);
        return ResponseEntity.ok(kitchenService.getItemsListForTicketId(ticketId));
    }
}