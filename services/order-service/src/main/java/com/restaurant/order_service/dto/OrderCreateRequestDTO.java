package com.restaurant.order_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.io.Serializable;
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
    @NotNull(message = "userId не может быть null")
    private Long userId;

    /**
     * Список блюд, который хочет заказать юзер
     */
    @NotEmpty(message = "Список блюд не может быть пустым")
    @Size(min = 1, message = "Список блюд не может быть пустым")
    @Valid
    private List<ItemRequestDTO> items;

    @Data
    public static class ItemRequestDTO implements Serializable {
        @NotBlank(message = "Название блюда не может быть пустым")
        private String productName;

        @NotNull(message = "Количество не может быть null")
        @Min(value = 1, message = "Количество должно быть больше 0")
        private Integer quantity;
    }
}
