package com.restaurant.kitchen_service.service;

import com.restaurant.kitchen_service.entity.Ticket;
import com.restaurant.kitchen_service.enums.TicketStatusEnum;
import com.restaurant.kitchen_service.metrics.KafkaMetrics;
import com.restaurant.kitchen_service.repository.TicketRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Это отдельный бин для транзакционных операций с тикетами.
 * Здесь тестируем логику процессора (без Spring-транзакций).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Unit-тесты CookingProcessor")
class CookingProcessorUnitTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private KitchenRedisService redisService;

    @Mock
    private KafkaMetrics kafkaMetrics;

    @InjectMocks
    private CookingProcessor cookingProcessor;

    private final Long TEST_ORDER_ID = 1L;

    /// Вспомогательные методы

    private Ticket createTicket(Long orderId, List<String> items, TicketStatusEnum status) {
        return Ticket.builder()
                .id(1L)
                .orderId(orderId)
                .items(items)
                .status(status)
                .build();
    }

    ///saveTicket()

    @Nested
    @DisplayName("Тесты saveTicket() - сохранение нового тикета")
    class SaveTicketTests {

        @Test
        @DisplayName("Сохраняет тикет")
        void saveTicket_ShouldSave() {
            /// тикет для сохранения
            Ticket ticket = createTicket(TEST_ORDER_ID, List.of("Пицца"), TicketStatusEnum.OPEN);

            when(ticketRepository.save(ticket)).thenReturn(ticket);

            /// сохраняем
            Ticket result = cookingProcessor.saveTicket(ticket);

            /// тикет сохранён
            assertThat(result).isNotNull();
            assertThat(result.getOrderId()).isEqualTo(TEST_ORDER_ID);
            verify(ticketRepository).save(ticket);
        }
    }

    // updateTicketStatus()

    @Nested
    @DisplayName("Тесты updateTicketStatus() - обновление статуса")
    class UpdateTicketStatusTests {

        @Test
        @DisplayName("Обновляет статус на IN_PROGRESS")
        void updateTicketStatus_ShouldSetInProgress() {
            /// тикет со статусом OPEN
            Ticket ticket = createTicket(TEST_ORDER_ID, List.of("Пицца"), TicketStatusEnum.OPEN);
            Ticket updated = createTicket(TEST_ORDER_ID, List.of("Пицца"), TicketStatusEnum.IN_PROGRESS);

            when(ticketRepository.findByOrderId(TEST_ORDER_ID)).thenReturn(Optional.of(ticket));
            when(ticketRepository.save(any(Ticket.class))).thenReturn(updated);
            doNothing().when(redisService).cacheTicket(anyLong(), any());

            /// обновляем статус
            Ticket result = cookingProcessor.updateTicketStatus(TEST_ORDER_ID, TicketStatusEnum.IN_PROGRESS);

            /// статус изменён
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(TicketStatusEnum.IN_PROGRESS);
            verify(ticketRepository).save(ticket);
            verify(redisService).cacheTicket(eq(TEST_ORDER_ID), any(Ticket.class));
        }

        @Test
        @DisplayName("Обновление статуса с пустым списком блюд - сразу READY")
        void updateTicketStatus_ShouldSetReady_WhenItemsEmpty() {
            /// тикет с пустым списком блюд
            Ticket ticket = createTicket(TEST_ORDER_ID, List.of(), TicketStatusEnum.OPEN);
            Ticket ready = createTicket(TEST_ORDER_ID, List.of(), TicketStatusEnum.READY);

            when(ticketRepository.findByOrderId(TEST_ORDER_ID)).thenReturn(Optional.of(ticket));
            when(ticketRepository.save(any(Ticket.class))).thenReturn(ready);
            doNothing().when(redisService).cacheTicket(anyLong(), any());

            /// обновляем статус
            Ticket result = cookingProcessor.updateTicketStatus(TEST_ORDER_ID, TicketStatusEnum.IN_PROGRESS);

            /// статус сразу READY (не null!)
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(TicketStatusEnum.READY);
            verify(redisService).cacheTicket(eq(TEST_ORDER_ID), any(Ticket.class));
        }

        @Test
        @DisplayName("Обновление статуса - тикет не найден")
        void updateTicketStatus_ShouldThrowException_WhenTicketNotFound() {
            when(ticketRepository.findByOrderId(TEST_ORDER_ID)).thenReturn(Optional.empty());

            /// выбрасывается исключение
            assertThatThrownBy(() ->
                    cookingProcessor.updateTicketStatus(TEST_ORDER_ID, TicketStatusEnum.IN_PROGRESS))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Тикет не найден");

            verify(ticketRepository, never()).save(any(Ticket.class));
        }
    }

    ///completeCooking()

    @Nested
    @DisplayName("Тесты completeCooking() - завершение готовки")
    class CompleteCookingTests {

        @Test
        @DisplayName("Меняет статус на READY")
        void completeCooking_ShouldSetReady() {
            /// тикет в процессе готовки
            Ticket ticket = createTicket(TEST_ORDER_ID, List.of("Пицца"), TicketStatusEnum.IN_PROGRESS);
            Ticket ready = createTicket(TEST_ORDER_ID, List.of("Пицца"), TicketStatusEnum.READY);

            when(ticketRepository.findByOrderId(TEST_ORDER_ID)).thenReturn(Optional.of(ticket));
            when(ticketRepository.save(any(Ticket.class))).thenReturn(ready);
            doNothing().when(redisService).cacheTicket(anyLong(), any());

            /// завершаем готовку
            Ticket result = cookingProcessor.completeCooking(TEST_ORDER_ID);

            /// статус изменён на READY
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(TicketStatusEnum.READY);
            verify(kafkaMetrics).incrementOrderReady();
            verify(redisService).cacheTicket(eq(TEST_ORDER_ID), any(Ticket.class));
        }

        @Test
        @DisplayName("Завершение готовки - тикет не найден")
        void completeCooking_ShouldThrowException_WhenTicketNotFound() {
            when(ticketRepository.findByOrderId(TEST_ORDER_ID)).thenReturn(Optional.empty());

            /// выбрасывается исключение
            assertThatThrownBy(() -> cookingProcessor.completeCooking(TEST_ORDER_ID))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Тикет не найден");
        }
    }

    /// rollbackCooking()

    @Nested
    @DisplayName("Тесты rollbackCooking() - откат готовки")
    class RollbackCookingTests {

        @Test
        @DisplayName("Возвращает статус в OPEN")
        void rollbackCooking_ShouldRevertToOpen() {
            /// тикет в процессе готовки
            Ticket ticket = createTicket(TEST_ORDER_ID, List.of("Пицца"), TicketStatusEnum.IN_PROGRESS);

            when(ticketRepository.findByOrderId(TEST_ORDER_ID)).thenReturn(Optional.of(ticket));
            when(ticketRepository.save(any(Ticket.class))).thenReturn(ticket);
            doNothing().when(redisService).cacheTicket(anyLong(), any());

            /// откат готовки
            cookingProcessor.rollbackCooking(TEST_ORDER_ID);

            /// статус возвращён в OPEN
            assertThat(ticket.getStatus()).isEqualTo(TicketStatusEnum.OPEN);
            verify(ticketRepository).save(ticket);
            verify(redisService).cacheTicket(eq(TEST_ORDER_ID), any(Ticket.class));
        }

        @Test
        @DisplayName("Не меняет статус, если не IN_PROGRESS")
        void rollbackCooking_ShouldNotChangeStatus_WhenNotInProgress() {
            /// готовый тикет
            Ticket ticket = createTicket(TEST_ORDER_ID, List.of("Пицца"), TicketStatusEnum.READY);

            when(ticketRepository.findByOrderId(TEST_ORDER_ID)).thenReturn(Optional.of(ticket));

            /// откат готовки
            cookingProcessor.rollbackCooking(TEST_ORDER_ID);

            /// статус не изменился
            assertThat(ticket.getStatus()).isEqualTo(TicketStatusEnum.READY);
            verify(ticketRepository, never()).save(any(Ticket.class));
            verify(redisService, never()).cacheTicket(anyLong(), any());
        }

        @Test
        @DisplayName("Тикет не найден - ничего не делаем")
        void rollbackCooking_ShouldDoNothing_WhenTicketNotFound() {
            when(ticketRepository.findByOrderId(TEST_ORDER_ID)).thenReturn(Optional.empty());

            /// откат готовки
            cookingProcessor.rollbackCooking(TEST_ORDER_ID);

            /// save не вызывается
            verify(ticketRepository, never()).save(any(Ticket.class));
        }
    }

    ///cancelTicket()

    @Nested
    @DisplayName("Тесты cancelTicket() - отмена заказа")
    class CancelTicketTests {

        @Test
        @DisplayName("Отменяет заказ")
        void cancelTicket_ShouldCancel() {
            /// тикет со статусом OPEN
            Ticket ticket = createTicket(TEST_ORDER_ID, List.of("Пицца"), TicketStatusEnum.OPEN);

            when(ticketRepository.findByOrderId(TEST_ORDER_ID)).thenReturn(Optional.of(ticket));
            when(ticketRepository.save(any(Ticket.class))).thenReturn(ticket);
            doNothing().when(redisService).evictTicket(TEST_ORDER_ID);

            /// отменяем
            boolean result = cookingProcessor.cancelTicket(TEST_ORDER_ID);

            /// заказ отменён
            assertThat(result).isTrue();
            assertThat(ticket.getStatus()).isEqualTo(TicketStatusEnum.CANCELLED);
            verify(ticketRepository).save(ticket);
            verify(redisService).evictTicket(TEST_ORDER_ID);
        }

        @Test
        @DisplayName("Нельзя отменить готовый заказ")
        void cancelTicket_ShouldNotCancel_WhenAlreadyReady() {
            /// готовый тикет
            Ticket ticket = createTicket(TEST_ORDER_ID, List.of("Пицца"), TicketStatusEnum.READY);

            when(ticketRepository.findByOrderId(TEST_ORDER_ID)).thenReturn(Optional.of(ticket));

            /// пытаемся отменить
            boolean result = cookingProcessor.cancelTicket(TEST_ORDER_ID);

            /// отмена невозможна
            assertThat(result).isFalse();
            assertThat(ticket.getStatus()).isEqualTo(TicketStatusEnum.READY);
            verify(ticketRepository, never()).save(any(Ticket.class));
            verify(redisService, never()).evictTicket(anyLong());
        }

        @Test
        @DisplayName("Тикет не найден - возвращает false")
        void cancelTicket_ShouldReturnFalse_WhenTicketNotFound() {
            when(ticketRepository.findByOrderId(TEST_ORDER_ID)).thenReturn(Optional.empty());

            /// пытаемся отменить
            boolean result = cookingProcessor.cancelTicket(TEST_ORDER_ID);

            /// отмена невозможна
            assertThat(result).isFalse();
            verify(ticketRepository, never()).save(any(Ticket.class));
        }
    }
}