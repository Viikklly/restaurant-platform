package com.restaurant.order_service.dto;

import lombok.Data;
import lombok.Getter;

/**
 * ItemDTO
 */
@Data
public class ItemDTO {
    /**
     * Название блюда
     */
    private String productName;

    /**
     * Кол-во порций блюда
     */
    private Integer quantity;
}
