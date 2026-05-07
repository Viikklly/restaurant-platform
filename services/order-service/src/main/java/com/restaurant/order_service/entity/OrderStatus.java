package com.restaurant.order_service.entity;

public enum OrderStatus {
    PENDING,    // Ожидает оплаты
    PAID,       // Оплачен
    CANCELLED,  // Отменён
    PREPARING,  // Готовится
    READY,      // Готов к выдаче
    DELIVERED   // Доставлен
}
