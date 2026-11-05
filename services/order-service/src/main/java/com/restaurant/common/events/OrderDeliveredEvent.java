package com.restaurant.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Событие "заказ доставлен клиенту"
 * delivery-service → order-service (топик delivery-service.order.delivered)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderDeliveredEvent {
    private Long orderId;
}