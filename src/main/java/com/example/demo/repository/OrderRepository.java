package com.example.demo.repository;

import com.example.demo.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    // Matches the 'createdAt' field in Order entity
    List<Order> findByUserIdOrderByCreatedAtDesc(String userId);
}