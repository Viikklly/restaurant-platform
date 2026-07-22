package com.restaurant.kitchen_service.controller;

import com.restaurant.kitchen_service.entity.Ticket;
import com.restaurant.kitchen_service.enums.TicketStatusEnum;
import com.restaurant.kitchen_service.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.cloud.config.enabled=false",
        "spring.kafka.bootstrap-servers=",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
        "spring.jpa.hibernate.ddl-auto=update",
        "spring.cache.type=redis"
})
@Testcontainers
@DisplayName("Интеграционные тесты KitchenController")
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class KitchenControllerIntegrationTest {


    /// мок для KafkaTemplate
    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public KafkaTemplate<String, Object> kafkaTemplate() {
            @SuppressWarnings("unchecked")
            KafkaTemplate<String, Object> template = mock(KafkaTemplate.class);
            when(template.send(anyString(), any()))
                    .thenReturn(CompletableFuture.completedFuture(null));
            return template;
        }
    }

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("kitchen_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        registry.add("spring.data.redis.timeout", () -> "6000ms");
        registry.add("spring.data.redis.lettuce.pool.max-idle", () -> "8");
        registry.add("spring.data.redis.lettuce.pool.min-idle", () -> "0");
        registry.add("spring.data.redis.lettuce.pool.max-active", () -> "8");
        registry.add("spring.data.redis.lettuce.pool.max-wait", () -> "-1ms");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private CacheManager cacheManager;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        /// Очищаем кеш перед каждым тестом
        cacheManager.getCacheNames().stream()
                .forEach(name -> cacheManager.getCache(name).clear());

        baseUrl = "http://localhost:" + port + "/api/kitchen";
    }

    private Ticket createTicket(Long orderId, List<String> items, TicketStatusEnum status) {
        return Ticket.builder()
                .orderId(orderId)
                .items(items)
                .status(status)
                .build();
    }



    @Test
    @DisplayName("GET /api/kitchen/tickets - возвращает все тикеты")
    void getAllTickets_ShouldReturnAllTickets() {
        Ticket ticket1 = createTicket(1L, List.of("Пицца"), TicketStatusEnum.OPEN);
        Ticket ticket2 = createTicket(2L, List.of("Паста"), TicketStatusEnum.READY);
        ticketRepository.saveAll(List.of(ticket1, ticket2));

        ResponseEntity<Ticket[]> response = restTemplate.getForEntity(
                baseUrl + "/tickets",
                Ticket[].class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(2);
    }


    @Test
    @DisplayName("GET /api/kitchen/tickets/{id} - возвращает тикет по ID")
    void getTicketById_ShouldReturnTicket() {
        Ticket ticket = createTicket(3L, List.of("Пицца", "Паста"), TicketStatusEnum.OPEN);
        Ticket saved = ticketRepository.save(ticket);

        ResponseEntity<Ticket> response = restTemplate.getForEntity(
                baseUrl + "/tickets/" + saved.getId(),
                Ticket.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(saved.getId());
    }


    @Test
    @DisplayName("GET /api/kitchen/tickets/{id} - возвращает ошибку для несуществующего тикета")
    void getTicketById_ShouldReturnError_WhenNotFound() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl + "/tickets/99999",
                String.class
        );

        assertThat(response.getStatusCode().is5xxServerError()).isTrue();
    }



    @Test
    @DisplayName("GET /api/kitchen/tickets/order/{orderId}/items - возвращает тикет по ID заказа")
    void getTicketByOrderId_ShouldReturnTicket() {
        ///тикет с orderId
        Ticket ticket = createTicket(4L, List.of("Салат"), TicketStatusEnum.OPEN);
        ticketRepository.save(ticket);

        /// запрос по orderId
        ResponseEntity<Ticket> response = restTemplate.getForEntity(
                baseUrl + "/tickets/order/" + 4L + "/items",
                Ticket.class
        );

        /// тикет найден
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getOrderId()).isEqualTo(4L);
        assertThat(response.getBody().getItems()).contains("Салат");
    }


    @Test
    @DisplayName("GET /api/kitchen/tickets/status/{status} - возвращает тикеты по статусу")
    void getTicketsByStatus_ShouldReturnTicketsByStatus() {
        /// тикеты с разными статусами
        Ticket ticket1 = createTicket(5L, List.of("Пицца"), TicketStatusEnum.OPEN);
        Ticket ticket2 = createTicket(6L, List.of("Паста"), TicketStatusEnum.OPEN);
        Ticket ticket3 = createTicket(7L, List.of("Салат"), TicketStatusEnum.READY);
        ticketRepository.saveAll(List.of(ticket1, ticket2, ticket3));

        /// запрос тикетов со статусом OPEN
        ResponseEntity<Ticket[]> response = restTemplate.getForEntity(
                baseUrl + "/tickets/status/OPEN",
                Ticket[].class
        );

        ///2 тикета со статусом OPEN
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(2);
        assertThat(response.getBody())
                .allMatch(t -> t.getStatus() == TicketStatusEnum.OPEN);
        assertThat(response.getBody())
                .extracting(Ticket::getOrderId)
                .containsExactlyInAnyOrder(5L, 6L);
    }

    @Test
    @DisplayName("GET /api/kitchen/tickets/status/open - возвращает открытые тикеты")
    void getOpenTickets_ShouldReturnOpenTickets() {

        Ticket ticket1 = createTicket(8L, List.of("Пицца"), TicketStatusEnum.OPEN);
        Ticket ticket2 = createTicket(9L, List.of("Паста"), TicketStatusEnum.OPEN);
        Ticket ticket3 = createTicket(10L, List.of("Салат"), TicketStatusEnum.READY);
        ticketRepository.saveAll(List.of(ticket1, ticket2, ticket3));

        /// запрос открытых тикетов
        ResponseEntity<Ticket[]> response = restTemplate.getForEntity(
                baseUrl + "/tickets/status/open",
                Ticket[].class
        );

        /// 2 открытых тикета
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(2);
        assertThat(response.getBody())
                .allMatch(t -> t.getStatus() == TicketStatusEnum.OPEN);
    }


    @Test
    @DisplayName("GET /api/kitchen/tickets/status/in-progress - возвращает тикеты в работе")
    void getInProgressTickets_ShouldReturnInProgressTickets() {

        Ticket ticket1 = createTicket(11L, List.of("Пицца"), TicketStatusEnum.IN_PROGRESS);
        Ticket ticket2 = createTicket(12L, List.of("Паста"), TicketStatusEnum.OPEN);
        Ticket ticket3 = createTicket(13L, List.of("Салат"), TicketStatusEnum.IN_PROGRESS);
        ticketRepository.saveAll(List.of(ticket1, ticket2, ticket3));

        ///запрос тикетов в работе
        ResponseEntity<Ticket[]> response = restTemplate.getForEntity(
                baseUrl + "/tickets/status/in-progress",
                Ticket[].class
        );

        ///2 тикета в работе
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(2);
        assertThat(response.getBody())
                .allMatch(t -> t.getStatus() == TicketStatusEnum.IN_PROGRESS);
    }


    @Test
    @DisplayName("GET /api/kitchen/tickets/status/ready - возвращает готовые тикеты")
    void getReadyTickets_ShouldReturnReadyTickets() {

        Ticket ticket1 = createTicket(14L, List.of("Пицца"), TicketStatusEnum.READY);
        Ticket ticket2 = createTicket(15L, List.of("Паста"), TicketStatusEnum.OPEN);
        Ticket ticket3 = createTicket(16L, List.of("Салат"), TicketStatusEnum.READY);
        ticketRepository.saveAll(List.of(ticket1, ticket2, ticket3));

        /// запрос готовых тикетов
        ResponseEntity<Ticket[]> response = restTemplate.getForEntity(
                baseUrl + "/tickets/status/ready",
                Ticket[].class
        );

        /// 2 готовых тикета
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(2);
        assertThat(response.getBody())
                .allMatch(t -> t.getStatus() == TicketStatusEnum.READY);
    }


    @Test
    @DisplayName("GET /api/kitchen/tickets/{ticketId}/items - возвращает список блюд тикета")
    void getItemsListByTicketId_ShouldReturnItems() {
        /// тикет с блюдами
        Ticket ticket = createTicket(17L, List.of("Пицца", "Паста", "Салат"), TicketStatusEnum.OPEN);
        Ticket saved = ticketRepository.save(ticket);

        ///запрос списка блюд
        ResponseEntity<String[]> response = restTemplate.getForEntity(
                baseUrl + "/tickets/" + saved.getId() + "/items",
                String[].class
        );

        ///список блюд
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(3);
        assertThat(response.getBody()).containsExactly("Пицца", "Паста", "Салат");
    }

    @Test
    @DisplayName("GET /api/kitchen/tickets - возвращает пустой список, если тикетов нет")
    void getAllTickets_ShouldReturnEmptyList_WhenNoTickets() {
        ///запрос при пустой БД
        ResponseEntity<Ticket[]> response = restTemplate.getForEntity(
                baseUrl + "/tickets",
                Ticket[].class
        );

        /// пустой список
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).isEmpty();
    }
}