package com.restaurant.order_service.exception;

public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(Long orderId) {
        super("Заказ не найден: " + orderId);
    }
}
