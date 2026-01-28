package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;

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

    private Long merchantProductId;
    private Integer quantity;
    private Double price;

    @ManyToOne
    @JoinColumn(name = "cart_id")
    private Cart cart;
}