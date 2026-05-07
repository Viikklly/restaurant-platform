package com.restaurant.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KitchenOrderReadyEvent {
    private Long orderId;
    private String ticketId;
    private String status;  // "READY", "COOKING"
}