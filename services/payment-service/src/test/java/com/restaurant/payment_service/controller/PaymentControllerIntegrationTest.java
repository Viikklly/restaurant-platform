package com.restaurant.payment_service.controller;

import com.netflix.discovery.EurekaClient;
import com.restaurant.payment_service.entity.PaymentTransaction;
import com.restaurant.payment_service.repository.PaymentTransactionRepository;
import com.restaurant.payment_service.service.RedisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)     /// Загружает полный Spring контекст для интеграционного теста на рандомном порте
@ActiveProfiles("test")                 /// Активируем профиль "test"
@TestPropertySource(properties = {      /// Переопределяем свойства для теста (отключаем Eureka, Config Server)
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.cloud.config.enabled=false",
        "spring.jpa.hibernate.ddl-auto=update"
})
@Testcontainers                 /// Включаем поддержку Docker-контейнеров для тестов
class PaymentControllerIntegrationTest {

    /// Создаем замоканый объект, чтобы Spring не искал реальный
    @MockitoBean
    private EurekaClient eurekaClient;

    /// запускаем PostgreSQL
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("payment_test")
            .withUsername("test")
            .withPassword("test");

    /// запускаем Redis
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    /// Подменяем реальные настройки на тестовые
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    /// Случайный порт, на котором запустилось приложение
    @LocalServerPort
    private int port;

    /// Случайный порт, на котором запустилось приложение
    @Autowired
    private TestRestTemplate restTemplate;

    /// Работа с БД
    @Autowired
    private PaymentTransactionRepository repository;

    /// Работа с кэшем
    @Autowired
    private RedisService redisService;

    private String baseUrl;

    /// Перед каждым тестом
    @BeforeEach
    void setUp() {
        /// Очищаем БД
        repository.deleteAll();
        /// Очищаем кэш
        redisService.delete("payments:all");
        /// Формируем URL
        baseUrl = "http://localhost:" + port + "/api/payments";
    }

    /// Создание транзакции
    private PaymentTransaction createTransaction(Long orderId, Long userId,
                                                 BigDecimal amount, String status) {
        return PaymentTransaction.builder()
                .orderId(orderId)
                .userId(userId)
                .amount(amount)
                .statusPayment(status)
                .message("Test message")
                .createdAt(LocalDateTime.now())
                .build();
    }

    /// Пустой список
    @Test
    @DisplayName("GET /api/payments - должен вернуть пустой список, когда транзакций нет")
    void getAllTransactions_ShouldReturnEmptyList() {
        ResponseEntity<List<PaymentTransaction>> response = restTemplate.exchange(
                baseUrl,                    /// URL
                HttpMethod.GET,             /// HTTP метод
                null,                       /// Тело запроса
                /// ParameterizedTypeReference:
                /// Сохраняет информацию о типе во время выполнения
                /// Позволяет десериализовать JSON в List<PaymentTransaction>
                new ParameterizedTypeReference<List<PaymentTransaction>>() {} /// JSON в List<PaymentTransaction>
        );

        /// Проверяем статуса ответа
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        /// Проверяем, что список пустой
        assertThat(response.getBody()).isEmpty();
    }

    /// Список всех транзакций
    @Test
    @DisplayName("GET /api/payments - должен вернуть все сохраненные транзакции")
    void getAllTransactions_ShouldReturnAllTransactions() {
        /// Создаем две транзакции в БД
        PaymentTransaction tx1 = createTransaction(1001L, 2001L,
                BigDecimal.valueOf(5000), "SUCCESS");
        PaymentTransaction tx2 = createTransaction(1002L, 2002L,
                BigDecimal.valueOf(3000), "FAILED");
        repository.saveAll(List.of(tx1, tx2));

        /// Отправляем GET запрос
        ResponseEntity<List<PaymentTransaction>> response = restTemplate.exchange(
                baseUrl,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<PaymentTransaction>>() {}
        );

        /// Проверяем, что статус OK
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        /// Проверяем, что в ответе ровно 2 элемента
        assertThat(response.getBody()).hasSize(2);
        /// Проверяем, что есть ID: 1001 и 1002
        assertThat(response.getBody())
                .extracting("orderId")
                .containsExactlyInAnyOrder(1001L, 1002L);
        /// Проверяем, что есть "SUCCESS" и "FAILED"
        assertThat(response.getBody())
                .extracting("statusPayment")
                .containsExactlyInAnyOrder("SUCCESS", "FAILED");
    }


