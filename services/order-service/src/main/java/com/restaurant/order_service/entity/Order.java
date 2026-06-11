package com.restaurant.order_service.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Сущность Заказ
 */
@Entity
@Table(name = "orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    /**
     * Идентификатор пользователя, создавшего заказ
     */
    @Column(nullable = false)
    private Long userId;


    /**
     * Текущий статус заказа
     * @example PENDING, PAID, CANCELLED, PREPARING, READY, DELIVERED
     */
    @Column(nullable = false)
    @Schema(description = "Статус заказа. Возможные значения: PENDING, PAID, CANCELLED, PREPARING, READY, DELIVERED",
            example = "PENDING", allowableValues = { "PENDING", "PAID", "CANCELLED", "PREPARING", "READY", "DELIVERED"})
    private String status;


    /**
     * Общая сумма заказа
     * @example 299.99
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;


    /**
     * Дата и время создания заказа (авто)
     */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    /**
     * Дата и время обновления заказа (авто)
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;


/*    *//**
     * Связь с таблицей OrderItems
     *//*
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> orderItems = new ArrayList<>();
    */

}