package com.restaurant.order_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.order_service.entity.Item;
import com.restaurant.order_service.repository.ItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
@DisplayName("Интеграционные тесты ItemController")
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ItemControllerIntegrationTest {

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
    private ItemRepository itemRepository;

    @Autowired
    private CacheManager cacheManager;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        cacheManager.getCacheNames().stream()
                .forEach(name -> cacheManager.getCache(name).clear());

        baseUrl = "http://localhost:" + port + "/api/items";

        when(kafkaTemplate.send(anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    private void createTestItems() {
        Item item1 = Item.builder()
                .itemName("Пицца Маргарита")
                .itemPrice(BigDecimal.valueOf(500))
                .itemDescription("Классическая пицца с томатным соусом и сыром")
                .isAvailable(true)
                .build();

        Item item2 = Item.builder()
                .itemName("Паста Карбонара")
                .itemPrice(BigDecimal.valueOf(450))
                .itemDescription("Спагетти с беконом и сыром пармезан")
                .isAvailable(true)
                .build();

        Item item3 = Item.builder()
                .itemName("Цезарь")
                .itemPrice(BigDecimal.valueOf(329.50))
                .itemDescription("Салат с курицей и соусом Цезарь")
                .isAvailable(false)
                .build();

        itemRepository.saveAll(List.of(item1, item2, item3));
    }

    @Test
    @DisplayName("GET /api/items - должен вернуть список всех блюд")
    void getAllItems_ShouldReturnAllItems() {
        createTestItems();

        ResponseEntity<Item[]> response = restTemplate.getForEntity(
                baseUrl,
                Item[].class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(3);

        List<Item> items = List.of(response.getBody());
        assertThat(items).extracting(Item::getItemName)
                .containsExactlyInAnyOrder("Пицца Маргарита", "Паста Карбонара", "Цезарь");


        assertThat(items).extracting(Item::getItemPrice)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactlyInAnyOrder(
                        BigDecimal.valueOf(500),
                        BigDecimal.valueOf(450),
                        BigDecimal.valueOf(329.5)
                );
    }

    @Test
    @DisplayName("GET /api/items - должен вернуть пустой список, если блюд нет")
    void getAllItems_ShouldReturnEmptyList_WhenNoItems() {
        ResponseEntity<Item[]> response = restTemplate.getForEntity(
                baseUrl,
                Item[].class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    @DisplayName("GET /api/items?available=true - должен вернуть только доступные блюда")
    void getAllItems_ShouldReturnOnlyAvailableItems() {
        createTestItems();


        ResponseEntity<Item[]> response = restTemplate.getForEntity(
                baseUrl + "?available=true",
                Item[].class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        List<Item> items = List.of(response.getBody());
        assertThat(items).hasSize(2);
        assertThat(items).extracting(Item::getItemName)
                .containsExactlyInAnyOrder("Пицца Маргарита", "Паста Карбонара");
        assertThat(items).allMatch(Item::getIsAvailable);
    }

    @Test
    @DisplayName("GET /api/items/{id} - должен вернуть блюдо по ID")
    void getItem_ShouldReturnItemById() {
        createTestItems();
        List<Item> items = itemRepository.findAll();
        Long itemId = items.get(0).getId();

        ResponseEntity<Item> response = restTemplate.getForEntity(
                baseUrl + "/" + itemId,
                Item.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(itemId);
        assertThat(response.getBody().getItemName()).isEqualTo("Пицца Маргарита");
        assertThat(response.getBody().getItemPrice())
                .usingComparator(BigDecimal::compareTo)
                .isEqualTo(BigDecimal.valueOf(500));
    }

    @Test
    @DisplayName("GET /api/items/{id} - должен вернуть ошибку 404 для несуществующего блюда")
    void getItem_ShouldReturnNotFound_WhenItemNotExists() {

        ResponseEntity<Item> response = restTemplate.getForEntity(
                baseUrl + "/99999",
                Item.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}