    /// Успешная транзакция
    @Test
    @DisplayName("GET /api/payments/{id} - должен вернуть транзакцию по существующему ID")
    void getTransactionById_ShouldReturnTransaction() {
        BigDecimal amount = BigDecimal.valueOf(7000);
        /// Создаем транзакцию
        PaymentTransaction tx = createTransaction(1003L, 2003L,
                amount, "SUCCESS");
        PaymentTransaction saved = repository.save(tx);
        Long id = saved.getId();

        /// Отправляем GET запрос с ID
        ResponseEntity<PaymentTransaction> response = restTemplate.getForEntity(
                baseUrl + "/" + id,
                PaymentTransaction.class
        );

        /// Проверяем, что статус OK
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        /// Тело ответа не null
        assertThat(response.getBody()).isNotNull();
        /// ID ответа = ожидаемому значению (переменная id)
        assertThat(response.getBody().getId()).isEqualTo(id);
        /// ID заказа равен 1003
        assertThat(response.getBody().getOrderId()).isEqualTo(1003L);
        /// Сумма равна 7000
        assertThat(response.getBody().getAmount())
                .usingComparator(BigDecimal::compareTo)
                .isEqualTo(amount);
        /// Статус "SUCCESS"
        assertThat(response.getBody().getStatusPayment()).isEqualTo("SUCCESS");
    }

