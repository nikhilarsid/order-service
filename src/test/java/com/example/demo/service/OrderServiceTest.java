package com.example.demo.service;

import com.example.demo.dto.response.ProductSnapshot;
import com.example.demo.entity.CartItem;
import com.example.demo.entity.Order;
import com.example.demo.entity.OrderItem;
import com.example.demo.entity.MerchantAnalytics;
import com.example.demo.enums.OrderStatus;
import com.example.demo.repository.CartItemRepository;
import com.example.demo.repository.MerchantAnalyticsRepository;
import com.example.demo.repository.OrderItemRepository;
import com.example.demo.repository.OrderRepository;
import com.example.demo.security.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private MerchantAnalyticsRepository merchantAnalyticsRepository;
    @Mock private RestTemplate restTemplate;
    @Mock private SecurityContext securityContext;
    @Mock private Authentication authentication;

    @InjectMocks
    private OrderService orderService;

    private User mockUser;
    private CartItem mockCartItem;

    @BeforeEach
    void setUp() {
        // Setup Security Context
        mockUser = User.builder().id("user-123").email("test@example.com").build();
        SecurityContextHolder.setContext(securityContext);

        // Inject the required Value for URL
        ReflectionTestUtils.setField(orderService, "productServiceUrl", "http://localhost:8080/products");

        // Setup common mock data
        mockCartItem = new CartItem();
        mockCartItem.setProductId(1);
        mockCartItem.setVariantId("v1");
        mockCartItem.setMerchantId("m1");
        mockCartItem.setQuantity(2);
    }

    @Test
    void checkout_Successful() {
        // Arrange
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(mockUser);
        when(cartItemRepository.findByUserId("user-123")).thenReturn(Collections.singletonList(mockCartItem));

        // Mock External Product Service Call
        Map<String, Object> productResponse = new HashMap<>();
        productResponse.put("success", true);
        Map<String, Object> data = new HashMap<>();
        data.put("name", "Laptop");

        Map<String, Object> seller = new HashMap<>();
        seller.put("merchantId", "m1");
        seller.put("price", 1000.0);
        seller.put("stock", 10);
        seller.put("merchantName", "TechStore");

        data.put("sellers", Collections.singletonList(seller));
        productResponse.put("data", data);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(productResponse));

        when(merchantAnalyticsRepository.findByMerchantIdAndProductIdAndVariantId(anyString(), anyInt(), anyString()))
                .thenReturn(Optional.empty());

        // Act
        String orderNumber = orderService.checkout();

        // Assert
        assertThat(orderNumber).isNotNull();
        verify(orderRepository, times(2)).save(any(Order.class));
        verify(orderItemRepository).save(any(OrderItem.class));
        verify(merchantAnalyticsRepository).save(any(MerchantAnalytics.class));
        verify(cartItemRepository).deleteAll(anyList());
    }

    @Test
    void checkout_ThrowsException_WhenCartIsEmpty() {
        // Arrange
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(mockUser);
        when(cartItemRepository.findByUserId("user-123")).thenReturn(Collections.emptyList());

        // Act & Assert
        assertThatThrownBy(() -> orderService.checkout())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Cart is empty");
    }

    @Test
    void checkout_ThrowsException_WhenStockIsInsufficient() {
        // Arrange
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(mockUser);
        when(cartItemRepository.findByUserId("user-123")).thenReturn(Collections.singletonList(mockCartItem));

        // Mock product with low stock (1 vs requested 2)
        Map<String, Object> productResponse = new HashMap<>();
        productResponse.put("success", true);
        Map<String, Object> data = new HashMap<>();
        data.put("name", "Laptop");
        Map<String, Object> seller = new HashMap<>();
        seller.put("merchantId", "m1");
        seller.put("price", 1000.0);
        seller.put("stock", 1);
        data.put("sellers", Collections.singletonList(seller));
        productResponse.put("data", data);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(productResponse));

        // Act & Assert
        assertThatThrownBy(() -> orderService.checkout())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Insufficient stock");
    }
}