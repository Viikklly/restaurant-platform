package com.restaurant.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Событие оплаченного заказа
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderPaidEvent {

    /**
     * ID заказа, который оплачен
     */
    private Long orderId;

    /**
     * ID пользователя, сделавшего заказ
     */
    private Long userId;

    /**
     * Сумма заказа
     */
    private BigDecimal totalAmount;
}