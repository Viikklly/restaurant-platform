package com.restaurant.kitchen_service.service;

import com.restaurant.kitchen_service.entity.Ticket;
import com.restaurant.kitchen_service.enums.TicketStatusEnum;
import com.restaurant.kitchen_service.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class KitchenServiceTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private TicketRepository ticketRepository;

    @InjectMocks
    private KitchenService kitchenService;


    private Ticket ticket;

    @BeforeEach
    void setUp() {
        /// Создаем тикет
        ticket = new Ticket();
        ticket.setId(1L);
        ticket.setStatus(TicketStatusEnum.OPEN);

    }

    @Test
    void acceptOrder() {
    }
}