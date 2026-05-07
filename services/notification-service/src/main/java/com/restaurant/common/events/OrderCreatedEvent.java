package com.restaurant.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreatedEvent {
    private Long orderId;      // ID созданного заказа
    private Long userId;       // Кто создал
    private BigDecimal totalAmount;  // Сумма заказа
    private String status;     // Текущий статус (PENDING)
}
