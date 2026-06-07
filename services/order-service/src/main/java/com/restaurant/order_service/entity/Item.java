package com.restaurant.order_service.entity;


import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;


/**
 * Сущность Блюдо
 */
@Entity
@Table(name = "items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Название блюда
     * @example Pizza
     */
    @Column(name = "item_name", nullable = false)
    @Schema(description = "Название блюда", example = "Pizza")
    private String itemName;


    /**
     * Стоимость блюда
     * @example 299.99
     */
    @Column(name = "item_price", nullable = false, precision = 10, scale = 2)
    @Schema(description = "Стоимость блюда", example = "299.99")
    private BigDecimal itemPrice;

    /**
     * Описание блюда
     */
    @Column(name = "item_description")
    @Schema(description = "Описание блюда", example = "Классическая пицца")
    private String itemDescription;

    /**
     * Доступно ли блюдо для заказа
     */
    @Column(name = "is_available")
    @Default
    private Boolean isAvailable = true;

    /**
     * Связь с таблицей OrderItems
     */
    @OneToMany(mappedBy = "item")
    @JsonIgnore ///  Что бы убрать ошибку LazyInitializationException (так как это обратная связь:какие заказы содержат это блюдо и Пользователь не должен видеть чужие заказы)
    @Default
    private List<OrderItem> orderItems = new ArrayList<>();
}
