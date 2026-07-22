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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.cloud.config.enabled=false",
        "spring.kafka.bootstrap-servers=",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
        "spring.jpa.hibernate.ddl-auto=update",
        "kitchen.cooking-time-ms=1000"
})
@Testcontainers
@DisplayName("Интеграционные тесты KitchenService (БД + Redis)")
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class KitchenServiceIntegrationTest {

    @MockitoBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @MockitoBean
    private KafkaMetrics kafkaMetrics;

    @MockitoBean
    private Timer.Sample timerSample;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("kitchen_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Autowired
    private KitchenService kitchenService;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private KitchenRedisService redisService;

    @Autowired
    private CacheManager cacheManager;

    @Value("${kitchen.cooking-time-ms:1000}")
    private long cookingTimeMs;

    @BeforeEach
    void setUp() {
        cacheManager.getCacheNames().stream()
                .forEach(name -> cacheManager.getCache(name).clear());

        // Очищаем Redis
        redisService.evictTicket(1L);
        redisService.evictTicket(2L);
        redisService.evictTicket(3L);

        when(kafkaTemplate.send(anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        when(kafkaMetrics.startProcessingTimer()).thenReturn(timerSample);
        doNothing().when(kafkaMetrics).stopProcessingTimer(any(Timer.Sample.class));
        doNothing().when(kafkaMetrics).incrementProduced();
        doNothing().when(kafkaMetrics).incrementConsumed();
        doNothing().when(kafkaMetrics).incrementOrderReceived();
        doNothing().when(kafkaMetrics).incrementOrderPreparing();
        doNothing().when(kafkaMetrics).incrementOrderReady();
        doNothing().when(kafkaMetrics).incrementCookingError();
    }

    private OrderPaidEvent createOrderPaidEvent(Long orderId, List<String> items) {
        return new OrderPaidEvent(orderId, 1001L, items, null);
    }


    @Test
    @DisplayName("acceptOrder() - принимает заказ и создаёт тикет")
    void acceptOrder_ShouldCreateTicket() {
        /// событие оплаченного заказа
        List<String> items = List.of("Пицца", "Паста");
        OrderPaidEvent event = createOrderPaidEvent(1L, items);

        /// принимаем заказ
        kitchenService.acceptOrder(event);

        /// тикет должен быть создан
        Ticket ticket = ticketRepository.findByOrderId(1L).orElseThrow();
        assertThat(ticket).isNotNull();
        assertThat(ticket.getOrderId()).isEqualTo(1L);
        assertThat(ticket.getItems()).containsExactlyInAnyOrder("Пицца", "Паста");
        assertThat(ticket.getStatus()).isEqualTo(TicketStatusEnum.OPEN);

        /// тикет должен быть в Redis
        assertThat(redisService.getCachedTicket(1L)).isPresent();

        /// Kafka отправлено событие начала готовки
        verify(kafkaTemplate, times(1)).send(
                eq("kitchen-service.cooking.started"),
                eq(1L)
        );

        /// метрики вызваны
        verify(kafkaMetrics, times(1)).incrementConsumed();
        verify(kafkaMetrics, times(1)).incrementOrderReceived();
    }


    @Test
    @DisplayName("acceptOrder() - создаёт тикет с пустым списком блюд")
    void acceptOrder_ShouldCreateTicket_WithEmptyItems() {
        /// событие с пустым списком блюд
        OrderPaidEvent event = createOrderPaidEvent(2L, List.of());

        /// принимаем заказ
        kitchenService.acceptOrder(event);

        /// тикет создан с пустым списком
        Ticket ticket = ticketRepository.findByOrderId(2L).orElseThrow();
        assertThat(ticket).isNotNull();
        assertThat(ticket.getItems()).isEmpty();
        assertThat(ticket.getStatus()).isEqualTo(TicketStatusEnum.OPEN);
    }


    @Test
    @DisplayName("completeCooking() - завершает готовку и меняет статус на READY")
    void completeCooking_ShouldChangeStatusToReady() {
        /// существующий тикет со статусом OPEN
        List<String> items = List.of("Пицца", "Паста");
        Ticket ticket = Ticket.builder()
                .orderId(3L)
                .items(items)
                .status(TicketStatusEnum.OPEN)
                .build();
        ticketRepository.save(ticket);

        /// завершаем готовку
        kitchenService.completeCooking(3L);

        /// статус изменился на READY
        Ticket updated = ticketRepository.findByOrderId(3L).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(TicketStatusEnum.READY);

        /// обновлён в Redis
        assertThat(redisService.getCachedTicket(3L)).isPresent();

        /// отправлено событие готовности
        verify(kafkaTemplate, times(1)).send(
                eq("kitchen-service.order.ready"),
                any(KitchenOrderReadyEvent.class)
        );

        /// метрики вызваны
        verify(kafkaMetrics, times(1)).incrementOrderReady();
        verify(kafkaMetrics, times(1)).incrementProduced();
    }


    @Test
    @DisplayName("completeCooking() - выбрасывает исключение, если тикет не найден")
    void completeCooking_ShouldThrowException_WhenTicketNotFound() {
        /// пытаемся завершить готовку несуществующего заказа
        /// выбрасывается исключение
        assertThatThrownBy(() -> kitchenService.completeCooking(99999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Тикет не найден");
    }


    @Test
    @DisplayName("cancelOrder() - отменяет заказ и меняет статус на CANCELLED")
    void cancelOrder_ShouldChangeStatusToCancelled() {
        /// существующий тикет со статусом OPEN
        Ticket ticket = Ticket.builder()
                .orderId(4L)
                .items(List.of("Пицца"))
                .status(TicketStatusEnum.OPEN)
                .build();
        ticketRepository.save(ticket);

        /// отменяем заказ
        kitchenService.cancelOrder(4L);

        /// статус изменился на CANCELLED
        Ticket updated = ticketRepository.findByOrderId(4L).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(TicketStatusEnum.CANCELLED);

        /// удалён из Redis
        assertThat(redisService.getCachedTicket(4L)).isEmpty();

        /// отправлено событие отмены
        verify(kafkaTemplate, times(1)).send(
                eq("kitchen-service.order.cancelled"),
                eq(4L)
        );
    }

    @Test
    @DisplayName("cancelOrder() - не отменяет заказ, если он уже готов")
    void cancelOrder_ShouldNotCancel_WhenAlreadyReady() {
        /// готовый тикет
        Ticket ticket = Ticket.builder()
                .orderId(5L)
                .items(List.of("Пицца"))
                .status(TicketStatusEnum.READY)
                .build();
        ticketRepository.save(ticket);

        /// пытаемся отменить
        kitchenService.cancelOrder(5L);

        /// статус остался READY
        Ticket updated = ticketRepository.findByOrderId(5L).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(TicketStatusEnum.READY);

        /// событие отмены НЕ отправлено
        verify(kafkaTemplate, never()).send(eq("kitchen-service.order.cancelled"), eq(5L));
    }


    @Test
    @DisplayName("startCookingAsync() - асинхронно готовит заказ")
    void startCookingAsync_ShouldCookOrder() throws InterruptedException {
        /// тикет со статусом OPEN
        List<String> items = List.of("Пицца", "Паста");
        Ticket ticket = Ticket.builder()
                .orderId(6L)
                .items(items)
                .status(TicketStatusEnum.OPEN)
                .build();
        ticketRepository.save(ticket);

        /// запускаем асинхронную готовку
        kitchenService.startCookingAsync(6L);

        /// ждём завершения готовки
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Ticket cooked = ticketRepository.findByOrderId(6L).orElseThrow();
                    assertThat(cooked.getStatus()).isEqualTo(TicketStatusEnum.READY);
                });

        /// метрика готовки вызвана
        verify(kafkaMetrics, times(1)).incrementOrderPreparing();
        verify(kafkaMetrics, times(1)).incrementOrderReady();
    }

    @Test
    @DisplayName("processQueue() - обрабатывает заказы из очереди")
    void processQueue_ShouldProcessOrdersFromQueue() throws InterruptedException {
        /// заказ принят и добавлен в очередь
        List<String> items = List.of("Пицца");
        OrderPaidEvent event = createOrderPaidEvent(7L, items);
        kitchenService.acceptOrder(event);

        /// запускаем обработку очереди
        kitchenService.processQueue();

        /// заказ начал готовиться
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Ticket ticket = ticketRepository.findByOrderId(7L).orElseThrow();
                    assertThat(ticket.getStatus()).isEqualTo(TicketStatusEnum.READY);
                });
    }


    @Test
    @DisplayName("getTicketByOrderId() - возвращает тикет из Redis, если есть")
    void getTicketByOrderId_ShouldReturnFromRedis_WhenExists() {
        /// тикет в БД и Redis
        Ticket ticket = Ticket.builder()
                .orderId(8L)
                .items(List.of("Пицца"))
                .status(TicketStatusEnum.OPEN)
                .build();
        ticketRepository.save(ticket);
        redisService.cacheTicket(8L, ticket);

        /// получаем тикет
        Ticket result = kitchenService.getTicketByOrderId(8L);

        /// возвращён тикет
        assertThat(result).isNotNull();
        assertThat(result.getOrderId()).isEqualTo(8L);
        /// Проверяем, что Redis вызывался
        assertThat(redisService.getCachedTicket(8L)).isPresent();
    }


    @Test
    @DisplayName("getTicketByOrderId() - загружает из БД, если нет в Redis")
    void getTicketByOrderId_ShouldLoadFromDatabase_WhenNotInRedis() {
        ///  тикет только в БД
        Ticket ticket = Ticket.builder()
                .orderId(9L)
                .items(List.of("Паста"))
                .status(TicketStatusEnum.OPEN)
                .build();
        ticketRepository.save(ticket);

        /// Очищаем Redis
        redisService.evictTicket(9L);
        assertThat(redisService.getCachedTicket(9L)).isEmpty();

        /// получаем тикет
        Ticket result = kitchenService.getTicketByOrderId(9L);

        /// тикет загружен из БД
        assertThat(result).isNotNull();
        assertThat(result.getOrderId()).isEqualTo(9L);
        assertThat(result.getStatus()).isEqualTo(TicketStatusEnum.OPEN);
    }


    @Test
    @DisplayName("getTicketById() - возвращает тикет со всеми полями")
    void getTicketById_ShouldReturnAllFields() {
        /// тикет с полными данными
        Ticket ticket = Ticket.builder()
                .orderId(10L)
                .items(List.of("Пицца", "Паста", "Салат"))
                .status(TicketStatusEnum.OPEN)
                .build();
        Ticket saved = ticketRepository.save(ticket);

        /// получаем по ID
        Ticket result = kitchenService.getTicketById(saved.getId());

        /// все поля совпадают
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(saved.getId());
        assertThat(result.getOrderId()).isEqualTo(10L);
        assertThat(result.getItems()).containsExactly("Пицца", "Паста", "Салат");
        assertThat(result.getStatus()).isEqualTo(TicketStatusEnum.OPEN);
    }


    @Test
    @DisplayName("getTicketsByStatus() - возвращает тикеты по статусу")
    void getTicketsByStatus_ShouldReturnTicketsByStatus() {
        /// тикеты с разными статусами
        Ticket ticket1 = Ticket.builder().orderId(11L).items(List.of("Пицца")).status(TicketStatusEnum.OPEN).build();
        Ticket ticket2 = Ticket.builder().orderId(12L).items(List.of("Паста")).status(TicketStatusEnum.OPEN).build();
        Ticket ticket3 = Ticket.builder().orderId(13L).items(List.of("Салат")).status(TicketStatusEnum.READY).build();
        ticketRepository.saveAll(List.of(ticket1, ticket2, ticket3));

        /// получаем OPEN тикеты
        List<Ticket> openTickets = kitchenService.getTicketsByStatus(TicketStatusEnum.OPEN);

        /// только 2 тикета со статусом OPEN
        assertThat(openTickets).hasSize(2);
        assertThat(openTickets).extracting(Ticket::getOrderId)
                .containsExactlyInAnyOrder(11L, 12L);
        assertThat(openTickets).allMatch(t -> t.getStatus() == TicketStatusEnum.OPEN);
    }


    @Test
    @DisplayName("getOpenTickets() - возвращает все открытые тикеты")
    void getOpenTickets_ShouldReturnOpenTickets() {

        Ticket ticket1 = Ticket.builder().orderId(14L).items(List.of("Пицца")).status(TicketStatusEnum.OPEN).build();
        Ticket ticket2 = Ticket.builder().orderId(15L).items(List.of("Паста")).status(TicketStatusEnum.OPEN).build();
        Ticket ticket3 = Ticket.builder().orderId(16L).items(List.of("Салат")).status(TicketStatusEnum.READY).build();
        ticketRepository.saveAll(List.of(ticket1, ticket2, ticket3));


        List<Ticket> result = kitchenService.getOpenTickets();


        assertThat(result).hasSize(2);
        assertThat(result).allMatch(t -> t.getStatus() == TicketStatusEnum.OPEN);
    }

    @Test
    @DisplayName("getItemsListForTicketId() - возвращает список блюд по ID тикета")
    void getItemsListForTicketId_ShouldReturnItems() {

        List<String> items = List.of("Пицца", "Паста", "Салат");
        Ticket ticket = Ticket.builder()
                .orderId(17L)
                .items(items)
                .status(TicketStatusEnum.OPEN)
                .build();
        Ticket saved = ticketRepository.save(ticket);


        List<String> result = kitchenService.getItemsListForTicketId(saved.getId());


        assertThat(result).hasSize(3);
        assertThat(result).containsExactly("Пицца", "Паста", "Салат");
    }


    @Test
    @DisplayName("getItemsListForOrderId() - возвращает список блюд по ID заказа")
    void getItemsListForOrderId_ShouldReturnItems() {

        List<String> items = List.of("Пицца", "Паста");
        Ticket ticket = Ticket.builder()
                .orderId(18L)
                .items(items)
                .status(TicketStatusEnum.OPEN)
                .build();
        ticketRepository.save(ticket);


        List<String> result = kitchenService.getItemsListForOrderId(18L);


        assertThat(result).hasSize(2);
        assertThat(result).containsExactly("Пицца", "Паста");
    }


    @Test
    @DisplayName("Полный цикл: acceptOrder() → startCookingAsync() → completeCooking()")
    void fullCycle_AcceptCookComplete() throws InterruptedException {
        /// событие заказа
        List<String> items = List.of("Пицца", "Паста");
        OrderPaidEvent event = createOrderPaidEvent(19L, items);

        /// принимаем заказ
        kitchenService.acceptOrder(event);

        /// тикет создан со статусом OPEN
        Ticket ticket = ticketRepository.findByOrderId(19L).orElseThrow();
        assertThat(ticket.getStatus()).isEqualTo(TicketStatusEnum.OPEN);

        /// запускаем готовку
        kitchenService.startCookingAsync(19L);

        /// статус изменился на READY
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Ticket cooked = ticketRepository.findByOrderId(19L).orElseThrow();
                    assertThat(cooked.getStatus()).isEqualTo(TicketStatusEnum.READY);
                });

        /// отправлены все события
        verify(kafkaTemplate, atLeastOnce()).send(anyString(), any());
        verify(kafkaMetrics, atLeastOnce()).incrementConsumed();
        verify(kafkaMetrics, atLeastOnce()).incrementOrderReceived();
        verify(kafkaMetrics, atLeastOnce()).incrementOrderReady();
    }
}