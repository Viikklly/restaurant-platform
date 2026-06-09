package com.restaurant.payment_service.controller;

import com.restaurant.payment_service.entity.PaymentTransaction;
import com.restaurant.payment_service.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Получить все транзакции (для отладки)
     */
    @GetMapping
    public ResponseEntity<List<PaymentTransaction>> getAllTransactions() {
        log.info("GET /api/payments");
        return ResponseEntity.ok(paymentService.getAllTransactions());
    }

    /**
     * Получить транзакцию по ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<PaymentTransaction> getTransactionById(@PathVariable Long id) {
        log.info("GET /api/payments/{}", id);
        return ResponseEntity.ok(paymentService.getTransactionById(id));
    }

    /**
     * Получить все транзакции по ID заказа
     */
    @GetMapping("/order/{orderId}")
    public ResponseEntity<List<PaymentTransaction>> getTransactionsByOrderId(@PathVariable Long orderId) {
        log.info("GET /api/payments/order/{}", orderId);
        return ResponseEntity.ok(paymentService.getTransactionsByOrderId(orderId));
    }

    /**
     * Получить все транзакции по ID пользователя
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<PaymentTransaction>> getTransactionsByUserId(@PathVariable Long userId) {
        log.info("GET /api/payments/user/{}", userId);
        return ResponseEntity.ok(paymentService.getTransactionsByUserId(userId));
    }
}
