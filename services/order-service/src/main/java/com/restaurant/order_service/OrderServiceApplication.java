package com.restaurant.order_service;

import com.restaurant.order_service.entity.Item;
import com.restaurant.order_service.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;

import java.math.BigDecimal;

@SpringBootApplication
@EnableDiscoveryClient

@RequiredArgsConstructor
@Slf4j
public class OrderServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(OrderServiceApplication.class, args);
	}


	private final ItemRepository itemRepository;


	// TODO  initDatabase() удалить после добавления Liquibase
	/**
	 * CommandLineRunner выполняется ПОСЛЕ запуска Spring контекста
	 * Нужно для отладки, что бы при запуске приложения Item(меню) не было пустым
	 */
	@Bean
	public CommandLineRunner initDatabase() {

		return args -> {
			log.info(" Инициализация меню");

			// Проверяем, есть ли уже блюда
			if (itemRepository.count() == 0) {

				Item pizza = Item.builder()
						.itemName("Пицца")
						.itemPrice(new BigDecimal("499.99"))
						.itemDescription("Пицца с томатным соусом")
						.build();

				Item pasta = Item.builder()
						.itemName("Паста")
						.itemPrice(new BigDecimal("389.00"))
						.itemDescription("Спагетти")
						.build();

				Item salad = Item.builder()
						.itemName("Цезарь")
						.itemPrice(new BigDecimal("329.50"))
						.itemDescription("Салат")
						.isAvailable(false)
						.build();

				itemRepository.save(pizza);
				itemRepository.save(pasta);
				itemRepository.save(salad);

				log.info("Добавлено 3 блюда в меню");
			} else {
				log.info("Меню уже содержит {} блюд", itemRepository.count());
			}
		};
	}

}
