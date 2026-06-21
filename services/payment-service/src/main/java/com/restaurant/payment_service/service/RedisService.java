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
     * Сохранить в кэш
     */
    public void save(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
        log.debug("Сохранено в Redis: {}", key);
    }

    /**
     * Сохранить с временем жизни (секунды)
     * "страховка" на случай, если забыть где-то очистить кэш
     */
    public void saveWithExpire(String key, Object value, long seconds) {
        redisTemplate.opsForValue().set(key, value, seconds, TimeUnit.SECONDS);
        log.debug("Сохранено в Redis на {} секунд: {}", seconds, key);
    }

    /**
     * Получить из кэша
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Object value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return null;
        }
        return (T) value;
    }

    /**
     * Удалить из кэша
     */
    public void delete(String key) {
        redisTemplate.delete(key);
        log.debug("Удалено из Redis: {}", key);
    }


}