package com.restaurant.order_service.controller;

import com.restaurant.order_service.entity.Item;
import com.restaurant.order_service.repository.ItemRepository;
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

    private final ItemRepository itemRepository;

    @GetMapping
    public ResponseEntity<List<Item>> getAllItems() {
        log.info("GET /api/items");
        List<Item> items = itemRepository.findAll();
        return ResponseEntity.ok(items);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Item> getItem(@PathVariable Long id) {
        log.info("GET /api/items/{}", id);
        Item item = itemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Блюдо не найдено: " + id));
        return ResponseEntity.ok(item);
    }
}