package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "order_items")
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer productId;
    private String variantId;
    private String merchantId;

    // ✅ NEW: Store the readable merchant name
    private String merchantName;

    private Integer quantity;
    private Double price;

    @Column(columnDefinition = "TEXT")
    private String imageUrl;
    @ManyToOne
    @JoinColumn(name = "order_id")
    private Order order;
}