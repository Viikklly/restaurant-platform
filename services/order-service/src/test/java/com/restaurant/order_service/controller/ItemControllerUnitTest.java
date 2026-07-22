package com.restaurant.order_service.controller;

import com.restaurant.order_service.entity.Item;
import com.restaurant.order_service.service.ItemService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit-тесты ItemController")
class ItemControllerUnitTest {

    @Mock
    private ItemService itemService;

    @InjectMocks
    private ItemController itemController;

    private final Long TEST_ITEM_ID = 1L;

    private Item createTestItem(Long id, String name, BigDecimal price, Boolean available) {
        return Item.builder()
                .id(id)
                .itemName(name)
                .itemPrice(price)
                .itemDescription("Test description")
                .isAvailable(available)
                .build();
    }

    @Test
    @DisplayName("GET /api/items - возвращает все блюда")
    void getAllItems_ShouldReturnAllItems() {

        List<Item> items = List.of(
                createTestItem(1L, "Пицца", BigDecimal.valueOf(500), true),
                createTestItem(2L, "Паста", BigDecimal.valueOf(450), true),
                createTestItem(3L, "Салат", BigDecimal.valueOf(300), false)
        );

        when(itemService.getAllItems()).thenReturn(items);


        ResponseEntity<List<Item>> result = itemController.getAllItems(null);

        /// проверки
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).hasSize(3);
        verify(itemService).getAllItems();
        verify(itemService, never()).getAvailableItems();
    }

    @Test
    @DisplayName("GET /api/items?available=true - возвращает только доступные блюда")
    void getAllItems_WithAvailableParam_ShouldReturnAvailableItems() {

        List<Item> availableItems = List.of(
                createTestItem(1L, "Пицца", BigDecimal.valueOf(500), true),
                createTestItem(2L, "Паста", BigDecimal.valueOf(450), true)
        );

        when(itemService.getAvailableItems()).thenReturn(availableItems);


        ResponseEntity<List<Item>> result = itemController.getAllItems(true);

        /// проверки
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).hasSize(2);
        assertThat(result.getBody()).allMatch(Item::getIsAvailable);
        verify(itemService).getAvailableItems();
        verify(itemService, never()).getAllItems();
    }

    @Test
    @DisplayName("GET /api/items?available=false - возвращает все блюда (available=false игнорируется)")
    void getAllItems_WithAvailableFalse_ShouldReturnAllItems() {

        List<Item> items = List.of(
                createTestItem(1L, "Пицца", BigDecimal.valueOf(500), true),
                createTestItem(2L, "Паста", BigDecimal.valueOf(450), false)
        );

        when(itemService.getAllItems()).thenReturn(items);


        ResponseEntity<List<Item>> result = itemController.getAllItems(false);

        /// проверки
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).hasSize(2);
        verify(itemService).getAllItems();
        verify(itemService, never()).getAvailableItems();
    }

    @Test
    @DisplayName("GET /api/items/{id} - возвращает блюдо по ID")
    void getItem_ShouldReturnItem() {

        Item item = createTestItem(TEST_ITEM_ID, "Пицца Маргарита", BigDecimal.valueOf(500), true);

        when(itemService.getItemById(TEST_ITEM_ID)).thenReturn(item);


        ResponseEntity<Item> result = itemController.getItem(TEST_ITEM_ID);

        /// проверки
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getId()).isEqualTo(TEST_ITEM_ID);
        assertThat(result.getBody().getItemName()).isEqualTo("Пицца Маргарита");
        verify(itemService).getItemById(TEST_ITEM_ID);
    }

    @Test
    @DisplayName("GET /api/items/{id} - возвращает NOT_FOUND при отсутствии блюда")
    void getItem_ShouldReturnNotFound_WhenItemNotFound() {

        Long nonExistentId = 999L;

        when(itemService.getItemById(nonExistentId)).thenThrow(new RuntimeException("Блюдо не найдено: " + nonExistentId));


        ResponseEntity<Item> result = itemController.getItem(nonExistentId);

        /// проверки
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(result.getBody()).isNull();
        verify(itemService).getItemById(nonExistentId);
    }
}