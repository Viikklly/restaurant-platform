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
    private Long orderId;
    private String status;  // "SUCCESS" или "FAILED"
    private String message;  // сообщение об ошибке (опционально)
}