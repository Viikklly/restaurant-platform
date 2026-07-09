package com.restaurant.order_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * RequestDTO заказа
 */
@Data
public class OrderCreateRequestDTO implements Serializable {

    /**
     * Идентификатор юзера, который совершает заказ
     */
    /// Берется из заголовка
    ///@NotNull(message = "userId не может быть null")
    ///private Long userId;

    /**
     * Список блюд, который хочет заказать юзер
     */
    @NotEmpty(message = "Список блюд не может быть пустым")
    @Valid
    private List<ItemRequestDTO> items;

    @Data
    public static class ItemRequestDTO {
        private String productName;
        private Integer quantity;
    }
}
