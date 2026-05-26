package com.restaurant.gateway_service.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 *  Модель для общения с Auth Service
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthRequest {
    private String token;
}
