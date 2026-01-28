package com.example.demo.controller;

import com.example.demo.dto.request.AddToCartRequest;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.CartResponse;
import com.example.demo.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @PostMapping("/add")
    public ResponseEntity<ApiResponse<String>> addToCart(@RequestBody AddToCartRequest request) {
        cartService.addToCart(request);
        return ResponseEntity.ok(ApiResponse.success(null, "Item added to cart successfully"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<CartResponse>> viewCart() {
        return ResponseEntity.ok(ApiResponse.success(cartService.getMyCart(), "Cart retrieved successfully"));
    }
}