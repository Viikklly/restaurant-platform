package com.restaurant.payment_service.service;

import com.restaurant.payment_service.entity.PaymentTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.cloud.config.enabled=false",
        "spring.jpa.hibernate.ddl-auto=update"
})
@Testcontainers
@DisplayName("Интеграционные тесты RedisService")
class PaymentRedisServiceIntegrationTest {


    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("payment_test")
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
    private RedisService redisService;

    ///  перед
    @BeforeEach
    void setUp() {
        // Очищаем все тестовые ключи
        redisService.delete("test:key");
        redisService.delete("test:list");
        redisService.delete("test:object");
        redisService.delete("test:expire");
        redisService.delete("test:user:*");
    }



    private PaymentTransaction createTestTransaction(Long id, Long orderId, Long userId,
                                                     BigDecimal amount, String status) {
        return PaymentTransaction.builder()
                .id(id)
                .orderId(orderId)
                .userId(userId)
                .amount(amount)
                .statusPayment(status)
                .message("Test message")
                .createdAt(LocalDateTime.now())
                .build();
    }

    private List<PaymentTransaction> createTestTransactionList() {
        List<PaymentTransaction> list = new ArrayList<>();
        list.add(createTestTransaction(1L, 1001L, 2001L, BigDecimal.valueOf(1000), "SUCCESS"));
        list.add(createTestTransaction(2L, 1002L, 2002L, BigDecimal.valueOf(2000), "FAILED"));
        list.add(createTestTransaction(3L, 1003L, 2003L, BigDecimal.valueOf(3000), "SUCCESS"));
        return list;
    }


    @Test
    @DisplayName("save() и get() - сохраняет и получает объект")
    void saveAndGet_ShouldSaveAndRetrieveObject() {

        String key = "test:key";
        String value = "Hello, Redis!";


        redisService.save(key, value);


        Object retrieved = redisService.get(key);
        assertThat(retrieved)
                .as("Должен вернуть сохраненное значение и оно не null")
                .isNotNull()
                .isEqualTo(value);
    }

    @Test
    @DisplayName("saveWithExpire() - сохраняет объект с TTL и он истекает")
    void saveWithExpire_ShouldSaveWithExpiration() throws InterruptedException {

        String key = "test:expire";
        String value = "Test";

        /// сохраняем на 2 секунды
        redisService.saveWithExpire(key, value, 2);

        /// сразу после сохранения значение есть
        Object retrieved = redisService.get(key);
        assertThat(retrieved)
                .as("Сразу после сохранения значение должно быть")
                .isNotNull()
                .isEqualTo(value);


        assertThat(redisService.hasKey(key))
                .as("Ключ должен существовать")
                .isTrue();

        /// Ждем истечения TTL (3 секунды)
        Thread.sleep(3000);

        ///после истечения TTL значение должно исчезнуть
        Object expired = redisService.get(key);
        assertThat(expired)
                .as("После истечения TTL значение должно быть null")
                .isNull();

        assertThat(redisService.hasKey(key))
                .as("После истечения TTL ключ должен отсутствовать")
                .isFalse();
    }

