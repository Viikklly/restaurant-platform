package com.restaurant.order_service.repository;

import com.restaurant.order_service.entity.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ItemRepository extends JpaRepository<Item, Long> {
    Optional<Item> findByItemName(String itemName);

    List<Item> findAllByItemNameIn(List<String> itemNames);

    List<Item> findByIsAvailable(boolean isAvailable);

    List<Item> findByIsAvailableTrue();
}
