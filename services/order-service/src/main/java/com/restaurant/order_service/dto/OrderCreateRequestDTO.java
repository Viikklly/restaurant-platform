package com.restaurant.order_service.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * RequestDTO заказа
 */
@Data
public class OrderCreateRequestDTO {

    /**
     * Идентификатор юзера, который совершает заказ
     */
    @NotNull(message = "userId не может быть null")
    private Long userId;

    /**
     * Список блюд, который хочет заказать юзер
     */
    @NotNull(message = "items(заказ) не может быть null")
    private List<ItemDTO> items;

}
