package com.example.demo.dto.response;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemDetailResponse {
    private Long id;
    private Long orderId; // Explicitly visible Order ID
    private Integer productId;
    private String variantId;
    private String merchantId;
    private Integer quantity;
    private Double price;
}