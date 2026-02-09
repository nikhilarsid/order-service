package com.example.demo.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddToCartRequest {
    private Integer productId;  // Numeric ID
    private String variantId;   // String ID
    private String merchantId;  // String ID
    private Integer quantity;
}