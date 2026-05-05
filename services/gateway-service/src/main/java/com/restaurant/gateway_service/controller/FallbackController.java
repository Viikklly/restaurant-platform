package com.restaurant.gateway_service.controller;


import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.HashMap;
import java.util.Map;

import static reactor.netty.http.HttpConnectionLiveness.log;

/// FallbackController - это контроллер, который возвращает ответы когда основной сервис не доступен.
public class FallbackController {

    /// Fallback для Auth Service
    @GetMapping("/auth")
    public ResponseEntity<Map<String, Object>> authFallback() {
        log.warn("Auth service fallback triggered");
        return createFallbackResponse(
                "Auth service is temporarily unavailable. Please try again later.",
                HttpStatus.SERVICE_UNAVAILABLE
        );
    }


    private ResponseEntity<Map<String, Object>> createFallbackResponse(String message, HttpStatus status) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", status.value());
        response.put("error", status.getReasonPhrase());
        response.put("message", message);
        response.put("timestamp", System.currentTimeMillis());
        response.put("type", "FALLBACK_RESPONSE");

        return ResponseEntity.status(status).body(response);
    }
}
