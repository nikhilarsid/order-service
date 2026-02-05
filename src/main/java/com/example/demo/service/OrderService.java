package com.example.demo.service;

import com.example.demo.dto.response.OrderResponse;
import com.example.demo.dto.response.OrderItemDetailResponse;
import com.example.demo.entity.*;
import com.example.demo.enums.OrderStatus;
import com.example.demo.enums.PaymentStatus;
import com.example.demo.integration.OrderNotificationPublisher;
import com.example.demo.repository.*;
import com.example.demo.security.User;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final CartItemRepository cartItemRepository;
    private final OrderItemRepository orderItemRepository;
    private final MerchantAnalyticsRepository analyticsRepository;
    private final OrderNotificationPublisher orderNotificationPublisher;
    private final RestTemplate restTemplate;

    @Transactional
    public Long checkout() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        log.info("DIAGNOSTIC: Starting checkout for user: {}", user.getEmail());

        List<CartItem> cartItems = cartItemRepository.findByUserId(user.getId());
        if (cartItems.isEmpty()) {
            log.error("DIAGNOSTIC: Cart is empty for user {}", user.getId());
            throw new RuntimeException("Cannot place order: Your cart is empty.");
        }

        double totalAmount = cartItems.stream()
                .mapToDouble(i -> i.getPrice() * i.getQuantity())
                .sum();

        Order order = Order.builder()
                .userId(user.getId())
                .totalAmount(totalAmount)
                .status(OrderStatus.CONFIRMED)
                .address("Default Address")
                .paymentStatus(PaymentStatus.PAID)
                .build();

        // Map CartItems to OrderItems and LOG the data being transferred
        List<OrderItem> orderItems = cartItems.stream()
                .map(ci -> {
                    log.info("DIAGNOSTIC: Processing item - Merchant: {}, Product: {}, Variant: {}",
                            ci.getMerchantId(), ci.getProductId(), ci.getVariantId());
                    return OrderItem.builder()
                            .productId(ci.getProductId())
                            .variantId(ci.getVariantId())
                            .merchantId(ci.getMerchantId())
                            .quantity(ci.getQuantity())
                            .price(ci.getPrice())
                            .order(order)
                            .build();
                })
                .collect(Collectors.toList());

        order.setItems(orderItems);
        Order savedOrder = orderRepository.save(order);
        log.info("DIAGNOSTIC: Order saved with ID: {}", savedOrder.getId());

        // Update Analytics and Inventory
        for (OrderItem item : orderItems) {
            updateMerchantAnalytics(item);
            updateProductInventory(item);
        }

        cartItemRepository.deleteByUserId(user.getId());
        log.info("DIAGNOSTIC: Cart cleared for user {}", user.getId());

        orderNotificationPublisher.sendOrderConfirmation(savedOrder, user.getEmail());
        return savedOrder.getId();
    }

    private void updateMerchantAnalytics(OrderItem item) {
        if (item.getMerchantId() == null) {
            log.error("DIAGNOSTIC ERROR: Cannot update analytics. MerchantId is NULL for Product: {}", item.getProductId());
            return;
        }

        log.info("DIAGNOSTIC: Updating Analytics table for Merchant: {}", item.getMerchantId());

        MerchantAnalytics stats = analyticsRepository
                .findByMerchantIdAndProductIdAndVariantId(item.getMerchantId(), item.getProductId(), item.getVariantId())
                .orElseGet(() -> {
                    log.info("DIAGNOSTIC: No existing record found for Merchant {}. Creating new entry.", item.getMerchantId());
                    return MerchantAnalytics.builder()
                            .merchantId(item.getMerchantId())
                            .productId(item.getProductId())
                            .variantId(item.getVariantId())
                            .amountGenerated(0.0)
                            .numberOfOrdersSold(0)
                            .build();
                });

        stats.setNumberOfOrdersSold(stats.getNumberOfOrdersSold() + item.getQuantity());
        stats.setAmountGenerated(stats.getAmountGenerated() + (item.getPrice() * item.getQuantity()));

        analyticsRepository.save(stats);
        log.info("DIAGNOSTIC: Successfully saved MerchantAnalytics for {}", item.getMerchantId());
    }

    private void updateProductInventory(OrderItem item) {
        String url = String.format(
                "https://product-service-jzzf.onrender.com/api/v1/products/inventory/%d?variantId=%s&stock=%d",
                item.getProductId(),
                item.getVariantId(),
                -item.getQuantity()
        );
        try {
            restTemplate.put(url, null);
            log.info("DIAGNOSTIC: Inventory update sent for Product: {}", item.getProductId());
        } catch (Exception e) {
            log.error("DIAGNOSTIC ERROR: Product Service Update failed: {}", e.getMessage());
        }
    }

    public List<OrderResponse> getOrderHistory() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return orderRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(o -> OrderResponse.builder()
                        .orderId(o.getId())
                        .totalAmount(o.getTotalAmount())
                        .status(o.getStatus())
                        .orderDate(o.getCreatedAt())
                        .build()).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public OrderItemDetailResponse getOrderItemDetail(Long itemId) {
        OrderItem item = orderItemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Order item not found"));

        return OrderItemDetailResponse.builder()
                .id(item.getId())
                .orderId(item.getOrder().getId())
                .productId(item.getProductId())
                .variantId(item.getVariantId())
                .merchantId(item.getMerchantId())
                .quantity(item.getQuantity())
                .price(item.getPrice())
                .build();
    }
}