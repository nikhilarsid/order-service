package com.example.demo.dto.response;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemDTO {
    private Long itemId;
    private String merchantProductId;
    private Integer quantity;
    private Double price;
    private Double subTotal;
}