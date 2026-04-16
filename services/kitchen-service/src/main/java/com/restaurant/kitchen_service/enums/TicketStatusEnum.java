package com.restaurant.kitchen_service.enums;

import lombok.Getter;

@Getter
public enum TicketStatusEnum {
    OPEN("Открыт"),           // Только создан, никто не взял
    IN_PROGRESS("В работе"),   // Повар взял в работу
    READY("Готов"),           // Готов к выдаче
    CANCELLED("Отменен");     // Отменен (если заказ не оплатили)

    private final String description;

    TicketStatusEnum(String description) {
        this.description = description;
    }
}
