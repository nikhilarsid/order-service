package com.example.demo.dto.response;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class CartResponse {
    private Long cartId;
    private List<CartItemDTO> items;
    private Double totalValue;

    @Data
    @Builder
    public static class CartItemDTO {
        private Long merchantProductId;
        private Integer quantity;
        private Double price;
        private Double subTotal;
    }
}