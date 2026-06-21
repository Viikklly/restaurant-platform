package com.restaurant.order_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OrderResponseDTO implements Serializable {
    private Long id;
    private Long userId;
    private String status;
    private List<String> items;
    private BigDecimal totalAmount;
    private LocalDateTime createdAt;
}