package com.restaurant.order_service.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class OrderDetailsResponseDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private String status;
    private BigDecimal totalAmount;
    private LocalDateTime createdAt;
    private List<OrderItemDto> items;

    @Data
    @Builder
    public static class OrderItemDto implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private Long id;
        private String productName;
        private Integer quantity;
        private BigDecimal price;
    }
}