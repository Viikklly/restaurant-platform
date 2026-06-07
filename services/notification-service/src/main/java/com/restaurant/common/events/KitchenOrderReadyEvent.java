package com.restaurant.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Событие, которое отправляет Kitchen Service когда заказ готов
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KitchenOrderReadyEvent {

    /**
     * ID заказа
     */
    private Long orderId;

    /**
     * ID тикета на кухне (кухонный чек)
     */
    private String ticketId;

    /**
     * Статус: "READY", "COOKING"
     */
    private String status;
}