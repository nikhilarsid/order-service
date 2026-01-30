package com.example.demo.service;

import com.example.demo.dto.request.AddToCartRequest;
import com.example.demo.dto.response.CartResponse;
import com.example.demo.entity.Cart;
import com.example.demo.entity.CartItem;
import com.example.demo.repository.CartRepository;
import com.example.demo.security.User;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final RestTemplate restTemplate;

    // ✅ FIXED: Points to your live Inventory Service on Render
    private final String INVENTORY_SERVICE_URL = "https://inventory-q6gj.onrender.com/api/v1/inventory/";

    @Transactional
    public void addToCart(AddToCartRequest request) {
        // 1. Get the currently logged-in user
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        Double verifiedPrice;

        // 2. Call Inventory Service to check Price & Stock
        try {
            String url = INVENTORY_SERVICE_URL + request.getMerchantProductId();
            System.out.println("🔍 Calling Inventory Service: " + url);

            // --- 🛡️ SECURITY FIX: Forward the JWT Token ---
            String jwtToken = null;
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                jwtToken = attributes.getRequest().getHeader("Authorization");
            }

            HttpHeaders headers = new HttpHeaders();
            if (jwtToken != null) {
                headers.set("Authorization", jwtToken); // Attach the token!
                System.out.println("🔑 Token forwarded to Inventory Service");
            }
            HttpEntity<String> entity = new HttpEntity<>(headers);
            // ------------------------------------------------

            // 3. Perform the Request
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity, // Pass the headers (with token) here
                    new ParameterizedTypeReference<>() {}
            );

            Map<String, Object> data = response.getBody();

            // 4. Validate Response
            if (data == null || !data.containsKey("price")) {
                System.err.println("❌ Invalid Response from Inventory: " + data);
                throw new RuntimeException("Price not found for this item");
            }

            // 5. Extract Price Safely (Handles Integer/Double mismatch)
            Object priceObj = data.get("price");
            if (priceObj instanceof Integer) {
                verifiedPrice = ((Integer) priceObj).doubleValue();
            } else if (priceObj instanceof Double) {
                verifiedPrice = (Double) priceObj;
            } else {
                verifiedPrice = Double.parseDouble(priceObj.toString());
            }

            System.out.println("✅ Price Verified: " + verifiedPrice);

        } catch (Exception e) {
            System.err.println("❌ Failed to verify price from Inventory Service: " + e.getMessage());
            // If the call fails (403, 404, or Network), we stop here to prevent "Free Items"
            throw new RuntimeException("Could not verify item price. " + e.getMessage());
        }

        // --- Existing Cart Logic (Save to DB) ---

        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseGet(() -> {
                    Cart newCart = Cart.builder()
                            .userId(user.getId())
                            .items(new ArrayList<>())
                            .build();
                    return cartRepository.save(newCart);
                });

        boolean exists = false;
        for (CartItem item : cart.getItems()) {
            // Check if product already exists in cart
            if (item.getMerchantProductId().equals(request.getMerchantProductId())) {
                item.setQuantity(item.getQuantity() + request.getQuantity());
                exists = true;
                break;
            }
        }

        if (!exists) {
            CartItem newItem = CartItem.builder()
                    .merchantProductId(request.getMerchantProductId())
                    .quantity(request.getQuantity())
                    .price(verifiedPrice) // Use the verified price from Inventory
                    .build();
            cart.addItem(newItem);
        }

        cartRepository.save(cart);
    }

    // ✅ FIX 1: Add Transactional to prevent LazyInitializationException
    @Transactional(readOnly = true)
    public CartResponse getMyCart() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        // ✅ FIX 2: Handle empty cart gracefully instead of crashing
        Cart cart = cartRepository.findByUserId(user.getId())
                .orElse(null);

        if (cart == null || cart.getItems() == null || cart.getItems().isEmpty()) {
            return CartResponse.builder()
                    .cartId(cart != null ? cart.getId() : null)
                    .items(new ArrayList<>())
                    .totalValue(0.0)
                    .build();
        }

        double totalValue = cart.getItems().stream()
                .mapToDouble(item -> item.getPrice() * item.getQuantity())
                .sum();

        List<CartResponse.CartItemDTO> itemDTOs = cart.getItems().stream()
                .map(item -> CartResponse.CartItemDTO.builder()
                        .merchantProductId(item.getMerchantProductId())
                        .quantity(item.getQuantity())
                        .price(item.getPrice())
                        .subTotal(item.getPrice() * item.getQuantity())
                        .build())
                .collect(Collectors.toList());

        return CartResponse.builder()
                .cartId(cart.getId())
                .items(itemDTOs)
                .totalValue(totalValue)
                .build();
    }
}