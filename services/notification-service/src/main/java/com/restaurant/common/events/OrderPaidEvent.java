package com.restaurant.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Событие, которое отправляет Order Service после успешной оплаты
 * Заказ оплачен, можно отправлять на кухню
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