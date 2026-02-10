package com.example.demo.service;

import com.example.demo.dto.response.OrderItemDto;
import com.example.demo.dto.response.OrderResponse;
import com.example.demo.dto.response.ProductSnapshot;
import com.example.demo.entity.CartItem;
import com.example.demo.entity.Order;
import com.example.demo.entity.OrderItem;
import com.example.demo.entity.MerchantAnalytics; // Added
import com.example.demo.enums.OrderStatus;
import com.example.demo.repository.CartItemRepository;
import com.example.demo.repository.OrderItemRepository;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.MerchantAnalyticsRepository; // Added
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

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartItemRepository cartItemRepository;
    private final MerchantAnalyticsRepository merchantAnalyticsRepository; // Added
    private final RestTemplate restTemplate;

    @Value("${integration.product-service.url}")
    private String productServiceUrl;

    @Transactional
    public String checkout() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        log.info("🛒 [CHECKOUT] Starting checkout for user: {}", user.getEmail());

        List<CartItem> cartItems = cartItemRepository.findByUserId(user.getId());
        if (cartItems.isEmpty()) {
            log.error("❌ [CHECKOUT] Cart is empty for user: {}", user.getEmail());
            throw new RuntimeException("Cart is empty");
        }

        // 1. Initialize Order
        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());
        order.setUserId(user.getId());
        order.setOrderDate(LocalDateTime.now());
        order.setTotalAmount(0.0);
        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);

        double totalAmount = 0.0;

        // 2. Process Each Item
        for (CartItem cartItem : cartItems) {
            log.info("🔍 [ITEM CHECK] Processing Item - ProductID: {}, VariantID: {}, MerchantID: {}",
                    cartItem.getProductId(), cartItem.getVariantId(), cartItem.getMerchantId());

            // A. Fetch Product Details from Product Service
            ProductSnapshot productData = fetchProductSnapshot(
                    cartItem.getProductId(),
                    cartItem.getVariantId(),
                    cartItem.getMerchantId()
            );

            // B. Validate Availability
            if (productData == null) {
                log.error("❌ [ITEM FAIL] Product unavailable or Merchant mismatch for ProductID: {}", cartItem.getProductId());
                throw new RuntimeException("Order failed: Item out of stock or unavailable: " + cartItem.getProductId());
            }

            if (productData.getStock() < cartItem.getQuantity()) {
                log.error("❌ [STOCK FAIL] Not enough stock. Requested: {}, Available: {}", cartItem.getQuantity(), productData.getStock());
                throw new RuntimeException("Insufficient stock for product: " + productData.getName());
            }

            log.info("✅ [ITEM VALID] Stock OK. Price: {}", productData.getPrice());

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
            orderItemRepository.save(orderItem);

            totalAmount += (productData.getPrice() * cartItem.getQuantity());

            // ✅ NEW: Update Merchant Analytics
            updateMerchantAnalytics(orderItem);

            // D. Update Inventory (Call Product Service)
            updateProductInventory(orderItem);
        }

        // 3. Finalize Order
        order.setTotalAmount(totalAmount);
        orderRepository.save(order);

        // 4. Clear Cart
        cartItemRepository.deleteAll(cartItems);
        log.info("🎉 [CHECKOUT] Order Placed Successfully! Order Number: {}", order.getOrderNumber());

        return order.getOrderNumber();
    }

    // ✅ NEW: Added method to update merchant statistics
    private void updateMerchantAnalytics(OrderItem item) {
        log.info("📊 [ANALYTICS] Updating stats for Merchant: {}", item.getMerchantId());

        MerchantAnalytics stats = merchantAnalyticsRepository
                .findByMerchantIdAndProductIdAndVariantId(
                        item.getMerchantId(),
                        item.getProductId(),
                        item.getVariantId()
                )
                .orElseGet(() -> {
                    log.info("📊 [ANALYTICS] Creating new record for Merchant: {}", item.getMerchantId());
                    return MerchantAnalytics.builder()
                            .merchantId(item.getMerchantId())
                            .productId(item.getProductId())
                            .variantId(item.getVariantId())
                            .numberOfOrdersSold(0)
                            .amountGenerated(0.0)
                            .build();
                });

        stats.setNumberOfOrdersSold(stats.getNumberOfOrdersSold() + item.getQuantity());
        stats.setAmountGenerated(stats.getAmountGenerated() + (item.getPrice() * item.getQuantity()));

        merchantAnalyticsRepository.save(stats);
        log.info("✅ [ANALYTICS] Success for Merchant: {}", item.getMerchantId());
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrderHistory() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        List<Order> orders = orderRepository.findByUserIdOrderByCreatedAtDesc(user.getId());

        return orders.stream().map(this::mapToOrderResponse).collect(Collectors.toList());
    }

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
                        .productId(item.getProductId())
                        .variantId(item.getVariantId())
                        .merchantName(item.getMerchantName())
                        .quantity(item.getQuantity())
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
        try {
            String baseUrl = productServiceUrl.endsWith("/") ? productServiceUrl : productServiceUrl + "/";
            String url = baseUrl + productId + "?variantId=" + variantId;

            log.info("🌍 [EXTERNAL CALL] Fetching Product Details: {}", url);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );

            if (response.getBody() == null || !(boolean) response.getBody().get("success")) {
                return null;
            }

            Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
            List<Map<String, Object>> sellers = (List<Map<String, Object>>) data.get("sellers");

            if (sellers != null) {
                for (Map<String, Object> seller : sellers) {
                    String sellerMerchantId = (String) seller.get("merchantId");
                    if (merchantId.equalsIgnoreCase(sellerMerchantId)) {
                        String imageUrl = "";
                        List<String> images = (List<String>) data.get("imageUrls");
                        if (images != null && !images.isEmpty()) {
                            imageUrl = images.get(0);
                        }

                        return ProductSnapshot.builder()
                                .name((String) data.get("name"))
                                .price(Double.valueOf(seller.get("price").toString()))
                                .stock(Integer.valueOf(seller.get("stock").toString()))
                                .merchantName((String) seller.get("merchantName"))
                                .imageUrl(imageUrl)
                                .build();
                    }
                }
            }
            return null;

        } catch (HttpClientErrorException e) {
            log.error("❌ [EXTERNAL ERROR] Product Service Error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            log.error("❌ [INTERNAL ERROR] Failed to parse product data", e);
            return null;
        }
    }

    private void updateProductInventory(OrderItem item) {
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

            log.info("🌍 [INVENTORY UPDATE] Calling: {}", url);
            restTemplate.put(url, null);
            log.info("✅ [INVENTORY UPDATE] Success");

        } catch (Exception e) {
            log.error("❌ [INVENTORY FAIL] Could not update stock for Order Item: {}", item.getId(), e);
        }
    }
}