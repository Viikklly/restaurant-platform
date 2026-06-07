package com.restaurant.kitchen_service.entity;

import com.restaurant.kitchen_service.enums.TicketStatusEnum;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Чек на кухне
 */
@Entity
@Table(name = "tickets")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     *  ID заказа из Order Service
     */
    @Column(nullable = false, unique = true)
    private Long orderId;

    /**
     * Статус тикета (используем Enum)
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatusEnum status;


    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Время когда блюдо готово (приготовили)
     */
    @Column(name = "ready_at")
    private LocalDateTime readyAt;
}