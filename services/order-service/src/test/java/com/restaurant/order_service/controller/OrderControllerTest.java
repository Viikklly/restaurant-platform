package com.restaurant.order_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.order_service.dto.OrderCreateRequestDTO;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderService orderService;

    private OrderCreateRequestDTO createRequest;
    private OrderResponseDTO createResponse;
    private OrderResponseDTO getResponse;

    @BeforeEach
    void setUp() {
        //  Данные для создания заказа
        createRequest = new OrderCreateRequestDTO();
        createRequest.setUserId(1L);

        OrderCreateRequestDTO.ItemRequestDTO item = new OrderCreateRequestDTO.ItemRequestDTO();
        item.setProductName("Пицца");
        item.setQuantity(2);
        createRequest.setItems(List.of(item));

        // Ответ на создание
        createResponse = OrderResponseDTO.builder()
                .id(1L)
                .userId(1L)
                .status("PENDING")
                .items(List.of("Пицца", "Пицца"))
                .totalAmount(new BigDecimal("999.98"))
                .createdAt(LocalDateTime.now())
                .build();

        // Ответ на получение заказа
        getResponse = OrderResponseDTO.builder()
                .id(1L)
                .userId(1L)
                .status("PENDING")
                .items(List.of("Пицца", "Пицца"))
                .totalAmount(new BigDecimal("999.98"))
                .createdAt(LocalDateTime.now())
                .build();
    }

    // POST /api/orders

    @Test
    void createOrder_ShouldReturnCreated() throws Exception {
        when(orderService.createOrder(any(OrderCreateRequestDTO.class))).thenReturn(createResponse);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.userId").value(1L))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(999.98))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(2));
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
    void createOrder_ShouldReturnBadRequest_WhenItemsIsEmpty() throws Exception {
        createRequest.setItems(List.of());

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

    // GET /api/orders/{id}

    @Test
    void getOrder_ShouldReturnOrder_WhenIdExists() throws Exception {
        when(orderService.getFullOrder(1L)).thenReturn(getResponse);

        mockMvc.perform(get("/api/orders/1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.userId").value(1L))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(999.98))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void getOrder_ShouldReturnError_WhenIdDoesNotExist() throws Exception {
        when(orderService.getFullOrder(999L))
                .thenThrow(new RuntimeException("Заказ не найден: 999"));

        mockMvc.perform(get("/api/orders/999")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is5xxServerError());
    }
}