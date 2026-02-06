package com.example.demo.controller;

import com.example.demo.dto.request.AddToCartRequest;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.CartResponse;
import com.example.demo.service.CartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
@Slf4j // ✅ Enables Logging
public class CartController {

    private final CartService cartService;

    @PostMapping("/addItem")
    public ResponseEntity<ApiResponse<String>> addToCart(@RequestBody AddToCartRequest request) {
        log.info("📢 [CONTROLLER] Received AddToCart Request: ProductID={}, VariantID={}, MerchantID={}",
                request.getProductId(), request.getVariantId(), request.getMerchantId());

        try {
            cartService.addToCart(request);
            log.info("✅ [CONTROLLER] Item added successfully");
            return ResponseEntity.ok(ApiResponse.success(null, "Item added to cart"));
        } catch (Exception e) {
            log.error("❌ [CONTROLLER] Error adding item to cart: ", e);
            throw e; // Re-throw to be handled by GlobalExceptionHandler
        }
    }

    @GetMapping("/view")
    public ResponseEntity<ApiResponse<CartResponse>> viewCart() {
        log.info("📢 [CONTROLLER] Request to View Cart");
        return ResponseEntity.ok(ApiResponse.success(cartService.getMyCart(), "Cart retrieved"));
    }

    @DeleteMapping("/deleteItem/{itemId}")
    public ResponseEntity<ApiResponse<String>> deleteItem(
            @PathVariable Long itemId,
            @RequestParam Integer quantity) {

        log.info("📢 [CONTROLLER] Request to Delete Item ID: {}, Quantity: {}", itemId, quantity);
        cartService.removeItem(itemId, quantity);
        return ResponseEntity.ok(ApiResponse.success(null, "Cart updated successfully"));
    }
}