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
    private Integer quantity;
    private Double price;

    @ManyToOne
    @JoinColumn(name = "order_id") // Matches order_id in schema
    private Order order;
}