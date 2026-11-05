package com.restaurant.order_service.service;

import com.restaurant.common.events.CookingStartedEvent;
import com.restaurant.common.events.KitchenOrderReadyEvent;
import com.restaurant.common.events.OrderCreatedEvent;
import com.restaurant.common.events.OrderDeliveredEvent;
import com.restaurant.common.events.OrderPaidEvent;
import com.restaurant.common.events.PaymentProcessedEvent;
import com.restaurant.order_service.dto.OrderCreateRequestDTO;
import com.restaurant.order_service.dto.OrderResponseDTO;
import com.restaurant.order_service.entity.Item;
import com.restaurant.order_service.entity.Order;
import com.restaurant.order_service.entity.OrderItem;
import com.restaurant.order_service.entity.OrderStatus;
import com.restaurant.order_service.exception.OrderNotFoundException;
import com.restaurant.order_service.metrics.KafkaMetrics;
import com.restaurant.order_service.repository.ItemRepository;
import com.restaurant.order_service.repository.OrderItemRepository;
import com.restaurant.order_service.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.micrometer.core.instrument.Timer;

import java.math.BigDecimal;
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

    @Autowired
    private final KafkaMetrics kafkaMetrics; /// Метрики

    private final OrderStatusUpdater orderStatusUpdater;   ///


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

        log.info("Создание заказа для userId: {}", request.getUserId());

        ///проверка на пустой список
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new RuntimeException("Список блюд не может быть пустым");
        }

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

        /// Метрики
        kafkaMetrics.incrementProduced();
        kafkaMetrics.incrementOrderCreated();

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

        kafkaMetrics.incrementProduced();

        log.info(" Событие OrderPaid отправлено в топик '{}'", TOPIC_ORDER_PAID);
    }



    @RetryableTopic(   // @RetryableTopic — non-blocking retry + DLT
            attempts = "4",
            backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 10000),
            autoCreateTopics = "true",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltStrategy = DltStrategy.FAIL_ON_ERROR
    )
    @KafkaListener(topics = TOPIC_PAYMENT_SUCCESS, groupId = "order-group")
    @Transactional   // 🔧 для lazy: sendOrderPaidEvent трогает order.getItems()
    public void handlePaymentSuccess(PaymentProcessedEvent event) {

        Timer.Sample timer = kafkaMetrics.startProcessingTimer();

        try {
            log.info(" Получен SUCCESS для заказа #{}", event.getOrderId());
            kafkaMetrics.incrementConsumed();

            /// через updater (транзакция + evict кэша внутри)
            Order order = orderStatusUpdater.updateStatusAndReturn(
                    event.getOrderId(), OrderStatus.PAID);
            kafkaMetrics.incrementOrderPaid();

            /// Отправляем событие на кухню
            sendOrderPaidEvent(order);

            log.info("Заказ #{}: статус изменён на {}", order.getId(), order.getStatus());

        } catch (Exception e) {
            kafkaMetrics.incrementProcessingError();
            log.error("Ошибка обработки оплаты заказа #{}", event.getOrderId(), e);
            throw e;
        } finally {
            kafkaMetrics.stopProcessingTimer(timer);
        }
    }

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 10000),
            autoCreateTopics = "true",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltStrategy = DltStrategy.FAIL_ON_ERROR
    )
    @KafkaListener(topics = TOPIC_PAYMENT_FAILED, groupId = "order-group")
    public void handlePaymentFailed(PaymentProcessedEvent event) {

        Timer.Sample timer = kafkaMetrics.startProcessingTimer();

        try {
            log.info(" Получен FAILED для заказа #{}: {}", event.getOrderId(), event.getMessage());
            kafkaMetrics.incrementConsumed();

            /// через updater (транзакция + evict кэша внутри)
            orderStatusUpdater.updateStatusAndEvict(event.getOrderId(), OrderStatus.CANCELLED);
            kafkaMetrics.incrementOrderCancelled();

        } catch (Exception e) {
            kafkaMetrics.incrementProcessingError();
            log.error(" Ошибка обработки отмены заказа #{}", event.getOrderId(), e);
            throw e;
        } finally {
            kafkaMetrics.stopProcessingTimer(timer);
        }
    }

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 10000),
            autoCreateTopics = "true",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltStrategy = DltStrategy.FAIL_ON_ERROR
    )
    @KafkaListener(topics = TOPIC_COOKING_STARTED, groupId = "order-group")
    public void handleCookingStarted(CookingStartedEvent event) {   //  CookingStartedEvent / OrderDeliveredEvent вместо Long

        Timer.Sample timer = kafkaMetrics.startProcessingTimer();

        try {
            log.info("Кухня начала готовить заказ #{}", event.getOrderId());
            kafkaMetrics.incrementConsumed();

            /// через updater (транзакция + evict кэша внутри)
            orderStatusUpdater.updateStatusAndEvict(event.getOrderId(), OrderStatus.PREPARING);

        } catch (Exception e) {
            kafkaMetrics.incrementProcessingError();
            log.error("Ошибка обработки начала готовки заказа #{}", event.getOrderId(), e);
            throw e;
        } finally {
            kafkaMetrics.stopProcessingTimer(timer);
        }
    }

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 10000),
            autoCreateTopics = "true",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltStrategy = DltStrategy.FAIL_ON_ERROR
    )
    @KafkaListener(topics = TOPIC_ORDER_READY, groupId = "order-group")
    public void handleOrderReady(KitchenOrderReadyEvent event) {

        Timer.Sample timer = kafkaMetrics.startProcessingTimer();

        try {
            log.info("Заказ #{} готов к выдаче!", event.getOrderId());
            kafkaMetrics.incrementConsumed();

            /// через updater (транзакция + evict кэша внутри)
            orderStatusUpdater.updateStatusAndEvict(event.getOrderId(), OrderStatus.READY);
            kafkaMetrics.incrementOrderReady();

        } catch (Exception e) {
            kafkaMetrics.incrementProcessingError();
            log.error("Ошибка обработки готовности заказа #{}", event.getOrderId(), e);
            throw e;
        } finally {
            kafkaMetrics.stopProcessingTimer(timer);
        }
    }

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 10000),
            autoCreateTopics = "true",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltStrategy = DltStrategy.FAIL_ON_ERROR
    )
    @KafkaListener(topics = TOPIC_ORDER_DELIVERED, groupId = "order-group")
    public void handleOrderDelivered(OrderDeliveredEvent event) {   //  CookingStartedEvent / OrderDeliveredEvent вместо Long

        Timer.Sample timer = kafkaMetrics.startProcessingTimer();

        try {
            log.info("Заказ #{} доставлен клиенту!", event.getOrderId());
            kafkaMetrics.incrementConsumed();

            /// через updater (транзакция + evict кэша внутри)
            orderStatusUpdater.updateStatusAndEvict(event.getOrderId(), OrderStatus.DELIVERED);

        } catch (Exception e) {
            kafkaMetrics.incrementProcessingError();
            log.error("Ошибка обработки доставки заказа #{}", event.getOrderId(), e);
            throw e;
        } finally {
            kafkaMetrics.stopProcessingTimer(timer);
        }
    }



    //  DLT HANDLER метод, который «ловит» сообщения, с
    //  которыми не удалось справиться основному обработчику после всех повторных попыток.
    @DltHandler
    public void handleDlt(Object event) {
        if (event instanceof PaymentProcessedEvent e) {
            log.error(" DLT (payment): заказ #{} не обработан после всех retry. status={}",
                    e.getOrderId(), e.getStatus());
        } else if (event instanceof CookingStartedEvent e) {
            log.error(" DLT (cooking.started): заказ #{} не обработан после всех retry", e.getOrderId());
        } else if (event instanceof KitchenOrderReadyEvent e) {
            log.error(" DLT (order.ready): заказ #{} не обработан после всех retry. ticketId={}",
                    e.getOrderId(), e.getTicketId());
        } else if (event instanceof OrderDeliveredEvent e) {
            log.error(" DLT (order.delivered): заказ #{} не обработан после всех retry", e.getOrderId());
        } else {
            log.error(" DLT: неизвестный тип события: {}", event != null ? event.getClass() : "null");
        }
        kafkaMetrics.incrementProcessingError();

    }
}