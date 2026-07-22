package com.restaurant.order_service.service;

import com.restaurant.order_service.entity.Item;
import com.restaurant.order_service.repository.ItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
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
@DisplayName("Интеграционные тесты ItemService (БД + Redis)")
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ItemServiceIntegrationTest {

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

    @Autowired
    private ItemService itemService;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        // Очищаем кэш перед каждым тестом
        cacheManager.getCacheNames().stream()
                .forEach(name -> cacheManager.getCache(name).clear());

        when(kafkaTemplate.send(anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    private Item createTestItem(String name, BigDecimal price, String description, Boolean available) {
        return Item.builder()
                .itemName(name)
                .itemPrice(price)
                .itemDescription(description)
                .isAvailable(available)
                .build();
    }



    @Test
    @DisplayName("getAllItems() - возвращает все блюда")
    void getAllItems_ShouldReturnAllItems() {
        /// В базе данных сохранены три блюда
        Item item1 = createTestItem("Пицца", BigDecimal.valueOf(500), "Вкусная пицца", true);
        Item item2 = createTestItem("Паста", BigDecimal.valueOf(450), "Итальянская паста", true);
        Item item3 = createTestItem("Салат", BigDecimal.valueOf(300), "Свежий салат", false);
        itemRepository.saveAll(List.of(item1, item2, item3));

        /// Вызываем метод getAllItems()
        List<Item> result = itemService.getAllItems();

        /// Должны получить все 3 блюда с правильными названиями и ценами
        assertThat(result).hasSize(3);
        assertThat(result).extracting(Item::getItemName)
                .containsExactlyInAnyOrder("Пицца", "Паста", "Салат");
        assertThat(result).extracting(Item::getItemPrice)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactlyInAnyOrder(
                        BigDecimal.valueOf(500),
                        BigDecimal.valueOf(450),
                        BigDecimal.valueOf(300)
                );
    }



    @Test
    @DisplayName("getAllItems() - возвращает пустой список, если блюд нет")
    void getAllItems_ShouldReturnEmptyList_WhenNoItems() {
        /// в базе данных нет ни одного блюда

        /// вызываем метод getAllItems()
        List<Item> result = itemService.getAllItems();

        /// должен вернуться пустой список
        assertThat(result).isEmpty();
    }



    @Test
    @DisplayName("getItemById() - возвращает блюдо по ID")
    void getItemById_ShouldReturnItem() {
        /// в базе данных сохранено блюдо "Пицца Маргарита"
        Item saved = itemRepository.save(
                createTestItem("Пицца Маргарита", BigDecimal.valueOf(500), "Классическая", true)
        );

        /// вызываем метод getItemById() с ID сохранённого блюда
        Item result = itemService.getItemById(saved.getId());

        /// должно вернуться правильное блюдо со всеми полями
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(saved.getId());
        assertThat(result.getItemName()).isEqualTo("Пицца Маргарита");
        assertThat(result.getItemPrice())
                .usingComparator(BigDecimal::compareTo)
                .isEqualTo(BigDecimal.valueOf(500));
        assertThat(result.getIsAvailable()).isTrue();
    }



    @Test
    @DisplayName("getItemById() - выбрасывает исключение, если блюдо не найдено")
    void getItemById_ShouldThrowException_WhenNotFound() {
        /// базе данных нет блюда с ID 99999

        /// вызываем метод getItemById() с несуществующим ID
        /// должно быть выброшено исключение с правильным сообщением
        assertThatThrownBy(() -> itemService.getItemById(99999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Блюдо не найдено: 99999");
    }


    @Test
    @DisplayName("getAvailableItems() - возвращает только доступные блюда")
    void getAvailableItems_ShouldReturnOnlyAvailableItems() {
        /// в базе данных есть 2 доступных и 1 недоступное блюдо
        Item item1 = createTestItem("Пицца", BigDecimal.valueOf(500), "Доступно", true);
        Item item2 = createTestItem("Паста", BigDecimal.valueOf(450), "Доступно", true);
        Item item3 = createTestItem("Салат", BigDecimal.valueOf(300), "Недоступно", false);
        itemRepository.saveAll(List.of(item1, item2, item3));

        /// вызываем метод getAvailableItems()
        List<Item> result = itemService.getAvailableItems();

        /// должны получить только 2 доступных блюда
        assertThat(result).hasSize(2);
        assertThat(result).extracting(Item::getItemName)
                .containsExactlyInAnyOrder("Пицца", "Паста");
        assertThat(result).allMatch(Item::getIsAvailable);
    }


    @Test
    @DisplayName("getItemByName() - возвращает блюдо по названию")
    void getItemByName_ShouldReturnItem() {
        /// в базе данных сохранено блюдо "Пицца Маргарита"
        itemRepository.save(
                createTestItem("Пицца Маргарита", BigDecimal.valueOf(500), "Классическая", true)
        );

        /// вызываем метод getItemByName() с правильным названием
        Item result = itemService.getItemByName("Пицца Маргарита");

        /// должно вернуться правильное блюдо
        assertThat(result).isNotNull();
        assertThat(result.getItemName()).isEqualTo("Пицца Маргарита");
        assertThat(result.getItemPrice())
                .usingComparator(BigDecimal::compareTo)
                .isEqualTo(BigDecimal.valueOf(500));
    }



    @Test
    @DisplayName("getItemByName() - выбрасывает исключение, если блюдо не найдено")
    void getItemByName_ShouldThrowException_WhenNotFound() {
        /// в базе данных нет блюда с таким названием

        /// вызываем метод getItemByName() с несуществующим названием
        /// должно быть выброшено исключение
        assertThatThrownBy(() -> itemService.getItemByName("Несуществующее блюдо"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Блюдо не найдено: Несуществующее блюдо");
    }



    @Test
    @DisplayName("getItemsByNames() - возвращает блюда по списку названий")
    void getItemsByNames_ShouldReturnItemsByNames() {
        /// в базе данных сохранены блюда "Пицца", "Паста", "Салат"
        Item item1 = createTestItem("Пицца", BigDecimal.valueOf(500), "", true);
        Item item2 = createTestItem("Паста", BigDecimal.valueOf(450), "", true);
        Item item3 = createTestItem("Салат", BigDecimal.valueOf(300), "", true);
        itemRepository.saveAll(List.of(item1, item2, item3));

        /// вызываем метод getItemsByNames() со списком названий
        List<Item> result = itemService.getItemsByNames(List.of("Пицца", "Паста"));

        /// должны получить только запрошенные блюда
        assertThat(result).hasSize(2);
        assertThat(result).extracting(Item::getItemName)
                .containsExactlyInAnyOrder("Пицца", "Паста");
    }



    @Test
    @DisplayName("validateItemsExist() - проверяет существование блюд")
    void validateItemsExist_ShouldValidateItems() {
        /// в базе данных сохранено блюдо "Пицца Маргарита"
        itemRepository.save(
                createTestItem("Пицца Маргарита", BigDecimal.valueOf(500), "", true)
        );

        /// вызываем метод
        /// исключение не должно быть выброшено
        itemService.validateItemsExist(List.of("Пицца Маргарита"));
    }



    @Test
    @DisplayName("validateItemsExist() - выбрасывает исключение, если блюдо не существует")
    void validateItemsExist_ShouldThrowException_WhenItemNotFound() {
        /// в базе данных сохранено только одно блюдо
        itemRepository.save(
                createTestItem("Пицца Маргарита", BigDecimal.valueOf(500), "", true)
        );

        /// вызываем метод validateItemsExist() со списком, содержащим несуществующее блюдо
        /// должно быть выброшено исключение
        assertThatThrownBy(() -> itemService.validateItemsExist(
                List.of("Пицца Маргарита", "Несуществующее блюдо")
        )).isInstanceOf(RuntimeException.class)
                .hasMessage("Блюдо не найдено в меню: Несуществующее блюдо");
    }
}