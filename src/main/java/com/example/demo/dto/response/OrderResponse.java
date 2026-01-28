package com.example.demo.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class OrderResponse {
    private Long orderId;
    private Double totalAmount;
    private String status;
    private LocalDateTime orderDate;
}