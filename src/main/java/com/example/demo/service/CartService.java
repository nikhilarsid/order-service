package com.example.demo.service;

import com.example.demo.dto.request.AddToCartRequest;
import com.example.demo.dto.response.CartItemDTO;
import com.example.demo.dto.response.CartResponse;
import com.example.demo.entity.CartItem;
import com.example.demo.repository.CartItemRepository;
import com.example.demo.security.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j // ✅ Enables Logging
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final RestTemplate restTemplate;

    // Ensure this URL is correct (use http://localhost:8095/api/v1/products/ if running locally)
    private final String PRODUCT_SERVICE_URL = "https://product-service-jzzf.onrender.com/api/v1/products/";

    @Transactional
    public void addToCart(AddToCartRequest request) {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        log.info("🔹 [SERVICE] User {} adding item to cart...", user.getEmail());

        Double verifiedPrice = null;
        Integer availableStock = 0;

        try {
            String url = UriComponentsBuilder.fromHttpUrl(PRODUCT_SERVICE_URL + request.getProductId())
                    .queryParam("variantId", request.getVariantId())
                    .toUriString();

            log.info("🌍 [EXTERNAL CALL] Verifying Product at URL: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, new ParameterizedTypeReference<Map<String, Object>>() {}
            );

            log.info("⬅️ [EXTERNAL RESPONSE] Status Code: {}", response.getStatusCode());

            Map<String, Object> body = response.getBody();
            if (body != null && (Boolean) body.get("success")) {
                Map<String, Object> productData = (Map<String, Object>) body.get("data");
                if (productData != null) {
                    List<Map<String, Object>> sellers = (List<Map<String, Object>>) productData.get("sellers");
                    if (sellers != null) {
                        for (Map<String, Object> seller : sellers) {
                            if (request.getMerchantId().equalsIgnoreCase(seller.get("merchantId").toString())) {
                                verifiedPrice = Double.valueOf(seller.get("price").toString());
                                availableStock = Integer.valueOf(seller.get("stock").toString());
                                log.info("✅ [SERVICE] Merchant Found! Price: {}, Stock: {}", verifiedPrice, availableStock);
                                break;
                            }
                        }
                    }
                }
            }
        } catch (HttpClientErrorException e) {
            log.error("❌ [EXTERNAL ERROR] Product Service returned {} at {}", e.getStatusCode(), PRODUCT_SERVICE_URL);
            throw new RuntimeException("Product not found or service unavailable.");
        } catch (Exception e) {
            log.error("❌ [INTERNAL ERROR] Failed to connect to Product Service: {}", e.getMessage());
            throw new RuntimeException("System error: Unable to verify product details.");
        }

        if (verifiedPrice == null) {
            log.warn("⚠️ [SERVICE] Merchant ID {} not found for this product.", request.getMerchantId());
            throw new RuntimeException("Could not find merchant for the selected product/variant.");
        }

        if (request.getQuantity() > availableStock) {
            log.warn("⚠️ [SERVICE] Insufficient Stock. Requested: {}, Available: {}", request.getQuantity(), availableStock);
            throw new RuntimeException("Stock not available. Only " + availableStock + " items left.");
        }

        // DB Operations
        Optional<CartItem> existing = cartItemRepository.findByUserIdAndProductIdAndVariantId(
                user.getId(), request.getProductId(), request.getVariantId());

        if (existing.isPresent()) {
            log.info("🔄 [DB] Updating existing cart item quantity.");
            CartItem item = existing.get();
            item.setQuantity(item.getQuantity() + request.getQuantity());
            item.setMerchantId(request.getMerchantId());
            cartItemRepository.save(item);
        } else {
            log.info("➕ [DB] Creating new cart item.");
            cartItemRepository.save(CartItem.builder()
                    .userId(user.getId())
                    .productId(request.getProductId())
                    .variantId(request.getVariantId())
                    .merchantId(request.getMerchantId())
                    .quantity(request.getQuantity())
                    .price(verifiedPrice)
                    .build());
        }
        log.info("✅ [SERVICE] Cart updated successfully.");
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
                        .productId(i.getProductId())
                        .variantId(i.getVariantId())
                        .merchantId(i.getMerchantId())
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

        log.info("🗑 [SERVICE] Removing item {} for user {}", itemId, user.getEmail());

        CartItem item = cartItemRepository.findByIdAndUserId(itemId, user.getId())
                .orElseThrow(() -> new RuntimeException("Item not found in your cart."));

        if (quantityToRemove >= item.getQuantity()) {
            cartItemRepository.delete(item);
            log.info("🗑 [DB] Item completely removed.");
        } else {
            item.setQuantity(item.getQuantity() - quantityToRemove);
            cartItemRepository.save(item);
            log.info("🔻 [DB] Item quantity reduced.");
        }
    }
}