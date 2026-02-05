package com.example.demo.dto.response;

import com.example.demo.enums.OrderStatus; // Import the enum
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
    private OrderStatus status;
    private Integer totalItems;
    private LocalDateTime orderDate;
}