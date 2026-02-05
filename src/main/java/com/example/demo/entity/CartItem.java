package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "cart_items")
public class CartItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userId;
    private Integer productId;
    private String variantId;
    private String merchantId;
    private Integer quantity;
    private Double price;

    @CreationTimestamp
    private LocalDateTime createdAt; // New field

    @org.hibernate.annotations.UpdateTimestamp
    private LocalDateTime updatedAt; // New field
}