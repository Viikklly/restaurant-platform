package com.restaurant.auth_service.entity;


import lombok.Getter;

/// @Getter
/// НЕ РАБОТАЕТ. Так как это архитектурное ограничение. Enum - Супер-класс.
/// Lombok не может определить прямо во время компиляции, что за объект лежит в переменной
public enum Role {
    ROLE_USER,
    ROLE_ADMIN,
    ROLE_COURIER;

    public String getName() {
        return this.name();  // Вернёт "ROLE_USER" и т.д.
    }
}
