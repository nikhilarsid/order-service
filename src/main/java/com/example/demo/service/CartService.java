package com.example.demo.service;

import com.example.demo.dto.request.AddToCartRequest;
import com.example.demo.dto.response.CartItemDTO;
import com.example.demo.dto.response.CartResponse;
import com.example.demo.entity.CartItem;
import com.example.demo.repository.CartItemRepository;
import com.example.demo.security.User;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final RestTemplate restTemplate;

    private final String PRODUCT_SERVICE_URL = "https://product-service-jzzf.onrender.com/api/v1/products/";

    @Transactional
    public void addToCart(AddToCartRequest request) {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Double verifiedPrice = null;
        Integer availableStock = 0; // Track available stock

        try {
            String url = UriComponentsBuilder.fromHttpUrl(PRODUCT_SERVICE_URL + request.getProductId())
                    .queryParam("variantId", request.getVariantId())
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            headers.set("User-Agent", "Mozilla/5.0");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, new ParameterizedTypeReference<Map<String, Object>>() {}
            );

            Map<String, Object> body = response.getBody();
            if (body != null && (Boolean) body.get("success")) {
                Map<String, Object> productData = (Map<String, Object>) body.get("data");
                if (productData != null) {
                    List<Map<String, Object>> sellers = (List<Map<String, Object>>) productData.get("sellers");
                    if (sellers != null) {
                        for (Map<String, Object> seller : sellers) {
                            if (request.getMerchantId().equalsIgnoreCase(seller.get("merchantId").toString())) {
                                verifiedPrice = Double.valueOf(seller.get("price").toString());
                                // Extract stock from the seller/merchant data
                                availableStock = Integer.valueOf(seller.get("stock").toString());
                                break;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Product Service connection failed: " + e.getMessage());
        }

        if (verifiedPrice == null) {
            throw new RuntimeException("Could not find merchant for the selected product/variant.");
        }

        // Check if item already exists in cart to calculate total requested quantity
        Optional<CartItem> existing = cartItemRepository.findByUserIdAndProductIdAndVariantId(
                user.getId(), request.getProductId(), request.getVariantId());

        int totalRequestedQuantity = request.getQuantity();
        if (existing.isPresent()) {
            totalRequestedQuantity += existing.get().getQuantity();
        }

        // Stock Validation
        if (totalRequestedQuantity > availableStock) {
            throw new RuntimeException("Stock not available. Only " + availableStock + " items left.");
        }

        // Save or Update Cart
        if (existing.isPresent()) {
            CartItem item = existing.get();
            item.setQuantity(totalRequestedQuantity);
            cartItemRepository.save(item);
        } else {
            cartItemRepository.save(CartItem.builder()
                    .userId(user.getId())
                    .productId(request.getProductId())
                    .variantId(request.getVariantId())
                    .merchantId(request.getMerchantId())
                    .quantity(request.getQuantity())
                    .price(verifiedPrice)
                    .build());
        }
    }
    @Transactional(readOnly = true)
    public CartResponse getMyCart() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        List<CartItem> items = cartItemRepository.findByUserId(user.getId());

        double total = items.stream()
                .mapToDouble(i -> i.getPrice() * i.getQuantity())
                .sum();

        List<CartItemDTO> itemDTOs = items.stream()
                .map(i -> CartItemDTO.builder()
                        .itemId(i.getId())
                        .merchantProductId(i.getProductId().toString())
                        .quantity(i.getQuantity())
                        .price(i.getPrice())
                        .subTotal(i.getPrice() * i.getQuantity())
                        .build())
                .collect(Collectors.toList());

        return CartResponse.builder()
                .cartId(0L)
                .totalValue(total)
                .items(itemDTOs)
                .build();
    }

    @Transactional
    public void removeItem(Long itemId, Integer quantityToRemove) {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        CartItem item = cartItemRepository.findByIdAndUserId(itemId, user.getId())
                .orElseThrow(() -> new RuntimeException("Item not found in your cart."));

        if (quantityToRemove <= 0) {
            throw new RuntimeException("Quantity to remove must be greater than 0.");
        }

        if (quantityToRemove > item.getQuantity()) {
            throw new RuntimeException("Error: Cannot remove " + quantityToRemove +
                    " items. You only have " + item.getQuantity() + " in your cart.");
        }

        if (quantityToRemove.equals(item.getQuantity())) {
            cartItemRepository.delete(item);
        } else {
            item.setQuantity(item.getQuantity() - quantityToRemove);
            cartItemRepository.save(item);
        }
    }
}