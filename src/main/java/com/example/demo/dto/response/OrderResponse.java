package com.example.demo.dto.response;

import com.example.demo.entity.Order;
import com.example.demo.enums.OrderStatus;
import com.example.demo.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    private Long orderId;
    private String firstName;
    private String lastName;
    private String address;
    private PaymentStatus paymentStatus;
    private String orderNumber;
    private LocalDateTime orderDate;
    private Double totalAmount;
    private OrderStatus status;
    private List<OrderItemDto> items;
}