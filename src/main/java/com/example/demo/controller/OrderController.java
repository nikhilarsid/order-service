package com.example.demo.controller;

import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.OrderResponse;
import com.example.demo.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/checkout")
    public ResponseEntity<ApiResponse<Long>> checkout() {
        Long orderId = orderService.checkout();
        return ResponseEntity.ok(ApiResponse.success(orderId, "Order placed successfully"));
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getOrderHistory() {
        return ResponseEntity.ok(ApiResponse.success(orderService.getOrderHistory(), "Order history fetched"));
    }
}