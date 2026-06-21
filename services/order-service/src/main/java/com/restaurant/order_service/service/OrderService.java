package com.restaurant.order_service.service;

import com.restaurant.common.events.KitchenOrderReadyEvent;
import com.restaurant.common.events.OrderCreatedEvent;
import com.restaurant.common.events.OrderPaidEvent;
import com.restaurant.common.events.PaymentProcessedEvent;
import com.restaurant.order_service.dto.OrderCreateRequestDTO;
import com.restaurant.order_service.dto.OrderResponseDTO;
import com.restaurant.order_service.entity.Item;
import com.restaurant.order_service.entity.Order;
import com.restaurant.order_service.entity.OrderItem;
import com.restaurant.order_service.entity.OrderStatus;
import com.restaurant.order_service.exception.OrderNotFoundException;
import com.restaurant.order_service.repository.ItemRepository;
import com.restaurant.order_service.repository.OrderItemRepository;
import com.restaurant.order_service.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ItemRepository itemRepository;

    private final KafkaTemplate<String, Object> kafkaTemplate;


    private static final String TOPIC_ORDER_CREATED = "order-service.order.created";
    private static final String TOPIC_ORDER_PAID = "order-service.order.paid";
    private static final String TOPIC_PAYMENT_SUCCESS = "payment-service.payment.success";
    private static final String TOPIC_PAYMENT_FAILED = "payment-service.payment.failed";
    private static final String TOPIC_COOKING_STARTED = "kitchen-service.cooking.started";
    private static final String TOPIC_ORDER_READY = "kitchen-service.order.ready";
    private static final String TOPIC_ORDER_DELIVERED = "delivery-service.order.delivered";


    /**
     * СОЗДАНИЕ ЗАКАЗА
     */
    @Transactional
    public OrderResponseDTO createOrder(OrderCreateRequestDTO request) {
        log.info("📝 Создание заказа для userId: {}", request.getUserId());

        ///Проверяем блюда
        Map<String, Item> itemMap = fetchItemsMap(request);
        Map<String, BigDecimal> prices = extractPrices(itemMap);

        /// Рассчитываем стоимость
        BigDecimal totalAmount = calculateTotalAmount(request, prices);

        ///Создаём заказ
        Order savedOrder = createOrderEntity(request, totalAmount);

        /// Создаём связи с блюдами
        createOrderItems(savedOrder, request, itemMap);

        /// Формируем ответ
        List<String> kitchenItems = prepareKitchenItems(request);
        OrderResponseDTO response = buildResponseDTO(savedOrder, kitchenItems);

        /// Отправляем событие в Kafka
        sendOrderCreatedEvent(savedOrder, kitchenItems);

        log.info(" Заказ #{} успешно создан", savedOrder.getId());
        return response;
    }

    /**
     * ПОЛУЧЕНИЕ ЗАКАЗА
     * Автоматическое кэширование
     */
    @Cacheable(value = "orders", key = "#id", unless = "#result == null")
    @Transactional(readOnly = true)
    public OrderResponseDTO getFullOrder(Long id) {
        log.info(" Загрузка заказа #{} из БД, так как кэш пуст", id);

        Order order = getOrderEntity(id);
        List<String> items = getOrderItemsList(id);

        return buildResponseDTO(order, items);
    }

    /**
     * ПОЛУЧЕНИЕ СПИСКА БЛЮД ЗАКАЗА
     * Автоматическое кэширование
     */
    @Cacheable(value = "orderItems", key = "#orderId", unless = "#result.isEmpty()")
    @Transactional(readOnly = true)
    public List<String> getOrderItemsList(Long orderId) {
        log.info("Загрузка списка блюд для заказа #{} из БД (кэш пуст)", orderId);

        List<OrderItem> orderItems = orderItemRepository.findByOrderId(orderId);

        if (orderItems == null || orderItems.isEmpty()) {
            return Collections.emptyList();
        }

        return orderItems.stream()
                .map(orderItem -> orderItem.getItem() != null
                        ? orderItem.getItem().getItemName()
                        : null)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /// ----

    /**
     * Получить сущность Order
     */
    private Order getOrderEntity(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    /**
     * Получить Map блюд из БД
     */
    private Map<String, Item> fetchItemsMap(OrderCreateRequestDTO request) {
        List<String> productNames = request.getItems().stream()
                .map(OrderCreateRequestDTO.ItemRequestDTO::getProductName)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        if (productNames.isEmpty()) {
            return Collections.emptyMap();
        }

        return itemRepository.findAllByItemNameIn(productNames).stream()
                .collect(Collectors.toMap(Item::getItemName, Function.identity()));
    }

    /**
     * Извлечь цены из Map блюд
     */
    private Map<String, BigDecimal> extractPrices(Map<String, Item> itemMap) {
        Map<String, BigDecimal> prices = new HashMap<>();
        for (Map.Entry<String, Item> entry : itemMap.entrySet()) {
            prices.put(entry.getKey(), entry.getValue().getItemPrice());
        }
        return prices;
    }

    /**
     * Рассчитать общую стоимость
     */
    private BigDecimal calculateTotalAmount(OrderCreateRequestDTO request, Map<String, BigDecimal> prices) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            return BigDecimal.ZERO;
        }

        return request.getItems().stream()
                .filter(item -> item.getProductName() != null && item.getQuantity() != null)
                .map(item -> {
                    BigDecimal price = prices.get(item.getProductName());
                    if (price == null) {
                        throw new RuntimeException("Цена не найдена для блюда: " + item.getProductName());
                    }
                    return price.multiply(BigDecimal.valueOf(item.getQuantity()));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Создать сущность Order
     */
    private Order createOrderEntity(OrderCreateRequestDTO request, BigDecimal totalAmount) {
        Order order = new Order();
        order.setUserId(request.getUserId());
        order.setStatus(OrderStatus.PENDING.name());
        order.setTotalAmount(totalAmount);
        return orderRepository.save(order);
    }

    /**
     * Создать связи OrderItem
     */
    private void createOrderItems(Order order, OrderCreateRequestDTO request, Map<String, Item> itemMap) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            return;
        }

        List<OrderItem> orderItems = new ArrayList<>();

        for (OrderCreateRequestDTO.ItemRequestDTO itemDTO : request.getItems()) {
            if (itemDTO.getProductName() == null || itemDTO.getQuantity() == null) {
                continue;
            }

            Item item = itemMap.get(itemDTO.getProductName());
            if (item == null) {
                throw new RuntimeException("Блюдо не найдено: " + itemDTO.getProductName());
            }

            for (int i = 0; i < itemDTO.getQuantity(); i++) {
                OrderItem orderItem = new OrderItem();
                orderItem.setOrder(order);
                orderItem.setItem(item);
                orderItems.add(orderItem);
            }
        }

        if (!orderItems.isEmpty()) {
            orderItemRepository.saveAll(orderItems);
        }
    }

    /**
     * Подготовить список блюд для кухни
     */
    private List<String> prepareKitchenItems(OrderCreateRequestDTO request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            return Collections.emptyList();
        }

        return request.getItems().stream()
                .filter(item -> item != null && item.getQuantity() != null && item.getQuantity() > 0)
                .flatMap(item -> IntStream.range(0, item.getQuantity())
                        .mapToObj(i -> item.getProductName()))
                .collect(Collectors.toList());
    }

    /**
     * Построить DTO ответа
     */
    private OrderResponseDTO buildResponseDTO(Order order, List<String> items) {
        return OrderResponseDTO.builder()
                .id(order.getId())
                .userId(order.getUserId())
                .status(order.getStatus())
                .items(items)
                .totalAmount(order.getTotalAmount())
                .createdAt(order.getCreatedAt())
                .build();
    }

    /// KAFKA ОТПРАВКА

    /**
     * Отправить событие создания заказа
     */
    private void sendOrderCreatedEvent(Order order, List<String> items) {
        OrderCreatedEvent event = new OrderCreatedEvent(
                order.getId(),
                order.getUserId(),
                items,
                order.getTotalAmount(),
                order.getStatus()
        );

        kafkaTemplate.send(TOPIC_ORDER_CREATED, event);
        log.info("Событие OrderCreated отправлено в топик '{}'", TOPIC_ORDER_CREATED);
    }

    /**
     * Отправить событие оплаты на кухню
     */
    private void sendOrderPaidEvent(Order order) {
        List<String> items = getOrderItemsList(order.getId());

        OrderPaidEvent event = new OrderPaidEvent(
                order.getId(),
                order.getUserId(),
                items,
                order.getTotalAmount()
        );

        kafkaTemplate.send(TOPIC_ORDER_PAID, event);
        log.info(" Событие OrderPaid отправлено в топик '{}'", TOPIC_ORDER_PAID);
    }

    ///  KAFKA CONSUMERS

    /**
     * ОБРАБОТКА УСПЕШНОЙ ОПЛАТЫ
     * Статус: PENDING → PAID
     * @CacheEvict - автоматически удаляет кэш при изменении данных
     */
    @KafkaListener(topics = TOPIC_PAYMENT_SUCCESS, groupId = "order-group")
    @Transactional
    @CacheEvict(value = {"orders", "orderItems"}, key = "#event.orderId")
    public void handlePaymentSuccess(PaymentProcessedEvent event) {
        try {
            log.info(" Получен SUCCESS для заказа #{}", event.getOrderId());

            Order order = getOrderEntity(event.getOrderId());
            order.setStatus(OrderStatus.PAID.name());
            orderRepository.save(order);

            /// Отправляем событие на кухню
            sendOrderPaidEvent(order);

            log.info("Заказ #{}: статус изменён на {}", order.getId(), order.getStatus());

        } catch (OrderNotFoundException e) {
            log.error("Заказ #{} не найден при обработке оплаты", event.getOrderId());
        } catch (Exception e) {
            log.error("Ошибка обработки оплаты заказа #{}", event.getOrderId(), e);
        }
    }

    /**
     * ОБРАБОТКА НЕУСПЕШНОЙ ОПЛАТЫ
     * Статус: PENDING → CANCELLED
     * @CacheEvict - автоматически удаляет кэш
     */
    @KafkaListener(topics = TOPIC_PAYMENT_FAILED, groupId = "order-group")
    @Transactional
    @CacheEvict(value = {"orders", "orderItems"}, key = "#event.orderId")
    public void handlePaymentFailed(PaymentProcessedEvent event) {
        try {
            log.info(" Получен FAILED для заказа #{}: {}", event.getOrderId(), event.getMessage());

            Order order = getOrderEntity(event.getOrderId());
            order.setStatus(OrderStatus.CANCELLED.name());
            orderRepository.save(order);

            log.info(" Заказ #{}: статус изменён на {}", order.getId(), order.getStatus());

        } catch (OrderNotFoundException e) {
            log.error(" Заказ #{} не найден при обработке отмены", event.getOrderId());
        } catch (Exception e) {
            log.error(" Ошибка обработки отмены заказа #{}", event.getOrderId(), e);
        }
    }

    /**
     * КУХНЯ НАЧАЛА ГОТОВКУ
     * Статус: PAID → PREPARING
     */
    @KafkaListener(topics = TOPIC_COOKING_STARTED, groupId = "order-group")
    @Transactional
    @CacheEvict(value = {"orders", "orderItems"}, key = "#orderId")
    public void handleCookingStarted(Long orderId) {
        try {
            log.info("Кухня начала готовить заказ #{}", orderId);

            Order order = getOrderEntity(orderId);
            order.setStatus(OrderStatus.PREPARING.name());
            orderRepository.save(order);

            log.info("Заказ #{}: статус изменён на {}", order.getId(), order.getStatus());

        } catch (OrderNotFoundException e) {
            log.error("Заказ #{} не найден при начале готовки", orderId);
        } catch (Exception e) {
            log.error("Ошибка обработки начала готовки заказа #{}", orderId, e);
        }
    }

    /**
     * ЗАКАЗ ГОТОВ
     * Статус: PREPARING → READY
     */
    @KafkaListener(topics = TOPIC_ORDER_READY, groupId = "order-group")
    @Transactional
    @CacheEvict(value = {"orders", "orderItems"}, key = "#event.orderId")
    public void handleOrderReady(KitchenOrderReadyEvent event) {
        try {

           log.info("Заказ #{} готов к выдаче!", event.getOrderId());

            Order order = getOrderEntity(event.getOrderId());
            order.setStatus(OrderStatus.READY.name());
            order.setUpdatedAt(LocalDateTime.now());

            orderRepository.save(order);

            log.info("Заказ #{}: статус изменён на {}", order.getId(), order.getStatus());

        } catch (OrderNotFoundException e) {
            log.error("Заказ #{} не найден при получении статуса READY", event.getOrderId());
        } catch (Exception e) {
            log.error("Ошибка обработки готовности заказа #{}", event.getOrderId(), e);
        }
    }

    /**
     * ЗАКАЗ ДОСТАВЛЕН
     * Статус: READY → DELIVERED
     */
    @KafkaListener(topics = TOPIC_ORDER_DELIVERED, groupId = "order-group")
    @Transactional
    @CacheEvict(value = {"orders", "orderItems"}, key = "#orderId")
    public void handleOrderDelivered(Long orderId) {
        try {
            log.info("Заказ #{} доставлен клиенту!", orderId);

            Order order = getOrderEntity(orderId);
            order.setStatus(OrderStatus.DELIVERED.name());
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);

            log.info("Заказ #{}: статус изменён на {}", order.getId(), order.getStatus());

        } catch (OrderNotFoundException e) {
            log.error("Заказ #{} не найден при доставке", orderId);
        } catch (Exception e) {
            log.error("Ошибка обработки доставки заказа #{}", orderId, e);
        }
    }
}