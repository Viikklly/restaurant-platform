package com.restaurant.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Событие оплаты
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentProcessedEvent {

    /**
     * ID заказа
     */
    private Long orderId;

    /**
     * Статус: "SUCCESS" или "FAILED"
     */
    private String status;

    /**
     * Сообщение
     */
    private String message;
}