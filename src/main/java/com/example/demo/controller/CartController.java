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

    @PostMapping("/addItem")
    public ResponseEntity<ApiResponse<String>> addToCart(@RequestBody AddToCartRequest request) {
        cartService.addToCart(request);
        return ResponseEntity.ok(ApiResponse.success(null, "Item added to cart"));
    }

    @GetMapping("/view")
    public ResponseEntity<ApiResponse<CartResponse>> viewCart() {
        return ResponseEntity.ok(ApiResponse.success(cartService.getMyCart(), "Cart retrieved"));
    }

    @DeleteMapping("/deleteItem/{itemId}")
    public ResponseEntity<ApiResponse<String>> deleteItem(
            @PathVariable Long itemId,
            @RequestParam Integer quantity) {

        cartService.removeItem(itemId, quantity);
        return ResponseEntity.ok(ApiResponse.success(null, "Cart updated successfully"));
    }
}