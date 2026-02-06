package com.example.demo.entity;

import com.example.demo.enums.OrderStatus;   // Add this
import com.example.demo.enums.PaymentStatus; // Add this
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import java.util.List;
import com.example.demo.enums.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userId;
    private String orderNumber;
    private LocalDateTime orderDate;
    private Double totalAmount;

    @Enumerated(EnumType.STRING)
    private OrderStatus status; // Changed to Enum

    private String address;

    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus; // Changed to Enum

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<OrderItem> items;

    @CreationTimestamp
    private LocalDateTime createdAt;
}