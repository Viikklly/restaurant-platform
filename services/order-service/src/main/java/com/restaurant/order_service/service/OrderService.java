package com.restaurant.order_service.service;

import com.restaurant.common.events.OrderCreatedEvent;
import com.restaurant.common.events.OrderPaidEvent;
import com.restaurant.common.events.PaymentProcessedEvent;
import com.restaurant.order_service.dto.ItemDTO;
import com.restaurant.order_service.dto.OrderCreateRequestDTO;
import com.restaurant.order_service.dto.OrderDetailsResponseDTO;
import com.restaurant.order_service.dto.OrderResponseDTO;
import com.restaurant.order_service.entity.Item;
import com.restaurant.order_service.entity.Order;
import com.restaurant.order_service.entity.OrderItem;
import com.restaurant.order_service.entity.OrderStatus;
import com.restaurant.order_service.repository.ItemRepository;
import com.restaurant.order_service.repository.OrderItemRepository;
import com.restaurant.order_service.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ItemRepository itemRepository;

    /**
     * СОЗДАНИЕ ЗАКАЗА (POST /api/orders)
     */
    @Transactional
    public OrderResponseDTO createOrder(OrderCreateRequestDTO request) {
        log.info("Начало создания заказа для userId: {}", request.getUserId());


        ///  Создаём заказ
        Order order = new Order();

        order.setUserId(request.getUserId());
        order.setStatus(OrderStatus.PENDING.name());
        /// Добавляем расчитанную стоимость
        order.setTotalAmount(calculateDishCost(request.getItems()));


        /// Сохраняем заказ
        Order savedOrder = orderRepository.save(order);
        log.info(" Заказ сохранён с ID: {}", savedOrder.getId());

        /// Создаём связи OrderItem (используя Item из БД)
        List<OrderItem> orderItems = createOrderItems(savedOrder, request.getItems());
        orderItemRepository.saveAll(orderItems);
        ///savedOrder.setOrderItems(orderItems);

        /// Формируем список блюд для кухни
        List<String> kitchenItemsList = getItemsListForKitchen(request.getItems());

        /// Отправляем событие в Kafka (заказ создан)
        OrderCreatedEvent event = new OrderCreatedEvent(
                savedOrder.getId(),
                savedOrder.getUserId(),
                kitchenItemsList,
                savedOrder.getTotalAmount(),
                savedOrder.getStatus()
        );
        kafkaTemplate.send("order-service.order.created", event);
        log.info(" Событие отправлено в топик 'order.created', ждем оплату заказа");

        return new OrderResponseDTO(
                savedOrder.getId(),
                savedOrder.getStatus(),
                kitchenItemsList,
                savedOrder.getTotalAmount(),
                savedOrder.getCreatedAt()
        );
    }







    /// Kafka ///

    /**
     * ОБРАБОТКА УСПЕШНОЙ ОПЛАТЫ
     * Статус: PENDING → PAID
     */
    @KafkaListener(topics = "payment-service.payment.success", groupId = "order-group")
    @Transactional
    public void handlePaymentSuccess(PaymentProcessedEvent event) {
        log.info("Получен SUCCESS для заказа #{}", event.getOrderId());

        Order order = orderRepository.findById(event.getOrderId())
                .orElseThrow(() -> new RuntimeException("Заказ не найден: " + event.getOrderId()));

        /// Меняем статус: PENDING → PAID
        order.setStatus(OrderStatus.PAID.name());

        orderRepository.save(order);
        log.info("Заказ #{}: статус изменён на {}", order.getId(), order.getStatus());

        /// Лист заказов для кухни
        List<String> orderItemsList = getOrderItemsList(order.getId());

        /// Отправляем событие на кухню.
        /// Отправляем OrderPaidEvent
        OrderPaidEvent paidEvent = new OrderPaidEvent(
                order.getId(),
                order.getUserId(),
                orderItemsList,
                order.getTotalAmount()
        );

        kafkaTemplate.send("order-service.order.paid", paidEvent);

        log.info(" Отправлено событие на кухню для заказа #{}", event.getOrderId());
    }

    /**
     * ОБРАБОТКА НЕУСПЕШНОЙ ОПЛАТЫ
     * Статус: PENDING → CANCELLED
     */
    @KafkaListener(topics = "payment-service.payment.failed", groupId = "order-group")
    @Transactional
    public void handlePaymentFailed(PaymentProcessedEvent event) {
        log.info("Получен FAILED для заказа #{}: {}", event.getOrderId(), event.getMessage());

        Order order = orderRepository.findById(event.getOrderId())
                .orElseThrow(() -> new RuntimeException("Заказ не найден: " + event.getOrderId()));

        /// Меняем статус: PENDING → CANCELLED
        order.setStatus(OrderStatus.CANCELLED.name());

        orderRepository.save(order);
        log.info(" Заказ #{}: статус изменён на {}", order.getId(), order.getStatus());
    }




    /**
     * КУХНЯ НАЧАЛА ГОТОВКУ
     * Статус: PAID → PREPARING
     */
    @KafkaListener(topics = "kitchen-service.cooking.started", groupId = "order-group")
    @Transactional
    public void handleCookingStarted(Long orderId) {
        log.info(" Кухня начала готовить заказ #{}", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Заказ не найден: " + orderId));

        order.setStatus(OrderStatus.PREPARING.name());

        orderRepository.save(order);
        log.info(" Заказ #{}: статус изменён на {}", order.getId(), order.getStatus());
    }

    /**
     * ЗАКАЗ ГОТОВ
     * Статус: PREPARING → READY
     */
    @KafkaListener(topics = "kitchen-service.order.ready", groupId = "order-group")
    @Transactional
    public void handleOrderReady(Long orderId) {
        log.info("Заказ #{} готов к выдаче!", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Заказ не найден: " + orderId));

        order.setStatus(OrderStatus.READY.name());

        orderRepository.save(order);
        log.info(" Заказ #{}: статус изменён на {}", order.getId(), order.getStatus());
    }

    /**
     * ЗАКАЗ ДОСТАВЛЕН
     * Статус: READY → DELIVERED
     */
    @KafkaListener(topics = "delivery-service.order.delivered", groupId = "order-group")
    @Transactional
    public void handleOrderDelivered(Long orderId) {
        log.info(" Заказ #{} доставлен клиенту!", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Заказ не найден: " + orderId));

        order.setStatus(OrderStatus.DELIVERED.name());
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        log.info("Заказ #{}: статус изменён на {}", order.getId(), order.getStatus());
    }





    /**
     * Получает сущность Order из БД
     */
    public Order getOrderEntity(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Заказ не найден: " + id));
    }


    /**
     * Получает OrderDetailsResponseDTO
     */
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



    /**
     * Рассчитывает стоимость заказа
     */
    public BigDecimal calculateDishCost(List<ItemDTO> items) {
        if (items == null || items.isEmpty()) {
            return BigDecimal.ZERO;
        }

        /// Получаем Map: название блюда -> цена из БД
        Map<String, BigDecimal> prices = getPricesFromDB(items);

        /// Суммируем: цена * количество
        return items.stream()
                .filter(dto -> dto.getProductName() != null && dto.getQuantity() != null)
                .map(dto -> prices.get(dto.getProductName())
                        .multiply(BigDecimal.valueOf(dto.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }





    /**
     * Создаёт связи OrderItem между заказом и существующими блюдами из БД (не создаёт новые блюда!)
     * Каждый OrderItem = одна единица блюда
     */
    private List<OrderItem> createOrderItems(Order order, List<ItemDTO> items) {
        if (items == null || items.isEmpty()) {
            return new ArrayList<>();
        }


        /// Получаем Map блюд из БД (ключ = название блюда)
        Map<String, Item> itemMap = fetchItemMap(items);

        /// Создаём связи (не создаём новые Item!)
        List<OrderItem> orderItems = new ArrayList<>();

        for (ItemDTO itemDTO : items) {
            /// Пропускаем некорректные DTO
            if (itemDTO.getProductName() == null || itemDTO.getQuantity() == null) {
                continue;
            }

            /// Находим блюдо в БД
            Item item = itemMap.get(itemDTO.getProductName());
            if (item == null) {
                throw new RuntimeException("Блюдо не найдено: " + itemDTO.getProductName());
            }

            /// Создаём связь для каждой единицы (quantity раз)
            for (int i = 0; i < itemDTO.getQuantity(); i++) {
                OrderItem orderItem = new OrderItem();
                orderItem.setOrder(order);
                orderItem.setItem(item);
                orderItems.add(orderItem);
            }
        }

        return orderItems;
    }



    /**
     * Загружает цены блюд из БД
     */
    private Map<String, BigDecimal> getPricesFromDB(List<ItemDTO> items) {
        List<String> names = items.stream()
                .map(ItemDTO::getProductName)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (names.isEmpty()) {
            return Map.of();
        }

        return itemRepository.findAllByItemNameIn(names).stream()
                .collect(Collectors.toMap(Item::getItemName, Item::getItemPrice));
    }

    /**
     * Загружает блюда из БД и возвращает Map (название -> Item)
     */
    private Map<String, Item> fetchItemMap(List<ItemDTO> items) {
        /// Извлекаем уникальные названия блюд
        List<String> productNames = items.stream()
                .map(ItemDTO::getProductName)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (productNames.isEmpty()) {
            return Map.of();
        }

        /// Один запрос в БД
        return itemRepository.findAllByItemNameIn(productNames).stream()
                .collect(Collectors.toMap(Item::getItemName, Function.identity()));
    }


    /**
     * Извлекает названия блюд из списка ItemDTO
     */
    private List<String> extractProductNames(List<ItemDTO> items) {
        return items.stream()
                .map(ItemDTO::getProductName)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Для кухни: список блюд, где каждое блюдо повторяется quantity раз
     * Пример: [Пицца, Пицца, Паста] - значит готовить 2 пиццы и 1 пасту
     */
    private List<String> getItemsListForKitchen(List<ItemDTO> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }

        return items.stream()
                .filter(item -> item != null && item.getQuantity() != null && item.getQuantity() > 0)
                .flatMap(item -> IntStream.range(0, item.getQuantity())
                        .mapToObj(i -> item.getProductName()))
                .collect(Collectors.toList());
    }

    /**
     * Получает список блюд для заказа по Order ID
     */
    public List<String> getOrderItemsList(Long orderId) {
        Order order = getOrderEntity(orderId);

        /// Получаем все OrderItem для заказа
        List<OrderItem> orderItems = orderItemRepository.findByOrderId(orderId);

        if (orderItems == null || orderItems.isEmpty()) {
            return Collections.emptyList();
        }

        /// Преобразуем в список названий блюд
        return orderItems.stream()
                .map(orderItem -> orderItem.getItem().getItemName())
                .collect(Collectors.toList());
    }

}