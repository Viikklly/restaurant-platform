package com.restaurant.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Событие, которое отправляет Order Service после успешной оплаты
 * Заказ оплачен, можно отправлять на кухню
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderPaidEvent {
    private Long orderId;
    private Long userId;
    private List<String> orderItemsList;  // для кухни
    private BigDecimal totalAmount;
}