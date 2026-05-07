# Резюме диалога (Cursor) — restaurant-platform

Последнее обновление в контексте сессии: 2026-05-07 23:14 (UTC+3).

## Docker и запуск

- Проанализированы `.env`, `docker-compose.yml`, `docker-compose.infra.yml`.
- Полный стек поднимается через `docker compose up -d --build`; микросервисы собираются из `target/*.jar` (Maven внутри образа `maven` или локально).
- Инфраструктура только БД/Kafka: `docker compose -f docker-compose.infra.yml up -d`.
- Возможные проблемы при запуске: конфликт порта **8761**, временные ошибки Docker Hub (**unexpected EOF** при pull), пустой `infrastructure/config-repo`.
- Конфиги Spring Cloud Config монтируются из `./infrastructure/config-repo` в `config-service` (`file:///config-repo`). После правок: `docker compose restart <service>` (Config Server обычно перезапуска не требует — читает с тома).

## Gateway и авторизация (история)

- Изначально `gateway-service` отвечал 401, т.к. `SecurityConfig` имел `anyExchange().authenticated()` без Resource Server / JWT-конвертера.
- Промежуточное решение: `permitAll()` на уровне Spring Security Gateway, проверка JWT через `JwtAuthenticationFilter` в маршрутах.
- Конфиг маршрутов Gateway взят из `infrastructure_2/config-repo/gateway-service.yml` и положен в `infrastructure/config-repo/gateway-service.yml`.

## Регистрация (фикс 403)

- Причина **403** на `POST /auth/register`: в **auth-service** `SecurityConfig` была сломанная цепочка (`.anyRequest().permitAll()` + лишний `.authenticated()`).
- Промежуточно: `/auth/**` и `/actuator/**` → `permitAll()`, остальное → `authenticated()` (после этого регистрация заработала).

## Конфиги Spring Cloud Config (БД и порты сервисов)

Добавлены в `infrastructure/config-repo/` для устранения «Failed to determine a suitable driver class» и неверного порта (`8080` вместо `8082` и т.д.):

- `auth-service.yml` — порт **8081**, `jdbc:postgresql://postgres-auth:5432/authdb`.
- `order-service.yml` — порт **8082**, `jdbc:postgresql://postgres-order:5432/orderdb`.
- `kitchen-service.yml` — порт **8083**, `jdbc:postgresql://postgres-kitchen:5432/kitchendb`.
- `payment-service.yml` — порт **8084**, `jdbc:postgresql://postgres-payment:5432/paymentdb`.

Во всех — `spring.kafka.bootstrap-servers: kafka:29092`, `spring.jpa.hibernate.ddl-auto: update`, диалект PostgreSQL.

## Полное отключение авторизации (текущее состояние)

В режиме разработки авторизация выключена:

- **API Gateway** (`infrastructure/config-repo/gateway-service.yml`, `infrastructure_2/...`):
  - С маршрутов `order-service`, `kitchen-service`, `payment-service` убран фильтр `JwtAuthenticationFilter`.
  - `gateway-service/config/SecurityConfig.java` — `anyExchange().permitAll()`.
  - Бин `gateway-service/filter/JwtAuthenticationFilter` помечен `@ConditionalOnProperty("app.gateway.jwt-filter-enabled" = true, matchIfMissing = false)`. По умолчанию **не создаётся**.

- **auth-service** (`auth-service/config/SecurityConfig.java`):
  - `anyRequest().permitAll()`, JWT-фильтр убран из цепочки.
  - `auth-service/security/JwtAuthenticationFilter` помечен `@ConditionalOnProperty("app.security.jwt-filter-enabled" = true, matchIfMissing = false)` — глобальный `@Component`-фильтр **не регистрируется**.

- В `order-service`, `kitchen-service`, `payment-service` собственного Spring Security нет — проверка шла только через Gateway.

### Как вернуть защиту

1. Gateway: в Config Server (`gateway-service.yml`) задать `app.gateway.jwt-filter-enabled: true` и снова добавить `- name: JwtAuthenticationFilter` в нужных маршрутах.
2. auth-service: задать `app.security.jwt-filter-enabled: true`, в `SecurityConfig` вернуть `addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)` и `authenticated()` для приватных URL.

## Как зарегистрироваться

```http
POST http://localhost:8080/auth/register
Content-Type: application/json

{
  "email": "user@example.com",
  "username": "nickname",
  "password": "secret12"
}
```

Ограничения: email валидный, username 3–50 символов, пароль минимум 6 символов. Ответ содержит `accessToken`, `refreshToken`, `tokenType`.

## Как создать заказ

В текущей конфигурации `Authorization` **не требуется** (см. раздел выше).

```http
POST http://localhost:8080/api/orders
Content-Type: application/json

{
  "userId": 1,
  "totalAmount": 1500,
  "items": [
    { "productName": "Пицца", "quantity": 2, "price": 750 }
  ]
}
```

- `totalAmount` ≥ **1** (`@Min(1)` для `BigDecimal`).
- `items` в текущем сервисе **не сохраняются** в БД (логируется предупреждение).
- `userId` задаётся в теле; нужен реальный id пользователя.

## Логи (Config Server, Zookeeper, Spring Security)

- Повторяющееся чтение `application.yml` в `config-service` — норма при частых обращениях клиентов.
- Zookeeper «Unable to read additional data … session = 0x0» — типичный INFO коротких подключений. При Kafka на **KRaft** в основном compose контейнер Zookeeper не нужен.
- DEBUG `org.springframework.security` (например, `Authorization successful`) — штатно. Убрать: `logging.level.org.springframework.security: INFO` в конфиге `gateway-service`.

## Полезные порты

| Сервис | URL |
|--------|-----|
| Gateway | http://localhost:8080 |
| Eureka (Discovery) | http://localhost:8761 |
| Config Server | http://localhost:8888 |
| Auth | http://localhost:8081 |
| Order | http://localhost:8082 |
| Kitchen | http://localhost:8083 |
| Payment | http://localhost:8084 |
| Kafka UI | http://localhost:8085 |
| pgAdmin | http://localhost:5050 |
| Postgres (auth/order/kitchen/payment) | 5437 / 5433 / 5434 / 5435 |
| Redis | 6379 |
| Kafka (host) | 9092 |

---

*Файл обновляется по запросу пользователя.*
