package com.restaurant.kitchen_service.controller;

import com.restaurant.kitchen_service.entity.Ticket;
import com.restaurant.kitchen_service.enums.TicketStatusEnum;
import com.restaurant.kitchen_service.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/kitchen")
@RequiredArgsConstructor
@Slf4j
public class KitchenController {

    private final TicketRepository ticketRepository;

    @GetMapping("/tickets")
    public ResponseEntity<List<Ticket>> getAllTickets() {
        log.info("GET /api/kitchen/tickets");
        return ResponseEntity.ok(ticketRepository.findAll());
    }

    @GetMapping("/tickets/order/{orderId}")
    public ResponseEntity<Ticket> getTicketByOrderId(@PathVariable Long orderId) {
        log.info("GET /api/kitchen/tickets/order/{}", orderId);
        Ticket ticket = ticketRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Тикет не найден: " + orderId));
        return ResponseEntity.ok(ticket);
    }

    @GetMapping("/tickets/status/{status}")
    public ResponseEntity<List<Ticket>> getTicketsByStatus(@PathVariable String status) {
        TicketStatusEnum statusEnum = TicketStatusEnum.valueOf(status.toUpperCase());
        List<Ticket> tickets = ticketRepository.findAll().stream()
                .filter(t -> t.getStatus() == statusEnum)
                .collect(Collectors.toList());
        return ResponseEntity.ok(tickets);
    }
}