package com.example.demo.dto.response;

import lombok.*;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemDTO {
    private Long itemId;
    private Integer productId; // Added
    private String variantId;  // Added
    private String merchantId; // Added
    private String merchantProductId; // Keep if you're using it for the frontend
    private Integer quantity;
    private Double price;
    private Double subTotal;
}