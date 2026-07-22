package com.restaurant.kitchen_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit-тесты KitchenRedisService")
class KitchenRedisServiceUnitTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private KitchenRedisService redisService;

    private final Long TEST_ORDER_ID = 1L;
    private final String TEST_KEY = "kitchen:ticket:" + TEST_ORDER_ID;
    private final Object TEST_TICKET = new Object();

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("cacheTicket() - сохраняет тикет в Redis с TTL")
    void cacheTicket_ShouldSaveWithTTL() {

        redisService.cacheTicket(TEST_ORDER_ID, TEST_TICKET);

        /// Проверки
        verify(valueOperations).set(TEST_KEY, TEST_TICKET, 10, TimeUnit.MINUTES);
    }

    @Test
    @DisplayName("getCachedTicket() - возвращает тикет из Redis")
    void getCachedTicket_ShouldReturnTicket_WhenExists() {

        when(valueOperations.get(TEST_KEY)).thenReturn(TEST_TICKET);


        Optional<Object> result = redisService.getCachedTicket(TEST_ORDER_ID);

        /// Проверки
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(TEST_TICKET);
        verify(valueOperations).get(TEST_KEY);
    }

    @Test
    @DisplayName("getCachedTicket() - возвращает empty, если тикет не найден")
    void getCachedTicket_ShouldReturnEmpty_WhenNotFound() {

        when(valueOperations.get(TEST_KEY)).thenReturn(null);


        Optional<Object> result = redisService.getCachedTicket(TEST_ORDER_ID);

        /// Проверки
        assertThat(result).isEmpty();
        verify(valueOperations).get(TEST_KEY);
    }

    @Test
    @DisplayName("evictTicket() - удаляет тикет из Redis")
    void evictTicket_ShouldDeleteFromRedis() {

        redisService.evictTicket(TEST_ORDER_ID);

        /// Проверка
        verify(redisTemplate).delete(TEST_KEY);
    }

    @Test
    @DisplayName("cacheTicket() - сохраняет тикет с правильным TTL")
    void cacheTicket_ShouldSaveWithCorrectTTL() {

        Long expectedTTL = 10L;


        redisService.cacheTicket(TEST_ORDER_ID, TEST_TICKET);


        verify(valueOperations).set(TEST_KEY, TEST_TICKET, expectedTTL, TimeUnit.MINUTES);
    }

    @Test
    @DisplayName("getCachedTicket() - возвращает тикет и не удаляет его")
    void getCachedTicket_ShouldNotDeleteTicket() {

        when(valueOperations.get(TEST_KEY)).thenReturn(TEST_TICKET);


        Optional<Object> result = redisService.getCachedTicket(TEST_ORDER_ID);

        /// Проверки
        assertThat(result).isPresent();
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("evictTicket() - удаляет правильный ключ")
    void evictTicket_ShouldDeleteCorrectKey() {

        Long orderId = 1L;
        String expectedKey = "kitchen:ticket:" + orderId;


        redisService.evictTicket(orderId);

        /// Проверки
        verify(redisTemplate).delete(expectedKey);
        verify(redisTemplate, times(1)).delete(anyString());
    }

    @Test
    @DisplayName("evictTicket() - удаляет правильный ключ для разных заказов")
    void evictTicket_ShouldDeleteCorrectKeyForDifferentOrders() {

        Long orderId1 = 1L;
        Long orderId2 = 2L;
        String expectedKey1 = "kitchen:ticket:" + orderId1;
        String expectedKey2 = "kitchen:ticket:" + orderId2;


        redisService.evictTicket(orderId1);
        redisService.evictTicket(orderId2);

        /// Проверки
        verify(redisTemplate).delete(expectedKey1);
        verify(redisTemplate).delete(expectedKey2);
        verify(redisTemplate, times(2)).delete(anyString());
    }

    @Test
    @DisplayName("evictTicket() - удаляет ключ для заказа с большим ID")
    void evictTicket_ShouldDeleteKeyForLargeOrderId() {

        Long largeOrderId = 99999L;
        String expectedKey = "kitchen:ticket:" + largeOrderId;


        redisService.evictTicket(largeOrderId);


        verify(redisTemplate).delete(expectedKey);
    }

    @Test
    @DisplayName("getCachedTicket() - пробрасывает исключение при ошибке Redis")
    void getCachedTicket_ShouldThrowException_WhenRedisError() {

        when(valueOperations.get(TEST_KEY)).thenThrow(new RuntimeException("Redis error"));

        /// Вызываем и проверяем
        assertThatThrownBy(() -> redisService.getCachedTicket(TEST_ORDER_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Redis error");

        verify(valueOperations).get(TEST_KEY);
    }
}