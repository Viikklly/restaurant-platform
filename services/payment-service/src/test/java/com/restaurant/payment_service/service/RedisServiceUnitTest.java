package com.restaurant.payment_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit-тесты RedisService")
class RedisServiceUnitTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private RedisService redisService;

    private final String TEST_KEY = "test:key";
    private final String TEST_VALUE = "Hello, Redis!";

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("save() - сохраняет значение в Redis")
    void save_ShouldSaveValue() {
        redisService.save(TEST_KEY, TEST_VALUE);

        verify(valueOperations).set(TEST_KEY, TEST_VALUE);
    }

    @Test
    @DisplayName("saveWithExpire() - сохраняет значение с TTL")
    void saveWithExpire_ShouldSaveWithTTL() {
        long ttlSeconds = 120;

        redisService.saveWithExpire(TEST_KEY, TEST_VALUE, ttlSeconds);

        verify(valueOperations).set(TEST_KEY, TEST_VALUE, ttlSeconds, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("get() - возвращает значение из Redis")
    void get_ShouldReturnValue_WhenExists() {
        when(valueOperations.get(TEST_KEY)).thenReturn(TEST_VALUE);

        Object result = redisService.get(TEST_KEY);

        assertThat(result).isEqualTo(TEST_VALUE);
        verify(valueOperations).get(TEST_KEY);
    }

    @Test
    @DisplayName("get() - возвращает null, если ключ не существует")
    void get_ShouldReturnNull_WhenKeyNotExists() {
        when(valueOperations.get(TEST_KEY)).thenReturn(null);

        Object result = redisService.get(TEST_KEY);

        assertThat(result).isNull();
        verify(valueOperations).get(TEST_KEY);
    }

    @Test
    @DisplayName("get(key, Class<T>) - возвращает значение с приведением типа")
    void getWithType_ShouldReturnCastedValue() {
        when(valueOperations.get(TEST_KEY)).thenReturn(TEST_VALUE);

        String result = redisService.get(TEST_KEY, String.class);

        assertThat(result).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("get(key, Class<T>) - возвращает null при несовместимом типе")
    void getWithType_ShouldReturnNull_WhenTypeMismatch() {
        when(valueOperations.get(TEST_KEY)).thenReturn(123);

        // Метод get() перехватывает ClassCastException и возвращает null
        String result = redisService.get(TEST_KEY, String.class);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("getList() - возвращает список из Redis")
    void getList_ShouldReturnList_WhenExists() {
        List<String> expectedList = List.of("item1", "item2", "item3");

        when(valueOperations.get(TEST_KEY)).thenReturn(expectedList);
        List<String> result = redisService.getList(TEST_KEY, String.class);

        assertThat(result).isNotNull().hasSize(3).containsExactly("item1", "item2", "item3");
    }

    @Test
    @DisplayName("getList() - возвращает null, если значение не является списком")
    void getList_ShouldReturnNull_WhenValueIsNotList() {
        when(valueOperations.get(TEST_KEY)).thenReturn("not a list");

        List<String> result = redisService.getList(TEST_KEY, String.class);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("getList() - возвращает null при несовместимом типе элементов")
    void getList_ShouldReturnNull_WhenElementTypeMismatch() {
        List<Integer> listWithIntegers = List.of(1, 2, 3);

        when(valueOperations.get(TEST_KEY)).thenReturn(listWithIntegers);

        List<String> result = redisService.getList(TEST_KEY, String.class);
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("getList() - возвращает пустой список, если сохранен пустой список")
    void getList_ShouldReturnEmptyList_WhenListIsEmpty() {
        List<String> emptyList = new ArrayList<>();

        when(valueOperations.get(TEST_KEY)).thenReturn(emptyList);

        List<String> result = redisService.getList(TEST_KEY, String.class);
        assertThat(result).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("delete() - удаляет ключ из Redis")
    void delete_ShouldDeleteKey() {
        redisService.delete(TEST_KEY);

        verify(redisTemplate).delete(TEST_KEY);
    }

    @Test
    @DisplayName("hasKey() - возвращает true, если ключ существует")
    void hasKey_ShouldReturnTrue_WhenKeyExists() {
        when(redisTemplate.hasKey(TEST_KEY)).thenReturn(true);

        boolean result = redisService.hasKey(TEST_KEY);

        assertThat(result).isTrue();
        verify(redisTemplate).hasKey(TEST_KEY);
    }

    @Test
    @DisplayName("hasKey() - возвращает false, если ключ не существует")
    void hasKey_ShouldReturnFalse_WhenKeyNotExists() {
        when(redisTemplate.hasKey(TEST_KEY)).thenReturn(false);

        boolean result = redisService.hasKey(TEST_KEY);

        assertThat(result).isFalse();
        verify(redisTemplate).hasKey(TEST_KEY);
    }
}