package com.restaurant.auth_service;

import com.restaurant.auth_service.entity.Role;
import com.restaurant.auth_service.entity.User;
import com.restaurant.auth_service.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

@Slf4j
@SpringBootApplication
@EnableDiscoveryClient
public class AuthServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AuthServiceApplication.class, args);
	}


	// TODO initDatabase() удалить после добавления Liquibase
	/**
	 * CommandLineRunner выполняется ПОСЛЕ запуска Spring контекста
	 * Нужно для отладки, чтобы при запуске приложения пользователи не были пустыми
	 */
	@Bean
	public CommandLineRunner initDatabase(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		return args -> {
			log.info("Инициализация тестовых пользователей...");

			// Проверяем, есть ли уже пользователи
			if (userRepository.count() == 0) {

				User alice = User.builder()
						.email("alice@test.com")
						.username("alice")
						.password(passwordEncoder.encode("password123"))
						.role(Role.ROLE_USER)
						.build();

				User bob = User.builder()
						.email("bobi@test.com")
						.username("bobi")
						.password(passwordEncoder.encode("password123"))
						.role(Role.ROLE_USER)
						.build();

				User charlie = User.builder()
						.email("cap@test.com")
						.username("cap")
						.password(passwordEncoder.encode("password123"))
						.role(Role.ROLE_ADMIN)
						.build();

				userRepository.save(alice);
				userRepository.save(bob);
				userRepository.save(charlie);

				log.info(" Добавлено 3 тестовых пользователя");
				log.info("   alice@test.com / password123 (ROLE_USER)");
				log.info("   bobi@test.com / password123 (ROLE_USER)");
				log.info("   cap@test.com / password123 (ROLE_ADMIN)");
			} else {
				log.info("База уже содержит {} пользователей", userRepository.count());
			}
		};
	}
}



