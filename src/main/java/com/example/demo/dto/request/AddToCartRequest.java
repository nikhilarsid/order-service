package com.example.demo.dto.request;

import lombok.Data;

@Data
public class AddToCartRequest {
    private String merchantProductId;
    private Integer quantity;
}