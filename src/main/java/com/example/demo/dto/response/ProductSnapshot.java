package com.example.demo.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSnapshot {
    private String name;
    private Double price;
    private Integer stock;
    private String merchantName;
    private String imageUrl;
}