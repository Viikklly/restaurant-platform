package com.restaurant.notification_service;

import com.restaurant.notification_service.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class NotificationServiceApplicationTests {

    @MockitoBean
    private NotificationService notificationService;

    @Test
    void contextLoads() {
    }
}
