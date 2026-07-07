package com.restaurant.order_service.exception;

public class OrderAccessDeniedException extends RuntimeException {

    public OrderAccessDeniedException(String message) {
        super(message);
    }

    public OrderAccessDeniedException(String message, Throwable cause) {
        super(message, cause);
    }
}
