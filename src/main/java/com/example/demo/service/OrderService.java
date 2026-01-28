package com.example.demo.service;

import com.example.demo.dto.response.OrderResponse;
import com.example.demo.entity.Cart;
import com.example.demo.entity.Order;
import com.example.demo.entity.OrderItem;
import com.example.demo.integration.OrderNotificationPublisher;
import com.example.demo.repository.CartRepository;
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
    private final CartRepository cartRepository;
    private final OrderNotificationPublisher orderNotificationPublisher;

    @Transactional
    public Long checkout() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Cart is empty, cannot checkout"));

        if(cart.getItems().isEmpty()) {
            throw new RuntimeException("Cart is empty");
        }

        double totalAmount = cart.getItems().stream().mapToDouble(item -> item.getPrice() * item.getQuantity()).sum();

        Order order = Order.builder()
                .userId(user.getId())
                .totalAmount(totalAmount)
                .status("CONFIRMED")
                .build();

        List<OrderItem> orderItems = cart.getItems().stream()
                .map(cartItem -> OrderItem.builder()
                        .merchantProductId(cartItem.getMerchantProductId())
                        .quantity(cartItem.getQuantity())
                        .price(cartItem.getPrice())
                        .order(order)
                        .build())
                .collect(Collectors.toList());

        order.setItems(orderItems);
        Order savedOrder = orderRepository.save(order);

        cartRepository.delete(cart);
        orderNotificationPublisher.sendOrderConfirmation(savedOrder, user.getEmail());
        return savedOrder.getId();
    }

    public List<OrderResponse> getOrderHistory() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return orderRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(order -> OrderResponse.builder()
                        .orderId(order.getId())
                        .totalAmount(order.getTotalAmount())
                        .status(order.getStatus())
                        .orderDate(order.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }
}