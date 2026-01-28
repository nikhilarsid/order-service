package com.example.demo.service;

import com.example.demo.dto.request.AddToCartRequest;
import com.example.demo.dto.response.CartResponse;
import com.example.demo.entity.Cart;
import com.example.demo.entity.CartItem;
import com.example.demo.repository.CartRepository;
import com.example.demo.security.User;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final RestTemplate restTemplate;

    // Ensure this matches your running Inventory Service
    private final String INVENTORY_URL = "http://localhost:8082/api/v1/inventory/";

    @Transactional
    public void addToCart(AddToCartRequest request) {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        Double verifiedPrice;

        try {
            String url = INVENTORY_URL + request.getMerchantProductId();
            System.out.println("DEBUG: Calling URL: " + url);

            // FIX: Changed expected response from ApiResponse to raw Map
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            System.out.println("DEBUG: Response received: " + response.getBody());

            Map<String, Object> data = response.getBody();

            if (data == null || !data.containsKey("price")) {
                throw new RuntimeException("Price not found in Inventory response");
            }

            // Safely extract price
            verifiedPrice = ((Number) data.get("price")).doubleValue();

        } catch (Exception e) {
            System.out.println("❌ ERROR CALLING INVENTORY: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Item not found in Inventory or Service is down");
        }

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
                    .price(verifiedPrice)
                    .build();
            cart.addItem(newItem);
        }

        cartRepository.save(cart);
    }

    public CartResponse getMyCart() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Cart is empty"));

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