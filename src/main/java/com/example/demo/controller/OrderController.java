package com.example.demo.controller;

import com.example.demo.dto.request.CheckoutRequest;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.OrderResponse;
import com.example.demo.dto.response.OrderItemDetailResponse; // Ensure this is imported
import com.example.demo.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.example.demo.entity.MerchantAnalytics;

import java.util.List;
import com.example.demo.repository.MerchantAnalyticsRepository; // Add this
import lombok.RequiredArgsConstructor;
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final MerchantAnalyticsRepository analyticsRepository;

    @PostMapping("/add")
    public ResponseEntity<ApiResponse<String>> placeOrder(@RequestBody CheckoutRequest request) {
//        orderService.checkout(request);
        return ResponseEntity.ok(ApiResponse.success(orderService.checkout(request).getOrderNo(),"Order placed successfully"));
    }

    @GetMapping("/view")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getOrderHistory() {
        return ResponseEntity.ok(ApiResponse.success(orderService.getOrderHistory(), "Order history fetched"));
    }

    @GetMapping("/viewItem/{itemId}")
    public ResponseEntity<ApiResponse<com.example.demo.dto.response.OrderItemDto>> viewItem(@PathVariable Long itemId) {
        // Fix: Call getOrderItemDetail and return the DTO type
        return ResponseEntity.ok(ApiResponse.success(orderService.getOrderItemDetail(itemId), "Item details fetched"));
    }

    @GetMapping("/merchant/{merchantId}/total-orders")
    public ResponseEntity<ApiResponse<Integer>> getTotalOrders(@PathVariable String merchantId) {
        return ResponseEntity.ok(ApiResponse.success(
                analyticsRepository.getTotalOrdersByMerchant(merchantId), "Total orders fetched"));
    }

    // 2. Get total revenue by merchant
    @GetMapping("/merchant/{merchantId}/total-revenue")
    public ResponseEntity<ApiResponse<Double>> getTotalRevenue(@PathVariable String merchantId) {
        return ResponseEntity.ok(ApiResponse.success(
                analyticsRepository.getTotalRevenueByMerchant(merchantId), "Total revenue fetched"));
    }

    // 3. Get orders/revenue for specific product/variant
    @GetMapping("/merchant/{merchantId}/stats")
    public ResponseEntity<ApiResponse<MerchantAnalytics>> getSpecificStats(
            @PathVariable String merchantId,
            @RequestParam Integer productId,
            @RequestParam String variantId) {

        return ResponseEntity.ok(ApiResponse.success(
                analyticsRepository.findByMerchantIdAndProductIdAndVariantId(merchantId, productId, variantId)
                        .orElse(new MerchantAnalytics()), "Stats fetched"));
    }
}