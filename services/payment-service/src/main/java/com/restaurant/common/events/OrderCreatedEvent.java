package com.restaurant.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreatedEvent {
    private Long orderId;
    private Long userId;
    private List<String> orderItemsList;  // список блюд для кухни
    private BigDecimal totalAmount;
    private String status;  // PENDING
}
