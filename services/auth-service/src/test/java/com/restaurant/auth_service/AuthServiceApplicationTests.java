package com.restaurant.auth_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class AuthServiceApplicationTests {

    /**
     * Заглушка Kafka — в auth-service KafkaTemplate используется в KafkaMetrics,
     * но реальный брокер в тесте не нужен.
     */
    @MockitoBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * PostgreSQL-контейнер для теста.
     * Поднимается один раз на весь класс.
     */
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("auth_test")
            .withUsername("test")
            .withPassword("test");

    /**
     * Динамически подставляем параметры подключения из контейнера
     * и отключаем всю внешнюю инфраструктуру (Config Server, Eureka, Liquibase).
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // ─── DataSource ──────────────────────────────────────────
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        // ─── JPA ─────────────────────────────────────────────────
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.PostgreSQLDialect");

        // ─── Liquibase выключаем — схему создаст Hibernate ───────
        registry.add("spring.liquibase.enabled", () -> "false");

        // ─── Config Server / Eureka выключаем ────────────────────
        registry.add("spring.cloud.config.enabled", () -> "false");
        registry.add("spring.cloud.config.import-check.enabled", () -> "false");
        registry.add("spring.cloud.discovery.enabled", () -> "false");
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("eureka.client.register-with-eureka", () -> "false");
        registry.add("eureka.client.fetch-registry", () -> "false");

        // ─── JWT (иначе JwtService не поднимется) ────────────────
        registry.add("jwt.secret",
                () -> "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
        registry.add("jwt.expiration", () -> "900000");
        registry.add("jwt.refresh-expiration", () -> "604800000");

        // ─── JWT-фильтр выключен (ConditionalOnProperty) ─────────
        registry.add("app.security.jwt-filter-enabled", () -> "false");
    }

    @Test
    void contextLoads() {
    }
}