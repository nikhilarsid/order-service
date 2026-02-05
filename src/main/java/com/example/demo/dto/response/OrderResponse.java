package com.example.demo.dto.response;

import com.example.demo.enums.OrderStatus; // Import the enum from the new package
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class OrderResponse {
    private Long orderId;
    private Double totalAmount;
    private OrderStatus status;
    private LocalDateTime orderDate;
}