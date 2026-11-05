package com.restaurant.order_service.service;

import com.restaurant.order_service.entity.Order;
import com.restaurant.order_service.entity.OrderStatus;
import com.restaurant.order_service.exception.OrderNotFoundException;
import com.restaurant.order_service.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Отдельный сервис для обновления статуса + evict кэша.
 * - @CacheEvict + @Transactional на одном методе — порядок AOP гарантирован:
 *   сначала commit транзакции, потом evict кэша.
 * - На @KafkaListener + @Transactional порядок НЕ гарантирован.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderStatusUpdater {

    private final OrderRepository orderRepository;

    /**
     * Обновить статус + очистить кэш.
     * Если заказ не найден — OrderNotFoundException (listener пробросит → retry).
     */
    @Transactional
    @CacheEvict(value = {"orders", "orderItems"}, key = "#orderId")
    public void updateStatusAndEvict(Long orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        order.setStatus(newStatus.name());
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        log.info("Заказ #{}: статус изменён на {}", orderId, newStatus);
    }

    /**
     * Обновить статус и вернуть order (нужно для отправки Kafka-события).
     */
    @Transactional
    @CacheEvict(value = {"orders", "orderItems"}, key = "#orderId")
    public Order updateStatusAndReturn(Long orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        order.setStatus(newStatus.name());
        order.setUpdatedAt(LocalDateTime.now());
        Order saved = orderRepository.save(order);
        log.info("Заказ #{}: статус изменён на {}", orderId, newStatus);
        return saved;
    }
}