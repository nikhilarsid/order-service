package com.example.demo.controller;

import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.OrderResponse;
import com.example.demo.dto.response.OrderItemDetailResponse; // Ensure this is imported
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

    @PostMapping("/add")
    public ResponseEntity<ApiResponse<Long>> checkout() {
        return ResponseEntity.ok(ApiResponse.success(orderService.checkout(), "Order placed successfully"));
    }

    @GetMapping("/view")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getOrderHistory() {
        return ResponseEntity.ok(ApiResponse.success(orderService.getOrderHistory(), "Order history fetched"));
    }

    @GetMapping("/viewItem/{itemId}")
    public ResponseEntity<ApiResponse<OrderItemDetailResponse>> viewItem(@PathVariable Long itemId) {
        // Fix: Call getOrderItemDetail and return the DTO type
        return ResponseEntity.ok(ApiResponse.success(orderService.getOrderItemDetail(itemId), "Item details fetched"));
    }
}