    @Test
    @DisplayName("get(key, Class<T>) - получает объект с приведением типа")
    void getWithType_ShouldRetrieveAndCastObject() {

        String key = "test:object";
        PaymentTransaction transaction = createTestTransaction(1L, 1001L, 2001L,
                BigDecimal.valueOf(5000), "SUCCESS");


        redisService.save(key, transaction);

        PaymentTransaction retrieved = redisService.get(key, PaymentTransaction.class);
        assertThat(retrieved)
                .as("Должен вернуть объект правильного типа")
                .isNotNull();
        assertThat(retrieved.getId()).isEqualTo(1L);
        assertThat(retrieved.getOrderId()).isEqualTo(1001L);
        assertThat(retrieved.getUserId()).isEqualTo(2001L);
        assertThat(retrieved.getAmount())
                .usingComparator(BigDecimal::compareTo)
                .isEqualTo(BigDecimal.valueOf(5000));
        assertThat(retrieved.getStatusPayment()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("get(key, Class<T>) - возвращает null для несуществующего ключа")
    void getWithType_ShouldReturnNull_WhenKeyNotExists() {
        /// Вызываем метод
        PaymentTransaction retrieved = redisService.get("non:existent:key", PaymentTransaction.class);

        assertThat(retrieved)
                .as("Для несуществующего ключа должен вернуть null")
                .isNull();
    }

    @Test
    @DisplayName("getList() - сохраняет и получает список")
    void getList_ShouldSaveAndRetrieveList() {

        String key = "test:list";
        List<PaymentTransaction> transactions = createTestTransactionList();

        ///  Вызываем метод
        redisService.save(key, transactions);

        /// Проверки
        List<PaymentTransaction> retrieved = redisService.getList(key, PaymentTransaction.class);

        assertThat(retrieved)
                .as("Должен вернуть список правильного размера")
                .isNotNull()
                .hasSize(3);

        assertThat(retrieved)
                .as("Списки должны совпадать")
                .usingRecursiveComparison()
                .ignoringFields("amount")
                .isEqualTo(transactions);

        /// Сравниваем BigDecimal отдельно
        for (int i = 0; i < retrieved.size(); i++) {
            assertThat(retrieved.get(i).getAmount())
                    .usingComparator(BigDecimal::compareTo)
                    .isEqualTo(transactions.get(i).getAmount());
        }
    }

    @Test
    @DisplayName("getList() - возвращает пустой список, если сохранен пустой список")
    void getList_ShouldReturnEmptyList_WhenListIsEmpty() {
        /// Создаем
        String key = "test:empty:list";
        List<PaymentTransaction> emptyList = new ArrayList<>();

        /// Вызываем
        redisService.save(key, emptyList);

        /// Проверки
        List<PaymentTransaction> retrieved = redisService.getList(key, PaymentTransaction.class);
        assertThat(retrieved)
                .as("Должен вернуть пустой список")
                .isNotNull()
                .isEmpty();
    }

    @Test
    @DisplayName("delete() - удаляет ключ из кэша")
    void delete_ShouldRemoveKeyFromCache() {
        /// Создаем
        String key = "test:to:delete";
        String value = "Delete me";

        /// Сохраняем
        redisService.save(key, value);
        assertThat(redisService.hasKey(key))
                .as("Ключ должен существовать после сохранения")
                .isTrue();

        /// Вызываем
        redisService.delete(key);

        /// Проверки
        assertThat(redisService.hasKey(key))
                .as("После удаления ключ должен отсутствовать")
                .isFalse();

        Object retrieved = redisService.get(key);
        assertThat(retrieved)
                .as("После удаления значение должно быть null")
                .isNull();
    }


    @Test
    @DisplayName("delete() - удаление нескольких ключей")
    void delete_ShouldRemoveMultipleKeys() {
        /// Создаем
        String key1 = "test:multi:1";
        String key2 = "test:multi:2";
        String key3 = "test:multi:3";

        /// Сохраняем
        redisService.save(key1, "value1");
        redisService.save(key2, "value2");
        redisService.save(key3, "value3");

        assertThat(redisService.hasKey(key1)).isTrue();
        assertThat(redisService.hasKey(key2)).isTrue();
        assertThat(redisService.hasKey(key3)).isTrue();

        /// удаляем по одному
        redisService.delete(key1);
        redisService.delete(key2);
        redisService.delete(key3);

        /// Проверки
        assertThat(redisService.hasKey(key1)).isFalse();
        assertThat(redisService.hasKey(key2)).isFalse();
        assertThat(redisService.hasKey(key3)).isFalse();
    }

    @Test
    @DisplayName("getList() - сохраняет и получает список, содержащий null элементы")
    void getList_ShouldHandleListWithNulls() {
        /// Создаем
        String key = "test:list:with:nulls";
        List<PaymentTransaction> listWithNulls = new ArrayList<>();
        listWithNulls.add(createTestTransaction(1L, 1001L, 2001L,
                BigDecimal.valueOf(1000), "SUCCESS"));
        listWithNulls.add(null);
        listWithNulls.add(createTestTransaction(2L, 1002L, 2002L,
                BigDecimal.valueOf(2000), "FAILED"));

        ///  Вызываем метод
        redisService.save(key, listWithNulls);

        /// Проверки
        List<PaymentTransaction> retrieved = redisService.getList(key, PaymentTransaction.class);
        assertThat(retrieved)
                .as("Список должен сохраниться с null элементами")
                .isNotNull()
                .hasSize(3);

        assertThat(retrieved.get(0))
                .as("Первый элемент должен быть транзакцией")
                .isNotNull();
        assertThat(retrieved.get(1))
                .as("Второй элемент должен быть null")
                .isNull();
        assertThat(retrieved.get(2))
                .as("Третий элемент должен быть транзакцией")
                .isNotNull();
    }
}
