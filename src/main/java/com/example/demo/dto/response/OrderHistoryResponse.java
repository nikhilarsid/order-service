package com.example.demo.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderHistoryResponse {
    private Long orderId;
    private Double totalAmount;
    private String status;
    private Integer totalItems; // Useful summary field for history lists
    private LocalDateTime orderDate;
}