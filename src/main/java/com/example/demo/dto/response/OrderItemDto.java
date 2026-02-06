package com.example.demo.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemDto {
    private Integer productId;
    private String variantId;
    private String merchantName;
    private Integer quantity;
    private Double price;
    private String imageUrl;
}