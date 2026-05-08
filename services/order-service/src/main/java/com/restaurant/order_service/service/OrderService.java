package com.restaurant.order_service.service;

import com.restaurant.common.events.OrderCreatedEvent;
import com.restaurant.common.events.PaymentProcessedEvent;
import com.restaurant.order_service.dto.OrderCreateRequestDTO;
import com.restaurant.order_service.dto.OrderDetailsResponseDTO;
import com.restaurant.order_service.dto.OrderResponseDTO;
import com.restaurant.order_service.entity.Order;
import com.restaurant.order_service.entity.OrderStatus;
import com.restaurant.order_service.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional
    public OrderResponseDTO createOrder(OrderCreateRequestDTO request) {
        log.info("📝 Начало создания заказа для userId: {}", request.getUserId());

        /// Создаём заказ в БД
        Order order = new Order();
        order.setUserId(request.getUserId());
        order.setStatus(OrderStatus.PENDING.name());
        order.setTotalAmount(request.getTotalAmount());
        order.setCreatedAt(LocalDateTime.now());

        /// Временно не сохраняем OrderItem - сначала запустим приложение
        if (request.getItems() != null && !request.getItems().isEmpty()) {
            log.warn(" OrderItems временно не сохраняются - будет добавлено позже");
        }

        Order savedOrder = orderRepository.save(order);
        log.info(" Заказ сохранён с ID: {}", savedOrder.getId());


        /// 2. Отправляем событие в Kafka
        OrderCreatedEvent event = new OrderCreatedEvent(
                savedOrder.getId(),
                savedOrder.getUserId(),
                savedOrder.getTotalAmount(),
                savedOrder.getStatus()
        );
        kafkaTemplate.send("order.created", event);
        log.info(" Событие отправлено в топик 'order.created'");

        return new OrderResponseDTO(
                savedOrder.getId(),
                savedOrder.getStatus(),
                savedOrder.getTotalAmount(),
                savedOrder.getCreatedAt()
        );
    }


    /// Слушаем результат оплаты
    @KafkaListener(topics = "payment.processed", groupId = "order-group")
    public void handlePaymentResult(PaymentProcessedEvent event) {
        log.info("📥 Получен результат оплаты для заказа #{}: {}",
                event.getOrderId(), event.getStatus());

        Order order = orderRepository.findById(event.getOrderId()).orElse(null);
        if (order == null) {
            log.error(" Заказ #{} не найден", event.getOrderId());
            return;
        }

        if ("SUCCESS".equals(event.getStatus())) {
            // Оплата успешна → заказ оплачен
            order.setStatus(OrderStatus.PAID.name());
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
            log.info("Заказ #{} оплачен, статус → PAID", event.getOrderId());

            // Отправляем событие на кухню
            kafkaTemplate.send("order.paid", event.getOrderId());
            log.info(" Отправлено событие на кухню для заказа #{}", event.getOrderId());

        } else {
            // Оплата не удалась → заказ отменён
            order.setStatus(OrderStatus.CANCELLED.name());
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
            log.error(" Заказ #{} отменён: {}", event.getOrderId(), event.getMessage());
        }
    }

    public Order getOrderEntity(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Заказ не найден: " + id));
    }

    public OrderDetailsResponseDTO getOrder(Long id) {
        Order order = getOrderEntity(id);

        return OrderDetailsResponseDTO.builder()
                .id(order.getId())
                .userId(order.getUserId())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .createdAt(order.getCreatedAt())
                // items пока пустой список
                .build();
    }
}