package com.restaurant.kitchen_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class KitchenRedisService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String TICKET_KEY_PREFIX = "kitchen:ticket:";
    private static final Long CACHE_TTL_MINUTES = 10L;

    /**
     * Сохранить тикет в кэш
     */
    public void cacheTicket(Long orderId, Object ticket) {
        String key = TICKET_KEY_PREFIX + orderId;
        redisTemplate.opsForValue().set(key, ticket, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        log.debug("Тикет для заказа #{} сохранён в Redis", orderId);
    }

    /**
     * Получить тикет из кэша
     */
    public Optional<Object> getCachedTicket(Long orderId) {
        String key = TICKET_KEY_PREFIX + orderId;
        Object ticket = redisTemplate.opsForValue().get(key);
        return Optional.ofNullable(ticket);
    }

    /**
     * Удалить тикет из кэша
     */
    public void evictTicket(Long orderId) {
        String key = TICKET_KEY_PREFIX + orderId;
        redisTemplate.delete(key);
        log.info("Тикет для заказа #{} удалён из Redis", orderId);
    }
}