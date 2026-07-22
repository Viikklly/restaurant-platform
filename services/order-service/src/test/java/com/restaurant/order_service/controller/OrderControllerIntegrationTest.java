package com.restaurant.order_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.order_service.dto.OrderCreateRequestDTO;
import com.restaurant.order_service.dto.OrderResponseDTO;
import com.restaurant.order_service.entity.Item;
import com.restaurant.order_service.entity.Order;
import com.restaurant.order_service.repository.ItemRepository;
import com.restaurant.order_service.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cache.CacheManager;
import org.springframework.http.*;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.cloud.config.enabled=false",
        "spring.kafka.bootstrap-servers=",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
        "spring.jpa.hibernate.ddl-auto=update"
})
@Testcontainers
@DisplayName("Интеграционные тесты OrderController")
/// Аннотация @Sql для очистки данных перед каждым тестом
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class OrderControllerIntegrationTest {

    @MockitoBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("order_test")
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

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private CacheManager cacheManager;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        /// Аннотация @Sql для очистки данных перед каждым тестом над классом
        /// Очищаем кэш
        cacheManager.getCacheNames().stream()
                .forEach(name -> cacheManager.getCache(name).clear());

        baseUrl = "http://localhost:" + port + "/api/orders";

        when(kafkaTemplate.send(anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    private void createTestItems() {
        Item item1 = Item.builder()
                .itemName("Пицца Маргарита")
                .itemPrice(BigDecimal.valueOf(500))
                .build();
        Item item2 = Item.builder()
                .itemName("Паста Карбонара")
                .itemPrice(BigDecimal.valueOf(450))
                .build();
        itemRepository.saveAll(List.of(item1, item2));
    }

    private OrderCreateRequestDTO createOrderRequest(List<OrderCreateRequestDTO.ItemRequestDTO> items) {
        OrderCreateRequestDTO request = new OrderCreateRequestDTO();
        request.setUserId(1001L);
        request.setItems(items);
        return request;
    }

    private OrderCreateRequestDTO.ItemRequestDTO createItemRequest(String productName, Integer quantity) {
        OrderCreateRequestDTO.ItemRequestDTO item = new OrderCreateRequestDTO.ItemRequestDTO();
        item.setProductName(productName);
        item.setQuantity(quantity);
        return item;
    }

    @Test
    @DisplayName("POST /api/orders - должен успешно создать заказ")
    void createOrder_ShouldCreateOrderSuccessfully() {
        /// Подготовка
        createTestItems();
        List<OrderCreateRequestDTO.ItemRequestDTO> items = List.of(
                createItemRequest("Пицца Маргарита", 2),
                createItemRequest("Паста Карбонара", 1)
        );
        OrderCreateRequestDTO request = createOrderRequest(items);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", "1001");
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<OrderCreateRequestDTO> entity = new HttpEntity<>(request, headers);

        /// Выполнение
        ResponseEntity<OrderResponseDTO> response = restTemplate.exchange(
                baseUrl,
                HttpMethod.POST,
                entity,
                OrderResponseDTO.class
        );

        /// Проверка
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isNotNull();
        assertThat(response.getBody().getUserId()).isEqualTo(1001L);
        assertThat(response.getBody().getStatus()).isEqualTo("PENDING");
        assertThat(response.getBody().getTotalAmount())
                .usingComparator(BigDecimal::compareTo)
                .isEqualTo(BigDecimal.valueOf(1450));
        assertThat(response.getBody().getItems())
                .hasSize(3)
                .containsExactlyInAnyOrder("Пицца Маргарита", "Пицца Маргарита", "Паста Карбонара");
        assertThat(response.getBody().getCreatedAt()).isNotNull();

        /// Проверка БД
        List<Order> orders = orderRepository.findAll();
        assertThat(orders).hasSize(1);
        Order savedOrder = orders.get(0);
        assertThat(savedOrder.getUserId()).isEqualTo(1001L);
        assertThat(savedOrder.getStatus()).isEqualTo("PENDING");
        assertThat(savedOrder.getTotalAmount())
                .usingComparator(BigDecimal::compareTo)
                .isEqualTo(BigDecimal.valueOf(1450));
    }

    @Test
    @DisplayName("POST /api/orders - должен вернуть ошибку 400 при отсутствии заголовка X-User-Id")
    void createOrder_ShouldReturnBadRequest_WhenUserIdHeaderMissing() {
        createTestItems();
        List<OrderCreateRequestDTO.ItemRequestDTO> items = List.of(
                createItemRequest("Пицца Маргарита", 1)
        );
        OrderCreateRequestDTO request = createOrderRequest(items);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<OrderCreateRequestDTO> entity = new HttpEntity<>(request, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl,
                HttpMethod.POST,
                entity,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("POST /api/orders - должен вернуть ошибку 400 при пустом списке блюд")
    void createOrder_ShouldReturnBadRequest_WhenItemsEmpty() {
        OrderCreateRequestDTO request = createOrderRequest(List.of());

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", "1001");
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<OrderCreateRequestDTO> entity = new HttpEntity<>(request, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl,
                HttpMethod.POST,
                entity,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("POST /api/orders - должен вернуть ошибку 500 при несуществующем блюде")
    void createOrder_ShouldReturnInternalServerError_WhenItemNotFound() {
        List<OrderCreateRequestDTO.ItemRequestDTO> items = List.of(
                createItemRequest("Несуществующее блюдо", 1)
        );
        OrderCreateRequestDTO request = createOrderRequest(items);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", "1001");
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<OrderCreateRequestDTO> entity = new HttpEntity<>(request, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl,
                HttpMethod.POST,
                entity,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("GET /api/orders/{id} - должен вернуть заказ по ID")
    void getOrder_ShouldReturnOrderById() {
        /// Подготовка
        createTestItems();
        List<OrderCreateRequestDTO.ItemRequestDTO> items = List.of(
                createItemRequest("Пицца Маргарита", 1)
        );
        OrderCreateRequestDTO request = createOrderRequest(items);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", "1001");
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<OrderCreateRequestDTO> entity = new HttpEntity<>(request, headers);

        ResponseEntity<OrderResponseDTO> createResponse = restTemplate.exchange(
                baseUrl,
                HttpMethod.POST,
                entity,
                OrderResponseDTO.class
        );

        Long orderId = createResponse.getBody().getId();

        /// Выполнение
        ResponseEntity<OrderResponseDTO> response = restTemplate.getForEntity(
                baseUrl + "/" + orderId,
                OrderResponseDTO.class
        );

        /// Проверка
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(orderId);
        assertThat(response.getBody().getUserId()).isEqualTo(1001L);
        assertThat(response.getBody().getStatus()).isEqualTo("PENDING");
        assertThat(response.getBody().getItems())
                .hasSize(1)
                .contains("Пицца Маргарита");
    }

    @Test
    @DisplayName("GET /api/orders/{id} - должен вернуть ошибку 404 для несуществующего заказа")
    void getOrder_ShouldReturnNotFound_WhenOrderNotExists() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl + "/99999",
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}