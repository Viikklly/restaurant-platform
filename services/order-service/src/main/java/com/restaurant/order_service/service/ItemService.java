package com.restaurant.order_service.service;


import com.restaurant.order_service.entity.Item;
import com.restaurant.order_service.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ItemService {

    private final ItemRepository itemRepository;

    /**
     * Получить все блюда
     */
    @Transactional(readOnly = true)
    public List<Item> getAllItems() {
        log.info("Получение всех блюд");
        return itemRepository.findAll();
    }

    /**
     * Получить блюдо по ID
     */
    @Transactional(readOnly = true)
    public Item getItemById(Long id) {
        log.info("Получение блюда по ID: {}", id);
        return itemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Блюдо не найдено: " + id));
    }

    /**
     * Найти блюдо по названию
     */
    @Transactional(readOnly = true)
    public Item getItemByName(String name) {
        log.info("Получение блюда по названию: {}", name);
        return itemRepository.findByItemName(name)
                .orElseThrow(() -> new RuntimeException("Блюдо не найдено: " + name));
    }

    /**
     * Найти блюда по списку названий
     */
    @Transactional(readOnly = true)
    public List<Item> getItemsByNames(List<String> names) {
        log.info("Получение блюд по названиям: {}", names);
        return itemRepository.findAllByItemNameIn(names);
    }

    /**
     * Проверить, существуют ли блюда (для заказа)
     */
    @Transactional(readOnly = true)
    public void validateItemsExist(List<String> productNames) {
        for (String name : productNames) {
            if (!itemRepository.findByItemName(name).isPresent()) {
                throw new RuntimeException("Блюдо не найдено в меню: " + name);
            }
        }
    }
}
