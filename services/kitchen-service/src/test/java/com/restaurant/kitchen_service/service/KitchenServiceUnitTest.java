package com.restaurant.kitchen_service.service;

import com.restaurant.common.events.KitchenOrderReadyEvent;
import com.restaurant.common.events.OrderPaidEvent;
import com.restaurant.kitchen_service.entity.Ticket;
import com.restaurant.kitchen_service.enums.TicketStatusEnum;
import com.restaurant.kitchen_service.metrics.KafkaMetrics;
import com.restaurant.kitchen_service.repository.TicketRepository;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit-тесты KitchenService")
class KitchenServiceUnitTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private KitchenRedisService redisService;

    @Mock
    private KafkaMetrics kafkaMetrics;

    @Mock
    private Timer.Sample timerSample;

    @InjectMocks
    private KitchenService kitchenService;

    private final Long TEST_ORDER_ID = 1L;
    private final Long TEST_USER_ID = 1001L;

    @BeforeEach
    void setUp() {
        lenient().when(kafkaMetrics.startProcessingTimer()).thenReturn(timerSample);
        lenient().doNothing().when(kafkaMetrics).stopProcessingTimer(any(Timer.Sample.class));
        lenient().doNothing().when(kafkaMetrics).incrementProduced();
        lenient().doNothing().when(kafkaMetrics).incrementConsumed();
        lenient().doNothing().when(kafkaMetrics).incrementOrderReceived();
        lenient().doNothing().when(kafkaMetrics).incrementOrderPreparing();
        lenient().doNothing().when(kafkaMetrics).incrementOrderReady();
        lenient().doNothing().when(kafkaMetrics).incrementCookingError();

        // Устанавливаем время готовки через reflection
        ReflectionTestUtils.setField(kitchenService, "cookingTimeMs", 100L);
    }

    // ========== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ==========

    private OrderPaidEvent createOrderPaidEvent(Long orderId, List<String> items) {
        return new OrderPaidEvent(orderId, TEST_USER_ID, items, null);
    }

    private Ticket createTicket(Long orderId, List<String> items, TicketStatusEnum status) {
        return Ticket.builder()
                .id(1L)
                .orderId(orderId)
                .items(items)
                .status(status)
                .build();
    }

    // ========== ТЕСТЫ ДЛЯ acceptOrder() ==========

    @Nested
    @DisplayName("Тесты acceptOrder() - принятие заказа")
    class AcceptOrderTests {

        @Test
        @DisplayName("Успешное принятие заказа")
        void acceptOrder_ShouldCreateTicket() {
            // GIVEN
            List<String> items = List.of("Пицца", "Паста");
            OrderPaidEvent event = createOrderPaidEvent(TEST_ORDER_ID, items);
            Ticket savedTicket = createTicket(TEST_ORDER_ID, items, TicketStatusEnum.OPEN);

            when(ticketRepository.save(any(Ticket.class))).thenReturn(savedTicket);
            when(kafkaTemplate.send(anyString(), any())).thenReturn(null);
            doNothing().when(redisService).cacheTicket(anyLong(), any());

            // WHEN
            kitchenService.acceptOrder(event);

            // THEN
            ArgumentCaptor<Ticket> ticketCaptor = ArgumentCaptor.forClass(Ticket.class);
            verify(ticketRepository).save(ticketCaptor.capture());

            Ticket saved = ticketCaptor.getValue();
            assertThat(saved.getOrderId()).isEqualTo(TEST_ORDER_ID);
            assertThat(saved.getItems()).containsExactly("Пицца", "Паста");
            assertThat(saved.getStatus()).isEqualTo(TicketStatusEnum.OPEN);

            verify(redisService).cacheTicket(eq(TEST_ORDER_ID), any(Ticket.class));
            verify(kafkaTemplate).send(eq("kitchen-service.cooking.started"), eq(TEST_ORDER_ID));
            verify(kafkaMetrics).incrementConsumed();
            verify(kafkaMetrics).incrementOrderReceived();
        }

        @Test
        @DisplayName("Принятие заказа с пустым списком блюд")
        void acceptOrder_ShouldCreateTicket_WithEmptyItems() {
            // GIVEN
            OrderPaidEvent event = createOrderPaidEvent(TEST_ORDER_ID, List.of());
            Ticket savedTicket = createTicket(TEST_ORDER_ID, List.of(), TicketStatusEnum.OPEN);

            when(ticketRepository.save(any(Ticket.class))).thenReturn(savedTicket);
            when(kafkaTemplate.send(anyString(), any())).thenReturn(null);

            // WHEN
            kitchenService.acceptOrder(event);

            // THEN
            ArgumentCaptor<Ticket> ticketCaptor = ArgumentCaptor.forClass(Ticket.class);
            verify(ticketRepository).save(ticketCaptor.capture());

            Ticket saved = ticketCaptor.getValue();
            assertThat(saved.getItems()).isEmpty();
            assertThat(saved.getStatus()).isEqualTo(TicketStatusEnum.OPEN);
        }

        @Test
        @DisplayName("Ошибка при сохранении тикета - пробрасывает исключение")
        void acceptOrder_ShouldThrowException_WhenSaveFails() {
            // GIVEN
            OrderPaidEvent event = createOrderPaidEvent(TEST_ORDER_ID, List.of("Пицца"));

            when(ticketRepository.save(any(Ticket.class)))
                    .thenThrow(new RuntimeException("DB error"));

            // WHEN & THEN
            assertThatThrownBy(() -> kitchenService.acceptOrder(event))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("DB error");

            verify(kafkaMetrics).incrementCookingError();
            verify(redisService, never()).cacheTicket(anyLong(), any());
            verify(kafkaTemplate, never()).send(anyString(), any());
        }
    }

    // ========== ТЕСТЫ ДЛЯ completeCooking() ==========

    @Nested
    @DisplayName("Тесты completeCooking() - завершение готовки")
    class CompleteCookingTests {

        @Test
        @DisplayName("Успешное завершение готовки")
        void completeCooking_ShouldChangeStatusToReady() {
            // GIVEN
            List<String> items = List.of("Пицца", "Паста");
            Ticket ticket = createTicket(TEST_ORDER_ID, items, TicketStatusEnum.OPEN);
            Ticket updatedTicket = createTicket(TEST_ORDER_ID, items, TicketStatusEnum.READY);

            when(ticketRepository.findByOrderId(TEST_ORDER_ID)).thenReturn(Optional.of(ticket));
            when(ticketRepository.save(any(Ticket.class))).thenReturn(updatedTicket);
            when(kafkaTemplate.send(anyString(), any())).thenReturn(null);
            doNothing().when(redisService).cacheTicket(anyLong(), any());

            // WHEN
            kitchenService.completeCooking(TEST_ORDER_ID);

            // THEN
            assertThat(ticket.getStatus()).isEqualTo(TicketStatusEnum.READY);
            verify(ticketRepository).save(ticket);
            verify(redisService).cacheTicket(eq(TEST_ORDER_ID), any(Ticket.class));
            verify(kafkaMetrics).incrementOrderReady();
            verify(kafkaMetrics).incrementProduced();

            ArgumentCaptor<KitchenOrderReadyEvent> eventCaptor = ArgumentCaptor.forClass(KitchenOrderReadyEvent.class);
            verify(kafkaTemplate).send(eq("kitchen-service.order.ready"), eventCaptor.capture());

            KitchenOrderReadyEvent event = eventCaptor.getValue();
            assertThat(event.getOrderId()).isEqualTo(TEST_ORDER_ID);

            assertThat(event.getStatus()).isEqualTo("READY");
        }

        @Test
        @DisplayName("Завершение готовки - тикет не найден")
        void completeCooking_ShouldThrowException_WhenTicketNotFound() {
            // GIVEN
            Long nonExistentOrderId = 999L;
            when(ticketRepository.findByOrderId(nonExistentOrderId)).thenReturn(Optional.empty());

            // WHEN & THEN
            assertThatThrownBy(() -> kitchenService.completeCooking(nonExistentOrderId))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Тикет не найден");

            verify(ticketRepository, never()).save(any(Ticket.class));
            verify(kafkaMetrics).incrementCookingError();
        }
    }

    // ========== ТЕСТЫ ДЛЯ cancelOrder() ==========

    @Nested
    @DisplayName("Тесты cancelOrder() - отмена заказа")
    class CancelOrderTests {

        @Test
        @DisplayName("Успешная отмена заказа")
        void cancelOrder_ShouldChangeStatusToCancelled() {
            // GIVEN
            Ticket ticket = createTicket(TEST_ORDER_ID, List.of("Пицца"), TicketStatusEnum.OPEN);

            when(ticketRepository.findByOrderId(TEST_ORDER_ID)).thenReturn(Optional.of(ticket));
            when(ticketRepository.save(any(Ticket.class))).thenReturn(ticket);
            when(kafkaTemplate.send(anyString(), any())).thenReturn(null);
            doNothing().when(redisService).evictTicket(anyLong());

            // WHEN
            kitchenService.cancelOrder(TEST_ORDER_ID);

            // THEN
            assertThat(ticket.getStatus()).isEqualTo(TicketStatusEnum.CANCELLED);
            verify(ticketRepository).save(ticket);
            verify(redisService).evictTicket(TEST_ORDER_ID);
            verify(kafkaTemplate).send(eq("kitchen-service.order.cancelled"), eq(TEST_ORDER_ID));
        }

        @Test
        @DisplayName("Отмена заказа - тикет не найден")
        void cancelOrder_ShouldNotThrow_WhenTicketNotFound() {
            // GIVEN
            when(ticketRepository.findByOrderId(TEST_ORDER_ID)).thenReturn(Optional.empty());

            // WHEN
            kitchenService.cancelOrder(TEST_ORDER_ID);

            // THEN
            verify(ticketRepository, never()).save(any(Ticket.class));
            verify(redisService, never()).evictTicket(anyLong());
        }

        @Test
        @DisplayName("Отмена заказа - нельзя отменить готовый заказ")
        void cancelOrder_ShouldNotCancel_WhenAlreadyReady() {
            // GIVEN
            Ticket ticket = createTicket(TEST_ORDER_ID, List.of("Пицца"), TicketStatusEnum.READY);

            when(ticketRepository.findByOrderId(TEST_ORDER_ID)).thenReturn(Optional.of(ticket));

            // WHEN
            kitchenService.cancelOrder(TEST_ORDER_ID);

            // THEN
            assertThat(ticket.getStatus()).isEqualTo(TicketStatusEnum.READY);
            verify(ticketRepository, never()).save(any(Ticket.class));
            verify(redisService, never()).evictTicket(anyLong());
            verify(kafkaTemplate, never()).send(eq("kitchen-service.order.cancelled"), any());
        }
    }

    // ========== ТЕСТЫ ДЛЯ updateTicketStatusTransactional() ==========

    @Nested
    @DisplayName("Тесты updateTicketStatusTransactional()")
    class UpdateTicketStatusTransactionalTests {

        @Test
        @DisplayName("Обновление статуса тикета")
        void updateTicketStatusTransactional_ShouldUpdateStatus() {
            // GIVEN
            Ticket ticket = createTicket(TEST_ORDER_ID, List.of("Пицца"), TicketStatusEnum.OPEN);

            when(ticketRepository.findByOrderId(TEST_ORDER_ID)).thenReturn(Optional.of(ticket));
            when(ticketRepository.save(any(Ticket.class))).thenReturn(ticket);
            doNothing().when(redisService).cacheTicket(anyLong(), any());

            // WHEN
            Ticket result = kitchenService.updateTicketStatusTransactional(TEST_ORDER_ID, TicketStatusEnum.IN_PROGRESS);

            // THEN
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(TicketStatusEnum.IN_PROGRESS);
            verify(ticketRepository).save(ticket);
            verify(redisService).cacheTicket(eq(TEST_ORDER_ID), any(Ticket.class));
        }

        @Test
        @DisplayName("Обновление статуса с пустым списком блюд - сразу READY")
        void updateTicketStatusTransactional_ShouldSetReady_WhenItemsEmpty() {
            // GIVEN
            Ticket ticket = createTicket(TEST_ORDER_ID, List.of(), TicketStatusEnum.OPEN);

            when(ticketRepository.findByOrderId(TEST_ORDER_ID)).thenReturn(Optional.of(ticket));
            when(ticketRepository.save(any(Ticket.class))).thenReturn(ticket);
            doNothing().when(redisService).cacheTicket(anyLong(), any());

            // WHEN
            Ticket result = kitchenService.updateTicketStatusTransactional(TEST_ORDER_ID, TicketStatusEnum.IN_PROGRESS);

            // THEN
            assertThat(result).isNull();
            assertThat(ticket.getStatus()).isEqualTo(TicketStatusEnum.READY);
            verify(ticketRepository).save(ticket);
            verify(redisService).cacheTicket(eq(TEST_ORDER_ID), any(Ticket.class));
        }

        @Test
        @DisplayName("Обновление статуса - тикет не найден")
        void updateTicketStatusTransactional_ShouldThrowException_WhenTicketNotFound() {
            // GIVEN
            when(ticketRepository.findByOrderId(TEST_ORDER_ID)).thenReturn(Optional.empty());

            // WHEN & THEN
            assertThatThrownBy(() -> kitchenService.updateTicketStatusTransactional(TEST_ORDER_ID, TicketStatusEnum.IN_PROGRESS))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Тикет не найден");

            verify(ticketRepository, never()).save(any(Ticket.class));
        }
    }

    // ========== ТЕСТЫ ДЛЯ rollbackCooking() ==========

    @Nested
    @DisplayName("Тесты rollbackCooking() - откат готовки")
    class RollbackCookingTests {

        @Test
        @DisplayName("Откат готовки - возвращает статус в OPEN")
        void rollbackCooking_ShouldRevertToOpen() {
            // GIVEN
            Ticket ticket = createTicket(TEST_ORDER_ID, List.of("Пицца"), TicketStatusEnum.IN_PROGRESS);

            when(ticketRepository.findByOrderId(TEST_ORDER_ID)).thenReturn(Optional.of(ticket));
            when(ticketRepository.save(any(Ticket.class))).thenReturn(ticket);
            doNothing().when(redisService).cacheTicket(anyLong(), any());

            // WHEN
            kitchenService.rollbackCooking(TEST_ORDER_ID);

            // THEN
            assertThat(ticket.getStatus()).isEqualTo(TicketStatusEnum.OPEN);
            verify(ticketRepository).save(ticket);
            verify(redisService).cacheTicket(eq(TEST_ORDER_ID), any(Ticket.class));
        }

        @Test
        @DisplayName("Откат готовки - не меняет статус, если не IN_PROGRESS")
        void rollbackCooking_ShouldNotChangeStatus_WhenNotInProgress() {
            // GIVEN
            Ticket ticket = createTicket(TEST_ORDER_ID, List.of("Пицца"), TicketStatusEnum.READY);

            when(ticketRepository.findByOrderId(TEST_ORDER_ID)).thenReturn(Optional.of(ticket));

            // WHEN
            kitchenService.rollbackCooking(TEST_ORDER_ID);

            // THEN
            assertThat(ticket.getStatus()).isEqualTo(TicketStatusEnum.READY);
            verify(ticketRepository, never()).save(any(Ticket.class));
            verify(redisService, never()).cacheTicket(anyLong(), any());
        }
    }

    // ========== ТЕСТЫ ДЛЯ getTicketByOrderId() ==========

    @Nested
    @DisplayName("Тесты getTicketByOrderId()")
    class GetTicketByOrderIdTests {

        @Test
        @DisplayName("Возвращает тикет из Redis")
        void getTicketByOrderId_ShouldReturnFromRedis() {
            // GIVEN
            Ticket ticket = createTicket(TEST_ORDER_ID, List.of("Пицца"), TicketStatusEnum.OPEN);

            when(redisService.getCachedTicket(TEST_ORDER_ID)).thenReturn(Optional.of(ticket));

            // WHEN
            Ticket result = kitchenService.getTicketByOrderId(TEST_ORDER_ID);

            // THEN
            assertThat(result).isNotNull();
            assertThat(result.getOrderId()).isEqualTo(TEST_ORDER_ID);
            verify(ticketRepository, never()).findByOrderId(anyLong());
        }

        @Test
        @DisplayName("Загружает из БД, если в Redis нет")
        void getTicketByOrderId_ShouldLoadFromDatabase_WhenNotInRedis() {
            // GIVEN
            Ticket ticket = createTicket(TEST_ORDER_ID, List.of("Паста"), TicketStatusEnum.OPEN);

            when(redisService.getCachedTicket(TEST_ORDER_ID)).thenReturn(Optional.empty());
            when(ticketRepository.findByOrderId(TEST_ORDER_ID)).thenReturn(Optional.of(ticket));
            doNothing().when(redisService).cacheTicket(anyLong(), any());

            // WHEN
            Ticket result = kitchenService.getTicketByOrderId(TEST_ORDER_ID);

            // THEN
            assertThat(result).isNotNull();
            assertThat(result.getOrderId()).isEqualTo(TEST_ORDER_ID);
            verify(ticketRepository).findByOrderId(TEST_ORDER_ID);
            verify(redisService).cacheTicket(eq(TEST_ORDER_ID), any(Ticket.class));
        }

        @Test
        @DisplayName("Выбрасывает исключение, если тикет не найден")
        void getTicketByOrderId_ShouldThrowException_WhenNotFound() {
            // GIVEN
            when(redisService.getCachedTicket(TEST_ORDER_ID)).thenReturn(Optional.empty());
            when(ticketRepository.findByOrderId(TEST_ORDER_ID)).thenReturn(Optional.empty());

            // WHEN & THEN
            assertThatThrownBy(() -> kitchenService.getTicketByOrderId(TEST_ORDER_ID))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Тикет не найден");
        }

        @Test
        @DisplayName("Очищает Redis при ошибке приведения типа")
        void getTicketByOrderId_ShouldEvictCache_OnClassCastException() {
            // GIVEN
            when(redisService.getCachedTicket(TEST_ORDER_ID)).thenReturn(Optional.of("неправильный объект"));
            doNothing().when(redisService).evictTicket(TEST_ORDER_ID);

            Ticket ticket = createTicket(TEST_ORDER_ID, List.of("Пицца"), TicketStatusEnum.OPEN);
            when(ticketRepository.findByOrderId(TEST_ORDER_ID)).thenReturn(Optional.of(ticket));
            doNothing().when(redisService).cacheTicket(anyLong(), any());

            // WHEN
            Ticket result = kitchenService.getTicketByOrderId(TEST_ORDER_ID);

            // THEN
            assertThat(result).isNotNull();
            verify(redisService).evictTicket(TEST_ORDER_ID);
            verify(ticketRepository).findByOrderId(TEST_ORDER_ID);
        }
    }

    // ========== ТЕСТЫ ДЛЯ CRUD МЕТОДОВ ==========

    @Nested
    @DisplayName("Тесты CRUD методов")
    class CrudTests {

        @Test
        @DisplayName("getAllTickets() - возвращает все тикеты")
        void getAllTickets_ShouldReturnAllTickets() {
            // GIVEN
            List<Ticket> tickets = List.of(
                    createTicket(1L, List.of("Пицца"), TicketStatusEnum.OPEN),
                    createTicket(2L, List.of("Паста"), TicketStatusEnum.READY)
            );
            when(ticketRepository.findAll()).thenReturn(tickets);

            // WHEN
            List<Ticket> result = kitchenService.getAllTickets();

            // THEN
            assertThat(result).hasSize(2);
            verify(ticketRepository).findAll();
        }

        @Test
        @DisplayName("getTicketById() - возвращает тикет по ID")
        void getTicketById_ShouldReturnTicket() {
            // GIVEN
            Ticket ticket = createTicket(TEST_ORDER_ID, List.of("Пицца"), TicketStatusEnum.OPEN);
            when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));

            // WHEN
            Ticket result = kitchenService.getTicketById(1L);

            // THEN
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            verify(ticketRepository).findById(1L);
        }

        @Test
        @DisplayName("getTicketById() - выбрасывает исключение, если тикет не найден")
        void getTicketById_ShouldThrowException_WhenNotFound() {
            // GIVEN
            Long nonExistentId = 999L;
            when(ticketRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            // WHEN & THEN
            assertThatThrownBy(() -> kitchenService.getTicketById(nonExistentId))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Тикет не найден");
        }

        @Test
        @DisplayName("getTicketsByStatus() - возвращает тикеты по статусу")
        void getTicketsByStatus_ShouldReturnTicketsByStatus() {
            // GIVEN
            List<Ticket> tickets = List.of(
                    createTicket(1L, List.of("Пицца"), TicketStatusEnum.OPEN),
                    createTicket(2L, List.of("Паста"), TicketStatusEnum.OPEN)
            );
            when(ticketRepository.findByStatus(TicketStatusEnum.OPEN)).thenReturn(tickets);

            // WHEN
            List<Ticket> result = kitchenService.getTicketsByStatus(TicketStatusEnum.OPEN);

            // THEN
            assertThat(result).hasSize(2);
            assertThat(result).allMatch(t -> t.getStatus() == TicketStatusEnum.OPEN);
            verify(ticketRepository).findByStatus(TicketStatusEnum.OPEN);
        }

        @Test
        @DisplayName("getOpenTickets() - возвращает открытые тикеты")
        void getOpenTickets_ShouldReturnOpenTickets() {
            // GIVEN
            List<Ticket> tickets = List.of(
                    createTicket(1L, List.of("Пицца"), TicketStatusEnum.OPEN)
            );
            when(ticketRepository.findByStatus(TicketStatusEnum.OPEN)).thenReturn(tickets);

            // WHEN
            List<Ticket> result = kitchenService.getOpenTickets();

            // THEN
            assertThat(result).hasSize(1);
            assertThat(result).allMatch(t -> t.getStatus() == TicketStatusEnum.OPEN);
        }

        @Test
        @DisplayName("getInProgressTickets() - возвращает тикеты в работе")
        void getInProgressTickets_ShouldReturnInProgressTickets() {
            // GIVEN
            List<Ticket> tickets = List.of(
                    createTicket(1L, List.of("Пицца"), TicketStatusEnum.IN_PROGRESS)
            );
            when(ticketRepository.findByStatus(TicketStatusEnum.IN_PROGRESS)).thenReturn(tickets);

            // WHEN
            List<Ticket> result = kitchenService.getInProgressTickets();

            // THEN
            assertThat(result).hasSize(1);
            assertThat(result).allMatch(t -> t.getStatus() == TicketStatusEnum.IN_PROGRESS);
        }

        @Test
        @DisplayName("getReadyTickets() - возвращает готовые тикеты")
        void getReadyTickets_ShouldReturnReadyTickets() {
            // GIVEN
            List<Ticket> tickets = List.of(
                    createTicket(1L, List.of("Пицца"), TicketStatusEnum.READY)
            );
            when(ticketRepository.findByStatus(TicketStatusEnum.READY)).thenReturn(tickets);

            // WHEN
            List<Ticket> result = kitchenService.getReadyTickets();

            // THEN
            assertThat(result).hasSize(1);
            assertThat(result).allMatch(t -> t.getStatus() == TicketStatusEnum.READY);
        }

        @Test
        @DisplayName("getItemsListForTicketId() - возвращает список блюд по ID тикета")
        void getItemsListForTicketId_ShouldReturnItems() {
            // GIVEN
            List<String> items = List.of("Пицца", "Паста");
            Ticket ticket = createTicket(1L, items, TicketStatusEnum.OPEN);
            when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));

            // WHEN
            List<String> result = kitchenService.getItemsListForTicketId(1L);

            // THEN
            assertThat(result).containsExactly("Пицца", "Паста");
        }

        @Test
        @DisplayName("getItemsListForOrderId() - возвращает список блюд по ID заказа")
        void getItemsListForOrderId_ShouldReturnItems() {
            // GIVEN
            List<String> items = List.of("Пицца", "Паста");
            Ticket ticket = createTicket(TEST_ORDER_ID, items, TicketStatusEnum.OPEN);

            when(ticketRepository.findByOrderId(TEST_ORDER_ID)).thenReturn(Optional.of(ticket));

            // WHEN
            List<String> result = kitchenService.getItemsListForOrderId(TEST_ORDER_ID);

            // THEN
            assertThat(result).containsExactly("Пицца", "Паста");
        }

        @Test
        @DisplayName("isCookingComplete() - проверяет статус готовки")
        void isCookingComplete_ShouldReturnCorrectStatus() {
            // GIVEN
            Ticket ticket = createTicket(TEST_ORDER_ID, List.of("Пицца"), TicketStatusEnum.READY);
            when(ticketRepository.findByOrderId(TEST_ORDER_ID)).thenReturn(Optional.of(ticket));

            // WHEN
            boolean result = kitchenService.isCookingComplete(TEST_ORDER_ID);

            // THEN
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("isCookingComplete() - возвращает false, если тикет не найден")
        void isCookingComplete_ShouldReturnFalse_WhenTicketNotFound() {
            // GIVEN
            when(ticketRepository.findByOrderId(TEST_ORDER_ID)).thenReturn(Optional.empty());

            // WHEN
            boolean result = kitchenService.isCookingComplete(TEST_ORDER_ID);

            // THEN
            assertThat(result).isFalse();
        }
    }

    // ========== ТЕСТЫ ДЛЯ processQueue() ==========

    @Nested
    @DisplayName("Тесты processQueue() - обработка очереди")
    class ProcessQueueTests {

        @Test
        @DisplayName("Обрабатывает заказ из очереди")
        void processQueue_ShouldProcessOrder() throws Exception {
            // GIVEN
            // Используем рефлексию для доступа к приватному полю orderQueue
            ConcurrentLinkedQueue<Long> queue = new ConcurrentLinkedQueue<>();
            queue.offer(TEST_ORDER_ID);
            ReflectionTestUtils.setField(kitchenService, "orderQueue", queue);

            // Мокаем startCookingAsync через spy
            KitchenService spyService = spy(kitchenService);
            doNothing().when(spyService).startCookingAsync(TEST_ORDER_ID);

            // WHEN
            spyService.processQueue();

            // THEN
            verify(spyService).startCookingAsync(TEST_ORDER_ID);
            assertThat(queue).isEmpty();
        }

        @Test
        @DisplayName("processQueue - ничего не делает, если очередь пуста")
        void processQueue_ShouldDoNothing_WhenQueueEmpty() {
            // GIVEN
            ConcurrentLinkedQueue<Long> queue = new ConcurrentLinkedQueue<>();
            ReflectionTestUtils.setField(kitchenService, "orderQueue", queue);

            KitchenService spyService = spy(kitchenService);

            // WHEN
            spyService.processQueue();

            // THEN
            verify(spyService, never()).startCookingAsync(anyLong());
        }
    }
}