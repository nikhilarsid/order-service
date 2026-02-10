package com.example.demo.repository;

import com.example.demo.entity.Order;
import com.example.demo.enums.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Exclude security to prevent NoSuchBeanDefinitionException for JwtService
@DataJpaTest(excludeAutoConfiguration = {
        SecurityAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void findByUserIdOrderByCreatedAtDesc_ShouldReturnOrderedList() {
        // Arrange
        Order oldOrder = Order.builder()
                .userId("user1")
                .orderNumber("ORD-OLD")
                .status(OrderStatus.CONFIRMED)
                .createdAt(LocalDateTime.now().minusDays(1))
                .build();

        Order newOrder = Order.builder()
                .userId("user1")
                .orderNumber("ORD-NEW")
                .status(OrderStatus.CONFIRMED)
                .createdAt(LocalDateTime.now())
                .build();

        orderRepository.save(oldOrder);
        orderRepository.save(newOrder);

        // Act
        List<Order> results = orderRepository.findByUserIdOrderByCreatedAtDesc("user1");

        // Assert
        assertThat(results).hasSize(2);
        // Verify Descending: Newest order should be at index 0
        assertThat(results.get(0).getOrderNumber()).isEqualTo("ORD-NEW");
    }
}