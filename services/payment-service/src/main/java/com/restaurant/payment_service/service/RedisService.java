package com.restaurant.payment_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisService {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Сохранить в кэш без TTL
     */
    public void save(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
        log.debug("Redis: сохранён ключ '{}'", key);
    }

    /**
     * Сохранить в кэш с TTL (секунды)
     */
    public void saveWithExpire(String key, Object value, long seconds) {
        redisTemplate.opsForValue().set(key, value, seconds, TimeUnit.SECONDS);
        log.debug("Redis: сохранён ключ '{}' на {} сек", key, seconds);
    }

    /**
     * Получить объект из кэша
     */
    public Object get(String key) {
        Object value = redisTemplate.opsForValue().get(key);
        if (value != null) {
            log.debug("Redis: получен ключ '{}'", key);
        }
        return value;
    }

    /**
     * Получить объект из кэша с приведением типа
     */
    public <T> T get(String key, Class<T> type) {
        Object value = get(key);
        if (value == null) {
            return null;
        }
        try {
            /// используем type.cast() вместо (T) value
            return type.cast(value);
        } catch (ClassCastException e) {
            log.warn("Не удалось привести объект к типу {} для ключа '{}'",
                    type.getSimpleName(), key);
            return null;
        }
    }

    /**
     * Получить список из кэша с проверкой типа
     */
    @SuppressWarnings("unchecked")
    public <T> java.util.List<T> getList(String key, Class<T> elementType) {
        Object value = get(key);
        if (value == null) {
            return null;
        }
        try {
            if (value instanceof java.util.List<?>) {
                java.util.List<?> list = (java.util.List<?>) value;

                /// Если список пустой - возвращаем его
                if (list.isEmpty()) {
                    return (java.util.List<T>) list;
                }

                /// Проверяем, что первый элемент имеет правильный тип
                /// elementType.cast() для проверки
                Object first = list.get(0);
                try {
                    /// Пытаемся привести первый элемент к нужному типу
                    elementType.cast(first);
                    /// Если успешно - возвращаем список
                    return (java.util.List<T>) list;
                } catch (ClassCastException e) {
                    /// Если первый элемент не того типа - возвращаем null
                    log.warn("Элемент списка не соответствует типу {} для ключа '{}'",
                            elementType.getSimpleName(), key);
                    return null;
                }
            }
            return null;
        } catch (ClassCastException e) {
            log.warn("Не удалось привести список к типу {} для ключа '{}'",
                    elementType.getSimpleName(), key);
            return null;
        }
    }



    /**
     * Удалить ключ из кэша
     */
    public void delete(String key) {
        redisTemplate.delete(key);
        log.debug("Redis: удалён ключ '{}'", key);
    }

    /**
     * Проверить существование ключа
     */
    public boolean hasKey(String key) {
        Boolean hasKey = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(hasKey);
    }
}