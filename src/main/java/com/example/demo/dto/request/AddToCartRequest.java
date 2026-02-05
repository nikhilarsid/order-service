package com.example.demo.dto.request;

import lombok.Data;

@Data
public class AddToCartRequest {
    private Integer productId;  // Numeric ID
    private String variantId;   // String ID
    private String merchantId;  // String ID
    private Integer quantity;
}