package com.restaurant.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Событие "кухня начала готовить заказ"
 * kitchen-service → order-service (топик kitchen-service.cooking.started)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CookingStartedEvent {
    private Long orderId;
}