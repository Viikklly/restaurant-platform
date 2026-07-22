package com.restaurant.kitchen_service.controller;

import com.restaurant.kitchen_service.entity.Ticket;
import com.restaurant.kitchen_service.enums.TicketStatusEnum;
import com.restaurant.kitchen_service.service.KitchenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit-тесты KitchenController")
class KitchenControllerUnitTest {

    @Mock
    private KitchenService kitchenService;

    @InjectMocks
    private KitchenController kitchenController;

    private final Long TEST_ORDER_ID = 1L;
    private final Long TEST_TICKET_ID = 1L;

    private Ticket createTicket(Long id, Long orderId, List<String> items, TicketStatusEnum status) {
        return Ticket.builder()
                .id(id)
                .orderId(orderId)
                .items(items)
                .status(status)
                .build();
    }

    @Test
    @DisplayName("GET /api/kitchen/tickets - возвращает все тикеты")
    void getAllTickets_ShouldReturnAllTickets() {

        List<Ticket> tickets = List.of(
                createTicket(1L, 1L, List.of("Пицца"), TicketStatusEnum.OPEN),
                createTicket(2L, 2L, List.of("Паста"), TicketStatusEnum.READY)
        );
        when(kitchenService.getAllTickets()).thenReturn(tickets);


        ResponseEntity<List<Ticket>> response = kitchenController.getAllTickets();

        /// Проверки
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
        verify(kitchenService).getAllTickets();
    }

    @Test
    @DisplayName("GET /api/kitchen/tickets/{id} - возвращает тикет по ID")
    void getTicketById_ShouldReturnTicket() {

        Ticket ticket = createTicket(TEST_TICKET_ID, TEST_ORDER_ID, List.of("Пицца"), TicketStatusEnum.OPEN);
        when(kitchenService.getTicketById(TEST_TICKET_ID)).thenReturn(ticket);


        ResponseEntity<Ticket> response = kitchenController.getTicketById(TEST_TICKET_ID);

        /// Проверки
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(TEST_TICKET_ID);
        verify(kitchenService).getTicketById(TEST_TICKET_ID);
    }

    @Test
    @DisplayName("GET /api/kitchen/tickets/order/{orderId}/items - возвращает тикет по ID заказа")
    void getTicketByOrderId_ShouldReturnTicket() {

        Ticket ticket = createTicket(TEST_TICKET_ID, TEST_ORDER_ID, List.of("Пицца", "Паста"), TicketStatusEnum.OPEN);
        when(kitchenService.getTicketByOrderId(TEST_ORDER_ID)).thenReturn(ticket);


        ResponseEntity<Ticket> response = kitchenController.getTicketByOrderId(TEST_ORDER_ID);

        /// Проверки
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getOrderId()).isEqualTo(TEST_ORDER_ID);
        verify(kitchenService).getTicketByOrderId(TEST_ORDER_ID);
    }

    @Test
    @DisplayName("GET /api/kitchen/tickets/status/{status} - возвращает тикеты по статусу")
    void getTicketsByStatus_ShouldReturnTicketsByStatus() {

        List<Ticket> tickets = List.of(
                createTicket(1L, 1L, List.of("Пицца"), TicketStatusEnum.OPEN),
                createTicket(2L, 2L, List.of("Паста"), TicketStatusEnum.OPEN)
        );
        when(kitchenService.getTicketsByStatus(TicketStatusEnum.OPEN)).thenReturn(tickets);


        ResponseEntity<List<Ticket>> response = kitchenController.getTicketsByStatus("OPEN");

        /// Проверки
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
        assertThat(response.getBody()).allMatch(t -> t.getStatus() == TicketStatusEnum.OPEN);
        verify(kitchenService).getTicketsByStatus(TicketStatusEnum.OPEN);
    }

    @Test
    @DisplayName("GET /api/kitchen/tickets/status/open - возвращает открытые тикеты")
    void getOpenTickets_ShouldReturnOpenTickets() {

        List<Ticket> tickets = List.of(
                createTicket(1L, 1L, List.of("Пицца"), TicketStatusEnum.OPEN)
        );
        when(kitchenService.getOpenTickets()).thenReturn(tickets);


        ResponseEntity<List<Ticket>> response = kitchenController.getOpenTickets();

        /// Проверки
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody()).allMatch(t -> t.getStatus() == TicketStatusEnum.OPEN);
        verify(kitchenService).getOpenTickets();
    }

    @Test
    @DisplayName("GET /api/kitchen/tickets/status/in-progress - возвращает тикеты в работе")
    void getInProgressTickets_ShouldReturnInProgressTickets() {

        List<Ticket> tickets = List.of(
                createTicket(1L, 1L, List.of("Пицца"), TicketStatusEnum.IN_PROGRESS)
        );
        when(kitchenService.getInProgressTickets()).thenReturn(tickets);


        ResponseEntity<List<Ticket>> response = kitchenController.getInProgressTickets();

        /// Проверки
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody()).allMatch(t -> t.getStatus() == TicketStatusEnum.IN_PROGRESS);
        verify(kitchenService).getInProgressTickets();
    }

    @Test
    @DisplayName("GET /api/kitchen/tickets/status/ready - возвращает готовые тикеты")
    void getReadyTickets_ShouldReturnReadyTickets() {

        List<Ticket> tickets = List.of(
                createTicket(1L, 1L, List.of("Пицца"), TicketStatusEnum.READY)
        );
        when(kitchenService.getReadyTickets()).thenReturn(tickets);


        ResponseEntity<List<Ticket>> response = kitchenController.getReadyTickets();

        /// Проверки
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody()).allMatch(t -> t.getStatus() == TicketStatusEnum.READY);
        verify(kitchenService).getReadyTickets();
    }

    @Test
    @DisplayName("GET /api/kitchen/tickets/{ticketId}/items - возвращает список блюд по ID тикета")
    void getItemsListByTicketId_ShouldReturnItems() {

        List<String> items = List.of("Пицца", "Паста", "Салат");
        when(kitchenService.getItemsListForTicketId(TEST_TICKET_ID)).thenReturn(items);


        ResponseEntity<List<String>> response = kitchenController.getItemsListByTicketId(TEST_TICKET_ID);

        /// Проверки
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(3);
        assertThat(response.getBody()).containsExactly("Пицца", "Паста", "Салат");
        verify(kitchenService).getItemsListForTicketId(TEST_TICKET_ID);
    }

    @Test
    @DisplayName("GET /api/kitchen/tickets/status/{status} - выбрасывает исключение при неверном статусе")
    void getTicketsByStatus_ShouldThrowException_WhenInvalidStatus() {

        assertThatThrownBy(() -> kitchenController.getTicketsByStatus("INVALID"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(kitchenService, never()).getTicketsByStatus(any());
    }
}