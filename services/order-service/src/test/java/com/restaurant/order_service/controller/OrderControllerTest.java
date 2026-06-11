package com.restaurant.order_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.order_service.dto.OrderCreateRequestDTO;
import com.restaurant.order_service.dto.OrderDetailsResponseDTO;
import com.restaurant.order_service.dto.OrderResponseDTO;
import com.restaurant.order_service.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class) /// Тестирование только контроллера
class OrderControllerTest {

    /**
     * Отправляет HTTP-запросы к контроллерам и проверяет ответы без поднятия реального сервера
     */
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderService orderService;

    private OrderCreateRequestDTO createRequest;
    private OrderResponseDTO createResponse;
    private OrderDetailsResponseDTO detailsResponse;

    @BeforeEach
    void setUp() {
        /// Данные для создания заказа
        createRequest = new OrderCreateRequestDTO();
        createRequest.setUserId(1L);

        /// Ответ на создание
        createResponse = new OrderResponseDTO(
                1L,
                "PENDING",
                new ArrayList<>(List.of("Пицца", "Пицца", "Паста")),
                new BigDecimal("499.99"),
                LocalDateTime.now()
        );

        /// Ответ на получение заказа
        detailsResponse = OrderDetailsResponseDTO.builder()
                .id(1L)
                .userId(1L)
                .status("PENDING")
                .totalAmount(new BigDecimal("499.99"))
                .createdAt(LocalDateTime.now())
                .build();
    }

    /// POST
    @Test
    void createOrder_ShouldReturnCreated() throws Exception {

        when(orderService.createOrder(any(OrderCreateRequestDTO.class))).thenReturn(createResponse);


        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(499.99));
    }

    @Test
    void createOrder_ShouldReturnBadRequest_WhenUserIdIsNull() throws Exception {

        createRequest.setUserId(null);


        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isBadRequest());
    }


    @Test
    void createOrder_ShouldReturnBadRequest_WhenItemsIsNull() throws Exception {
        createRequest.setItems(null);

        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isBadRequest());

    }
    /// GET
    @Test
    void getOrder_ShouldReturnOrder_WhenIdExists() throws Exception {

        when(orderService.getOrder(1L)).thenReturn(detailsResponse);


        mockMvc.perform(get("/api/orders/1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.userId").value(1L))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(999.98));
    }

    @Test
    void getOrder_ShouldReturnError_WhenIdDoesNotExist() throws Exception {

        when(orderService.getOrder(999L))
                .thenThrow(new RuntimeException("Заказ не найден: 999"));


        mockMvc.perform(get("/api/orders/999")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is5xxServerError());
    }

}