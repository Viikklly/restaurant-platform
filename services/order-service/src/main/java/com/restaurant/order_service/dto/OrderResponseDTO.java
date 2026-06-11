package com.restaurant.order_service.dto;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderResponseDTO {
    private Long id;
    private String status;
    private List<String> itemsList;
    private BigDecimal totalAmount;
    private LocalDateTime createdAt;
}