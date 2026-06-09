package com.restaurant.order_service.controller;

import com.restaurant.order_service.entity.Item;
import com.restaurant.order_service.service.ItemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
@Slf4j
public class ItemController {

    private final ItemService itemService;

    @GetMapping
    public ResponseEntity<List<Item>> getAllItems() {
        log.info("GET /api/items");
        return ResponseEntity.ok(itemService.getAllItems());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Item> getItem(@PathVariable Long id) {
        log.info("GET /api/items/{}", id);
        return ResponseEntity.ok(itemService.getItemById(id));
    }
}