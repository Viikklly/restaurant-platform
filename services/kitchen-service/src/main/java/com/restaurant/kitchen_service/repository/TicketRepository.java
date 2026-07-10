package com.restaurant.kitchen_service.repository;

import com.restaurant.kitchen_service.entity.Ticket;
import com.restaurant.kitchen_service.enums.TicketStatusEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {
    Optional<Ticket> findByOrderId(Long orderId);

    List<Ticket> findByStatus(TicketStatusEnum status);

    List<Ticket> findByStatusIn(List<TicketStatusEnum> statuses);

    boolean existsByOrderIdAndStatus(Long orderId, TicketStatusEnum status);

}