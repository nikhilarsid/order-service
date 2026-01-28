package com.example.demo.dto.request;

import lombok.Data;

@Data
public class AddToCartRequest {
    private Long merchantProductId;
    private Integer quantity;
}