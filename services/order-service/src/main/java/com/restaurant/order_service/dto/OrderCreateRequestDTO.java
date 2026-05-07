package com.restaurant.order_service.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class OrderCreateRequestDTO {

    @NotNull(message = "userId не может быть null")
    private Long userId;

    @NotNull(message = "totalAmount не может быть null")
    @Min(value = 1, message = "Сумма должна быть больше 0")
    private BigDecimal totalAmount;

    private List<OrderItemDto> items;

    @Data
    public static class OrderItemDto {
        private String productName;
        private Integer quantity;
        private BigDecimal price;
    }
}
