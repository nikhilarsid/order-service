package com.example.demo.service;

import com.example.demo.dto.response.OrderResponse;
import com.example.demo.dto.response.OrderItemDetailResponse;
import com.example.demo.entity.CartItem;
import com.example.demo.entity.Order;
import com.example.demo.enums.OrderStatus;   // Add this
import com.example.demo.enums.PaymentStatus; // Add this
import com.example.demo.entity.OrderItem;
import com.example.demo.integration.OrderNotificationPublisher;
import com.example.demo.repository.CartItemRepository;
import com.example.demo.repository.OrderItemRepository;
import com.example.demo.repository.OrderRepository;
import com.example.demo.security.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartItemRepository cartItemRepository;
    private final OrderItemRepository orderItemRepository; // Ensure this is injected
    private final OrderNotificationPublisher orderNotificationPublisher;

    @Transactional
    public Long checkout() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        List<CartItem> cartItems = cartItemRepository.findByUserId(user.getId());
        if (cartItems.isEmpty()) {
            throw new RuntimeException("Cannot place order: Your cart is empty.");
        }

        double totalAmount = cartItems.stream()
                .mapToDouble(i -> i.getPrice() * i.getQuantity())
                .sum();

        Order order = Order.builder()
                .userId(user.getId())
                .totalAmount(totalAmount)
                .status(OrderStatus.CONFIRMED) // Updated to Enum
                .address("Default Address")
                .paymentStatus(PaymentStatus.PAID) // Updated to Enum
                .build();

        List<OrderItem> orderItems = cartItems.stream()
                .map(ci -> OrderItem.builder()
                        .productId(ci.getProductId())
                        .variantId(ci.getVariantId())
                        .merchantId(ci.getMerchantId())
                        .quantity(ci.getQuantity())
                        .price(ci.getPrice())
                        .order(order)
                        .build())
                .collect(Collectors.toList());

        order.setItems(orderItems);
        Order savedOrder = orderRepository.save(order);

        cartItemRepository.deleteByUserId(user.getId());
        orderNotificationPublisher.sendOrderConfirmation(savedOrder, user.getEmail());

        return savedOrder.getId();
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

    // RESTORED: Fetch specific order item by its ID
    public OrderItem getOrderItem(Long itemId) {
        return orderItemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Order item not found with ID: " + itemId));
    }

    @Transactional(readOnly = true)
    public OrderItemDetailResponse getOrderItemDetail(Long itemId) {
        OrderItem item = orderItemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Order item not found with ID: " + itemId));

        // Mapping to a flat DTO ensures the Order ID is visible and avoids Lazy Loading errors
        return OrderItemDetailResponse.builder()
                .id(item.getId())
                .orderId(item.getOrder().getId()) // Safely access the Order ID
                .productId(item.getProductId())
                .variantId(item.getVariantId())
                .merchantId(item.getMerchantId())
                .quantity(item.getQuantity())
                .price(item.getPrice())
                .build();
    }
}