    /// Не найден
    @Test
    @DisplayName("GET /api/payments/{id} - должен вернуть ошибку 500 для несуществующего ID")
    void getTransactionById_ShouldThrowExceptionWhenNotFound() {
        ResponseEntity<PaymentTransaction> response = restTemplate.getForEntity(
                baseUrl + "/99999",
                PaymentTransaction.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }


    /// Успешное получения списка транзакций по ID заказа
    @Test
    @DisplayName("GET /api/payments/order/{orderId} - должен вернуть все транзакции по ID заказа")
    void getTransactionsByOrderId_ShouldReturnTransactions() {
        /// Создаем две тестовые транзакции с одинаковым orderId (1004)
        Long orderId = 1004L;
        PaymentTransaction tx1 = createTransaction(orderId, 2004L,
                BigDecimal.valueOf(1000), "SUCCESS");
        PaymentTransaction tx2 = createTransaction(orderId, 2004L,
                BigDecimal.valueOf(2000), "FAILED");
        repository.saveAll(List.of(tx1, tx2));

        /// Запрос
        ResponseEntity<List<PaymentTransaction>> response = restTemplate.exchange(
                baseUrl + "/order/" + orderId,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<PaymentTransaction>>() {}
        );

        /// Статус 200 OK
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        /// В ответе ровно 2 транзакции
        assertThat(response.getBody()).hasSize(2);
        /// У всех транзакций orderId равен 1004
        assertThat(response.getBody())
                .extracting("orderId")
                .containsOnly(orderId);
    }


    /// Для ID заказа не найдено ни одной транзакции
    @Test
    @DisplayName("GET /api/payments/order/{orderId} - должен вернуть пустой список, если транзакций для заказа нет")
    void getTransactionsByOrderId_ShouldReturnEmptyList() {
        /// Запрос
        ResponseEntity<List<PaymentTransaction>> response = restTemplate.exchange(
                baseUrl + "/order/99999",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<PaymentTransaction>>() {}
        );

        /// Статус 200 OK
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        /// Тело ответа - пустой список
        assertThat(response.getBody()).isEmpty();
    }

    /// Получение списка транзакций по ID пользователя
    @Test
    @DisplayName("GET /api/payments/user/{userId} - должен вернуть все транзакции по ID пользователя")
    void getTransactionsByUserId_ShouldReturnTransactions() {
        // Создаем 2 транзакции с userId 2005
        Long userId = 2005L;
        PaymentTransaction tx1 = createTransaction(1005L, userId,
                BigDecimal.valueOf(1500), "SUCCESS");
        PaymentTransaction tx2 = createTransaction(1006L, userId,
                BigDecimal.valueOf(2500), "SUCCESS");
        repository.saveAll(List.of(tx1, tx2));

        // Запрос
        ResponseEntity<List<PaymentTransaction>> response = restTemplate.exchange(
                baseUrl + "/user/" + userId,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<PaymentTransaction>>() {}
        );

        /// Ответ 200
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        /// Ответ не пустой, 2 транзакции
        List<PaymentTransaction> transactions = response.getBody();
        assertThat(transactions)
                .isNotNull()
                .hasSize(2);

        /// обе транзакции с user Id 2005
        assertThat(transactions)
                .extracting(PaymentTransaction::getUserId)
                .containsOnly(userId);

        /// Заказы с id 1005 1006
        assertThat(transactions)
                .extracting(PaymentTransaction::getOrderId)
                .containsExactlyInAnyOrder(1005L, 1006L);

        /// Правильные суммы
        assertThat(transactions)
                .extracting(PaymentTransaction::getAmount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactlyInAnyOrder(
                        BigDecimal.valueOf(1500),
                        BigDecimal.valueOf(2500)
                );
    }

    /// Для user ID не найдено транзакций
    @Test
    @DisplayName("GET /api/payments/user/{userId} - должен вернуть пустой список, если транзакций для пользователя нет")
    void getTransactionsByUserId_ShouldReturnEmptyList() {

        Long nonExistentUserId = 88888L;

        /// нет транзакции для 88888
        List<PaymentTransaction> existing = repository.findByUserId(nonExistentUserId);
        assertThat(existing).isEmpty();

        /// Запрос
        ResponseEntity<List<PaymentTransaction>> response = restTemplate.exchange(
                baseUrl + "/user/" + nonExistentUserId,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<PaymentTransaction>>() {}
        );

        /// Статус 200
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        /// тело не null, список пустой
        assertThat(response.getBody())
                .isNotNull()
                .isEmpty();
    }

    /// Получение транзакции по ID
    @Test
    @DisplayName("GET /api/payments/{id} - должен вернуть транзакцию со всеми заполненными полями")
    void getTransactionById_ShouldReturnAllFields() {
        /// Создаем транзакцию
        Long orderId = 7777L;
        Long userId = 8888L;
        BigDecimal amount = BigDecimal.valueOf(1234.56);
        String status = "SUCCESS";
        String message = "Тест";

        PaymentTransaction tx = PaymentTransaction.builder()
                .orderId(orderId)
                .userId(userId)
                .amount(amount)
                .statusPayment(status)
                .message(message)
                .createdAt(LocalDateTime.now())
                .build();

        PaymentTransaction saved = repository.save(tx);
        Long id = saved.getId();

        /// запрос
        ResponseEntity<PaymentTransaction> response = restTemplate.getForEntity(
                baseUrl + "/" + id,
                PaymentTransaction.class
        );

        /// Статус Ок
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        PaymentTransaction result = response.getBody();

        /// не null
        assertThat(result).isNotNull();

        /// Проверяем, что все поля существуют
        assertThat(result.getId()).isEqualTo(id);
        assertThat(result.getOrderId()).isEqualTo(orderId);
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getAmount()).isEqualTo(amount);
        assertThat(result.getStatusPayment()).isEqualTo(status);
        assertThat(result.getMessage()).isEqualTo(message);
        assertThat(result.getCreatedAt()).isNotNull();
    }

    /// Проверка кэширования

    @Test
    @DisplayName("GET /api/payments - при повторном вызове данные должны браться из кэша Redis")
    void getAllTransactions_ShouldUseCacheOnSecondCall() {
        // Создаем данные
        PaymentTransaction tx = createTransaction(9999L, 8888L,
                BigDecimal.valueOf(5000), "SUCCESS");
        repository.save(tx);

        /// загружает из БД
        ResponseEntity<List<PaymentTransaction>> response1 = restTemplate.exchange(
                baseUrl,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<PaymentTransaction>>() {}
        );

        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response1.getBody()).hasSize(1);

        /// Проверяем, что кэш создан
        assertThat(redisService.hasKey("payments:all")).isTrue();

        /// Второй запрос - должен взять из кэша
        ResponseEntity<List<PaymentTransaction>> response2 = restTemplate.exchange(
                baseUrl,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<PaymentTransaction>>() {}
        );

        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response2.getBody()).hasSize(1);
        assertThat(response2.getBody())
                .usingRecursiveComparison()
                .isEqualTo(response1.getBody());
    }
}
