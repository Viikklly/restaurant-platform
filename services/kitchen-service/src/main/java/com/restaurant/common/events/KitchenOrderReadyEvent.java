package com.restaurant.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KitchenOrderReadyEvent {
    private Long orderId;
    private String ticketId;
    private List<String> orderItemsList; /// Список блюд заказа
    private String status;  // "READY", "COOKING"
}