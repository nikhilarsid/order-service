package com.example.demo.service;

import com.example.demo.dto.response.OrderItemDto;
import com.example.demo.dto.response.OrderResponse;
import com.example.demo.dto.response.ProductSnapshot;
import com.example.demo.entity.CartItem;
import com.example.demo.entity.Order;
import com.example.demo.entity.OrderItem;
import com.example.demo.enums.OrderStatus;
import com.example.demo.repository.CartItemRepository;
import com.example.demo.repository.OrderItemRepository;
import com.example.demo.repository.OrderRepository;
import com.example.demo.security.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;



import com.example.demo.repository.MerchantAnalyticsRepository;
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartItemRepository cartItemRepository;
    private final RestTemplate restTemplate;

    @Value("${integration.product-service.url}")
    private String productServiceUrl;

    @Transactional
    public String checkout() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        log.info("🛒 [CHECKOUT] Starting checkout for user: {} with {} items", 
                user.getEmail(), cartItemRepository.findByUserId(user.getId()).size());

        List<CartItem> cartItems = cartItemRepository.findByUserId(user.getId());
        if (cartItems.isEmpty()) {
            log.error("❌ [CHECKOUT] Cart is empty for user: {}", user.getEmail());
            throw new RuntimeException("Cart is empty");
        }

        log.info("📦 [CHECKOUT] Processing {} items", cartItems.size());

        // 1. Initialize Order
        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());
        order.setUserId(user.getId());
        order.setOrderDate(LocalDateTime.now());
        order.setTotalAmount(0.0);
        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);
        log.info("📋 [CHECKOUT] Order created with ID: {}, OrderNumber: {}", order.getId(), order.getOrderNumber());

        double totalAmount = 0.0;
        int processedItems = 0;

        // 2. Process Each Item
        for (int i = 0; i < cartItems.size(); i++) {
            CartItem cartItem = cartItems.get(i);
            log.info("🔍 [ITEM CHECK] Processing Item {}/{} - ProductID: {}, VariantID: {}, MerchantID: {}, Quantity: {}",
                    i + 1, cartItems.size(), cartItem.getProductId(), cartItem.getVariantId(), 
                    cartItem.getMerchantId(), cartItem.getQuantity());

            // A. Fetch Product Details from Product Service
            ProductSnapshot productData = fetchProductSnapshot(
                    cartItem.getProductId(),
                    cartItem.getVariantId(),
                    cartItem.getMerchantId()
            );

            // B. Validate Availability
            if (productData == null) {
                log.error("❌ [ITEM FAIL] Could not fetch product data for ProductID: {} (Item {}/{})", 
                        cartItem.getProductId(), i + 1, cartItems.size());
                throw new RuntimeException("Order failed: Unable to process product ID " + cartItem.getProductId() + 
                        " - Product not found or merchant unavailable");
            }

            if (productData.getStock() < cartItem.getQuantity()) {
                log.error("❌ [STOCK FAIL] Insufficient stock for {}: Requested {}, Available {}", 
                        productData.getName(), cartItem.getQuantity(), productData.getStock());
                throw new RuntimeException("Insufficient stock for product '" + productData.getName() + 
                        "': Requested " + cartItem.getQuantity() + " but only " + productData.getStock() + " available");
            }

            log.info("✅ [ITEM VALID] Item {}/{} - Stock OK. Name: {}, Price: {}, Quantity: {}", 
                    i + 1, cartItems.size(), productData.getName(), productData.getPrice(), cartItem.getQuantity());

            // C. Create Order Item
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProductId(cartItem.getProductId());
            orderItem.setVariantId(cartItem.getVariantId());
            orderItem.setMerchantId(cartItem.getMerchantId());
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setPrice(productData.getPrice());
            orderItem.setMerchantName(productData.getMerchantName());
            orderItem.setImageUrl(productData.getImageUrl());
            orderItem.setProductName(productData.getName());
            orderItemRepository.save(orderItem);
            log.info("💾 [DB SAVE] OrderItem created - ID: {}", orderItem.getId());

            totalAmount += (productData.getPrice() * cartItem.getQuantity());
            processedItems++;

            // D. Update Inventory (Call Product Service)
            log.info("📊 [INVENTORY] Updating inventory for ProductID: {}", cartItem.getProductId());
            updateMerchantAnalytics(orderItem);
            updateProductInventory(orderItem);
        }

        // 3. Finalize Order
        order.setTotalAmount(totalAmount);
        orderRepository.save(order);
        log.info("✅ [FINALIZE] Order finalized with {} items, Total Amount: {}", processedItems, totalAmount);

        // 4. Clear Cart
        cartItemRepository.deleteAll(cartItems);
        log.info("🎉 [CHECKOUT] Order Placed Successfully! Order Number: {}, Items: {}, Total: {}", 
                order.getOrderNumber(), processedItems, totalAmount);

        return order.getOrderNumber();
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrderHistory() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        List<Order> orders = orderRepository.findByUserIdOrderByCreatedAtDesc(user.getId());

        return orders.stream().map(this::mapToOrderResponse).collect(Collectors.toList());
    }

    // ✅ NEW: Added this method to fix your error
    @Transactional(readOnly = true)
    public OrderItemDto getOrderItemDetail(Long itemId) {
        OrderItem item = orderItemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Order Item not found"));

        return OrderItemDto.builder()
                .productId(item.getProductId())
                .variantId(item.getVariantId())
                .merchantName(item.getMerchantName())
                .quantity(item.getQuantity())
                .price(item.getPrice())
                .imageUrl(item.getImageUrl())
                .build();
    }

    private OrderResponse mapToOrderResponse(Order order) {
        List<OrderItemDto> orderItems = order.getItems().stream()
                .map(item -> OrderItemDto.builder()
                        .itemId(item.getId())
                        .productId(item.getProductId())
                        .variantId(item.getVariantId())
                        .merchantName(item.getMerchantName())
                        .quantity(item.getQuantity())
                        .productName(item.getProductName())
                        .price(item.getPrice())
                        .imageUrl(item.getImageUrl())
                        .build())
                .collect(Collectors.toList());

        return OrderResponse.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .orderDate(order.getOrderDate())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus().toString())
                .items(orderItems)
                .build();
    }

    private ProductSnapshot fetchProductSnapshot(Integer productId, String variantId, String merchantId) {
        // Add small delay between requests to avoid overwhelming product service
        try {
            Thread.sleep(200); // 200ms delay between requests
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        int retryAttempts = 3;
        int retryCount = 0;
        Exception lastException = null;

        while (retryCount < retryAttempts) {
            try {
                String baseUrl = productServiceUrl.endsWith("/") ? productServiceUrl : productServiceUrl + "/";
                String url = baseUrl + productId + "?variantId=" + variantId;

                log.info("🌍 [EXTERNAL CALL] Fetching Product Details (Attempt {}/{}): {}", 
                        retryCount + 1, retryAttempts, url);

                ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        null,
                        new ParameterizedTypeReference<Map<String, Object>>() {}
                );

                log.info("⬅️ [EXTERNAL RESPONSE] Status: {} OK", response.getStatusCode());

                if (response.getBody() == null) {
                    log.warn("⚠️ [EXTERNAL FAIL] Response body is null");
                    retryCount++;
                    if (retryCount < retryAttempts) {
                        try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                        continue;
                    }
                    return null;
                }

                Object successObj = response.getBody().get("success");
                if (successObj == null || !(boolean) successObj) {
                    log.warn("⚠️ [EXTERNAL FAIL] Response success=false");
                    return null;
                }

                Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
                if (data == null) {
                    log.warn("⚠️ [EXTERNAL FAIL] Response data is null");
                    return null;
                }

                List<Map<String, Object>> sellers = (List<Map<String, Object>>) data.get("sellers");

                log.info("🔎 [PARSING] Found {} sellers for product.", sellers != null ? sellers.size() : 0);

                if (sellers != null && !sellers.isEmpty()) {
                    for (Map<String, Object> seller : sellers) {
                        String sellerMerchantId = (String) seller.get("merchantId");

                        log.info("   👉 Comparing Cart Merchant [{}] vs Product Merchant [{}]", merchantId, sellerMerchantId);

                        if (sellerMerchantId != null && merchantId.equalsIgnoreCase(sellerMerchantId)) {
                            log.info("   ✅ MATCH FOUND!");

                            try {
                                String imageUrl = "";
                                List<String> images = (List<String>) data.get("imageUrls");
                                if (images != null && !images.isEmpty()) {
                                    imageUrl = images.get(0);
                                }

                                Object priceObj = seller.get("price");
                                Object stockObj = seller.get("stock");
                                Object nameObj = data.get("name");
                                Object merchantNameObj = seller.get("merchantName");

                                if (priceObj == null || stockObj == null) {
                                    log.error("❌ [PARSE ERROR] Missing price or stock in seller data");
                                    return null;
                                }

                                return ProductSnapshot.builder()
                                        .name(nameObj != null ? (String) nameObj : "Unknown")
                                        .price(Double.valueOf(priceObj.toString()))
                                        .stock(Integer.valueOf(stockObj.toString()))
                                        .merchantName(merchantNameObj != null ? (String) merchantNameObj : "Unknown")
                                        .imageUrl(imageUrl)
                                        .build();
                            } catch (NumberFormatException | ClassCastException e) {
                                log.error("❌ [PARSE ERROR] Failed to parse price or stock values: {}", e.getMessage());
                                return null;
                            }
                        }
                    }
                }

                log.warn("⚠️ [MATCH FAIL] No matching merchant found in seller list for merchantId: {}", merchantId);
                return null;

            } catch (HttpClientErrorException e) {
                lastException = e;
                log.warn("⚠️ [RETRY] HTTP Error {}: {}", e.getStatusCode(), e.getMessage());
                retryCount++;
                if (retryCount < retryAttempts) {
                    try { 
                        Thread.sleep(1000 * retryCount); // Exponential backoff
                    } catch (InterruptedException ie) { 
                        Thread.currentThread().interrupt();
                    }
                } else {
                    log.error("❌ [EXTERNAL ERROR] Product Service HTTP Error after {} attempts: {} - {}", 
                            retryAttempts, e.getStatusCode(), e.getMessage());
                }
            } catch (Exception e) {
                lastException = e;
                log.warn("⚠️ [RETRY] Error fetching product snapshot: {}", e.getMessage());
                retryCount++;
                if (retryCount < retryAttempts) {
                    try { 
                        Thread.sleep(1000 * retryCount); // Exponential backoff
                    } catch (InterruptedException ie) { 
                        Thread.currentThread().interrupt();
                    }
                } else {
                    log.error("❌ [INTERNAL ERROR] Failed to fetch product snapshot after {} attempts: {}", 
                            retryAttempts, e.getMessage(), e);
                }
            }
        }

        return null;
    }

    private void updateProductInventory(OrderItem item) {
        int retryAttempts = 2;
        int retryCount = 0;

        while (retryCount < retryAttempts) {
            try {
                String cleanBase = productServiceUrl.endsWith("/")
                        ? productServiceUrl.substring(0, productServiceUrl.length() - 1)
                        : productServiceUrl;

                String url = String.format(
                        "%s/reduce-stock/%d?variantId=%s&merchantId=%s&quantity=%d",
                        cleanBase,
                        item.getProductId(),
                        item.getVariantId(),
                        item.getMerchantId(),
                        item.getQuantity()
                );

                log.info("🌍 [INVENTORY UPDATE] Attempt {}/{} - Calling: {}", retryCount + 1, retryAttempts, url);
                restTemplate.put(url, null);
                log.info("✅ [INVENTORY UPDATE] Success for ProductID: {}", item.getProductId());
                return; // Success - exit the retry loop

            } catch (HttpClientErrorException e) {
                retryCount++;
                log.warn("⚠️ [INVENTORY RETRY] HTTP Error {} on attempt {}: {}", 
                        e.getStatusCode(), retryCount, e.getMessage());
                if (retryCount < retryAttempts) {
                    try { 
                        Thread.sleep(500); 
                    } catch (InterruptedException ie) { 
                        Thread.currentThread().interrupt(); 
                    }
                } else {
                    log.error("❌ [INVENTORY FAIL] Could not update stock after {} attempts for Order Item: {} - {}",
                            retryAttempts, item.getId(), e.getMessage());
                }
            } catch (Exception e) {
                retryCount++;
                log.warn("⚠️ [INVENTORY RETRY] Error on attempt {}: {}", retryCount, e.getMessage());
                if (retryCount < retryAttempts) {
                    try { 
                        Thread.sleep(500); 
                    } catch (InterruptedException ie) { 
                        Thread.currentThread().interrupt(); 
                    }
                } else {
                    log.error("❌ [INVENTORY FAIL] Could not update stock after {} attempts for Order Item: {}",
                            retryAttempts, item.getId(), e);
                }
            }
        }
    }
}