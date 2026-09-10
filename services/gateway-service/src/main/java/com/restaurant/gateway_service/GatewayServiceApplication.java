package com.restaurant.gateway_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

/*
API Gateway
 */

@SpringBootApplication
@EnableDiscoveryClient  // регистрация в Eureka
@ComponentScan(basePackages = "com.restaurant.gateway_service")
public class GatewayServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(GatewayServiceApplication.class, args);
	}

}
