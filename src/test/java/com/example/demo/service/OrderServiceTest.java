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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService Unit Tests")
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private OrderService orderService;

    private User testUser;
    private CartItem testCartItem;
    private Order testOrder;
    private OrderItem testOrderItem;
    private ProductSnapshot testProductSnapshot;

    @BeforeEach
    void setUp() {
        // Initialize test user
        testUser = new User();
        testUser.setId("user-123");
        testUser.setEmail("test@example.com");

        // Initialize cart item
        testCartItem = CartItem.builder()
                .id(1L)
                .userId("user-123")
                .productId(101)
                .variantId("variant-1")
                .merchantId("merchant-001")
                .quantity(2)
                .price(100.0)
                .build();

        // Initialize order
        testOrder = Order.builder()
                .id(1L)
                .userId("user-123")
                .orderNumber("ORD-123-UUID")
                .orderDate(LocalDateTime.now())
                .status(OrderStatus.CONFIRMED)
                .totalAmount(200.0)
                .items(new ArrayList<>())
                .build();

        // Initialize order item
        testOrderItem = OrderItem.builder()
                .id(1L)
                .productId(101)
                .variantId("variant-1")
                .merchantId("merchant-001")
                .merchantName("TechStore")
                .quantity(2)
                .price(100.0)
                .imageUrl("http://image.url/product.jpg")
                .order(testOrder)
                .build();

        // Initialize product snapshot
        testProductSnapshot = ProductSnapshot.builder()
                .name("Test Product")
                .price(100.0)
                .stock(10)
                .merchantName("TechStore")
                .imageUrl("http://image.url/product.jpg")
                .build();

        // Setup Security Context
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(testUser);

        // Set product service URL
        ReflectionTestUtils.setField(orderService, "productServiceUrl", "https://product-service.com/api/v1/products/");
    }

    // ==================== HAPPY PATH TESTS ====================

    @Test
    @DisplayName("✅ Checkout - Successfully create order from cart items")
    void testCheckout_Success() {
        // Arrange
        List<CartItem> cartItems = Collections.singletonList(testCartItem);
        
        try (MockedStatic<SecurityContextHolder> mockedSecurityHolder = mockStatic(SecurityContextHolder.class)) {
            mockedSecurityHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);
            
            when(cartItemRepository.findByUserId("user-123")).thenReturn(cartItems);
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
                Order order = invocation.getArgument(0);
                order.setId(1L);
                return order;
            });
            
            // Mock product service call
            Map<String, Object> responseBody = createProductServiceResponse();
            ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<>(responseBody, HttpStatus.OK);
            
            when(restTemplate.exchange(
                    anyString(),
                    eq(HttpMethod.GET),
                    isNull(),
                    any(ParameterizedTypeReference.class)
            )).thenReturn(responseEntity);

            when(orderItemRepository.save(any(OrderItem.class))).thenReturn(testOrderItem);

            // Act
            String orderNumber = orderService.checkout();

            // Assert
            assertNotNull(orderNumber);
            assertFalse(orderNumber.isEmpty());
            verify(orderRepository, times(2)).save(any(Order.class));
            verify(orderItemRepository, times(1)).save(any(OrderItem.class));
            verify(cartItemRepository, times(1)).deleteAll(cartItems);
            verify(restTemplate, times(2)).exchange(anyString(), any(), any(), any(ParameterizedTypeReference.class));
        }
    }

    @Test
    @DisplayName("✅ Get Order History - Return mapped DTO list")
    void testGetOrderHistory_Success() {
        // Arrange
        List<Order> orders = Arrays.asList(
                Order.builder()
                        .id(1L)
                        .orderNumber("ORD-001")
                        .userId("user-123")
                        .orderDate(LocalDateTime.now())
                        .totalAmount(500.0)
                        .status(OrderStatus.CONFIRMED)
                        .items(Arrays.asList(testOrderItem))
                        .build()
        );

        try (MockedStatic<SecurityContextHolder> mockedSecurityHolder = mockStatic(SecurityContextHolder.class)) {
            mockedSecurityHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);
            
            when(orderRepository.findByUserIdOrderByCreatedAtDesc("user-123")).thenReturn(orders);

            // Act
            List<OrderResponse> result = orderService.getOrderHistory();

            // Assert
            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("ORD-001", result.get(0).getOrderNumber());
            assertEquals(500.0, result.get(0).getTotalAmount());
            verify(orderRepository, times(1)).findByUserIdOrderByCreatedAtDesc("user-123");
        }
    }

    @Test
    @DisplayName("✅ Get Order Item Detail - Return OrderItemDto with correct data")
    void testGetOrderItemDetail_Success() {
        // Arrange
        when(orderItemRepository.findById(1L)).thenReturn(Optional.of(testOrderItem));

        // Act
        OrderItemDto result = orderService.getOrderItemDetail(1L);

        // Assert
        assertNotNull(result);
        assertEquals(101, result.getProductId());
        assertEquals("variant-1", result.getVariantId());
        assertEquals("TechStore", result.getMerchantName());
        assertEquals(2, result.getQuantity());
        assertEquals(100.0, result.getPrice());
    }

    // ==================== VALIDATION FAILURE TESTS ====================

    @Test
    @DisplayName("❌ Checkout - Throws exception when cart is empty")
    void testCheckout_EmptyCart() {
        // Arrange
        try (MockedStatic<SecurityContextHolder> mockedSecurityHolder = mockStatic(SecurityContextHolder.class)) {
            mockedSecurityHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);
            
            when(cartItemRepository.findByUserId("user-123")).thenReturn(Collections.emptyList());

            // Act & Assert
            RuntimeException exception = assertThrows(RuntimeException.class, () -> orderService.checkout());
            assertEquals("Cart is empty", exception.getMessage());
            verify(orderRepository, never()).save(any());
        }
    }

    @Test
    @DisplayName("❌ Checkout - Throws exception when product data is unavailable")
    void testCheckout_ProductUnavailable() {
        // Arrange
        List<CartItem> cartItems = Collections.singletonList(testCartItem);
        
        try (MockedStatic<SecurityContextHolder> mockedSecurityHolder = mockStatic(SecurityContextHolder.class)) {
            mockedSecurityHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);
            
            when(cartItemRepository.findByUserId("user-123")).thenReturn(cartItems);
            when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

            // Mock product service returning null (product not found)
            ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<>(
                    Collections.singletonMap("success", false),
                    HttpStatus.OK
            );
            
            when(restTemplate.exchange(
                    anyString(),
                    eq(HttpMethod.GET),
                    isNull(),
                    any(ParameterizedTypeReference.class)
            )).thenReturn(responseEntity);

            // Act & Assert
            RuntimeException exception = assertThrows(RuntimeException.class, () -> orderService.checkout());
            assertTrue(exception.getMessage().contains("Item out of stock or unavailable"));
            verify(cartItemRepository, never()).deleteAll(any());
        }
    }

    @Test
    @DisplayName("❌ Checkout - Throws exception when insufficient stock")
    void testCheckout_InsufficientStock() {
        // Arrange
        List<CartItem> cartItems = Collections.singletonList(testCartItem);
        
        try (MockedStatic<SecurityContextHolder> mockedSecurityHolder = mockStatic(SecurityContextHolder.class)) {
            mockedSecurityHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);
            
            when(cartItemRepository.findByUserId("user-123")).thenReturn(cartItems);
            when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

            // Mock product service with insufficient stock
            Map<String, Object> responseBody = createProductServiceResponse();
            Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
            List<Map<String, Object>> sellers = (List<Map<String, Object>>) data.get("sellers");
            sellers.get(0).put("stock", 1); // Only 1 item in stock, but requesting 2

            ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<>(responseBody, HttpStatus.OK);
            
            when(restTemplate.exchange(
                    anyString(),
                    eq(HttpMethod.GET),
                    isNull(),
                    any(ParameterizedTypeReference.class)
            )).thenReturn(responseEntity);

            // Act & Assert
            RuntimeException exception = assertThrows(RuntimeException.class, () -> orderService.checkout());
            assertTrue(exception.getMessage().contains("Insufficient stock"));
            verify(cartItemRepository, never()).deleteAll(any());
        }
    }

    @Test
    @DisplayName("❌ Get Order Item Detail - Throws exception when item not found")
    void testGetOrderItemDetail_NotFound() {
        // Arrange
        when(orderItemRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> orderService.getOrderItemDetail(999L));
        assertEquals("Order Item not found", exception.getMessage());
    }

    // ==================== BUSINESS RULE VIOLATION TESTS ====================

    @Test
    @DisplayName("❌ Checkout - Throws exception when merchant ID does not match")
    void testCheckout_MerchantMismatch() {
        // Arrange
        CartItem cartWithWrongMerchant = CartItem.builder()
                .userId("user-123")
                .productId(101)
                .variantId("variant-1")
                .merchantId("wrong-merchant-id")
                .quantity(2)
                .price(100.0)
                .build();

        List<CartItem> cartItems = Collections.singletonList(cartWithWrongMerchant);
        
        try (MockedStatic<SecurityContextHolder> mockedSecurityHolder = mockStatic(SecurityContextHolder.class)) {
            mockedSecurityHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);
            
            when(cartItemRepository.findByUserId("user-123")).thenReturn(cartItems);
            when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

            Map<String, Object> responseBody = createProductServiceResponse();
            ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<>(responseBody, HttpStatus.OK);
            
            when(restTemplate.exchange(
                    anyString(),
                    eq(HttpMethod.GET),
                    isNull(),
                    any(ParameterizedTypeReference.class)
            )).thenReturn(responseEntity);

            // Act & Assert
            RuntimeException exception = assertThrows(RuntimeException.class, () -> orderService.checkout());
            assertTrue(exception.getMessage().contains("Item out of stock or unavailable"));
            verify(cartItemRepository, never()).deleteAll(any());
        }
    }

    @Test
    @DisplayName("❌ Checkout - Handles external service error gracefully")
    void testCheckout_ExternalServiceError() {
        // Arrange
        List<CartItem> cartItems = Collections.singletonList(testCartItem);
        
        try (MockedStatic<SecurityContextHolder> mockedSecurityHolder = mockStatic(SecurityContextHolder.class)) {
            mockedSecurityHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);
            
            when(cartItemRepository.findByUserId("user-123")).thenReturn(cartItems);
            when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

            // Mock REST template throwing HTTP error
            when(restTemplate.exchange(
                    anyString(),
                    eq(HttpMethod.GET),
                    isNull(),
                    any(ParameterizedTypeReference.class)
            )).thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));

            // Act & Assert
            RuntimeException exception = assertThrows(RuntimeException.class, () -> orderService.checkout());
            assertTrue(exception.getMessage().contains("Item out of stock or unavailable"));
            verify(cartItemRepository, never()).deleteAll(any());
        }
    }

    // ==================== EXCEPTION HANDLING TESTS ====================

    @Test
    @DisplayName("✅ Checkout - Correctly calculates total amount from multiple items")
    void testCheckout_CorrectTotalCalculation() {
        // Arrange
        CartItem item1 = CartItem.builder()
                .userId("user-123")
                .productId(101)
                .variantId("var-1")
                .merchantId("merchant-001")
                .quantity(2)
                .price(100.0)
                .build();

        CartItem item2 = CartItem.builder()
                .userId("user-123")
                .productId(102)
                .variantId("var-2")
                .merchantId("merchant-002")
                .quantity(3)
                .price(50.0)
                .build();

        List<CartItem> cartItems = Arrays.asList(item1, item2);
        
        try (MockedStatic<SecurityContextHolder> mockedSecurityHolder = mockStatic(SecurityContextHolder.class)) {
            mockedSecurityHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);
            
            when(cartItemRepository.findByUserId("user-123")).thenReturn(cartItems);

            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
                Order order = invocation.getArgument(0);
                order.setId(1L);
                return order;
            });
            
            // Mock product service responses
            ResponseEntity<Map<String, Object>> response1 = new ResponseEntity<>(
                    createProductServiceResponse(), HttpStatus.OK);
            
            Map<String, Object> response2Data = createProductServiceResponse();
            Map<String, Object> data2 = (Map<String, Object>) response2Data.get("data");
            List<Map<String, Object>> sellers2 = (List<Map<String, Object>>) data2.get("sellers");
            sellers2.get(0).put("price", 50.0);
            ResponseEntity<Map<String, Object>> response2 = new ResponseEntity<>(response2Data, HttpStatus.OK);
            
            when(restTemplate.exchange(
                    anyString(),
                    eq(HttpMethod.GET),
                    isNull(),
                    any(ParameterizedTypeReference.class)
            )).thenReturn(response1, response2, response1, response2);

            when(orderItemRepository.save(any(OrderItem.class)))
                    .thenReturn(OrderItem.builder().id(1L).build());

            // Act
            String orderNumber = orderService.checkout();

            // Assert
            assertNotNull(orderNumber);
            
            // Verify the total was calculated correctly (2*100 + 3*50 = 350)
            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository, times(2)).save(orderCaptor.capture());
            
            Order finalOrder = orderCaptor.getAllValues().get(1);
            assertEquals(350.0, finalOrder.getTotalAmount());
        }
    }

    @Test
    @DisplayName("✅ Get Order History - Return empty list when no orders exist")
    void testGetOrderHistory_NoOrders() {
        // Arrange
        try (MockedStatic<SecurityContextHolder> mockedSecurityHolder = mockStatic(SecurityContextHolder.class)) {
            mockedSecurityHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);
            
            when(orderRepository.findByUserIdOrderByCreatedAtDesc("user-123")).thenReturn(Collections.emptyList());

            // Act
            List<OrderResponse> result = orderService.getOrderHistory();

            // Assert
            assertNotNull(result);
            assertTrue(result.isEmpty());
            verify(orderRepository, times(1)).findByUserIdOrderByCreatedAtDesc("user-123");
        }
    }

    @Test
    @DisplayName("✅ Checkout - Order status should be CONFIRMED after creation")
    void testCheckout_OrderStatusConfirmed() {
        // Arrange
        List<CartItem> cartItems = Collections.singletonList(testCartItem);
        
        try (MockedStatic<SecurityContextHolder> mockedSecurityHolder = mockStatic(SecurityContextHolder.class)) {
            mockedSecurityHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);
            
            when(cartItemRepository.findByUserId("user-123")).thenReturn(cartItems);
            when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

            Map<String, Object> responseBody = createProductServiceResponse();
            ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<>(responseBody, HttpStatus.OK);
            
            when(restTemplate.exchange(
                    anyString(),
                    eq(HttpMethod.GET),
                    isNull(),
                    any(ParameterizedTypeReference.class)
            )).thenReturn(responseEntity);

            when(orderItemRepository.save(any(OrderItem.class))).thenReturn(testOrderItem);

            // Act
            orderService.checkout();

            // Assert
            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository, times(2)).save(orderCaptor.capture());
            
            Order createdOrder = orderCaptor.getAllValues().get(0);
            assertEquals(OrderStatus.CONFIRMED, createdOrder.getStatus());
        }
    }

    // ==================== HELPER METHODS ====================

    private Map<String, Object> createProductServiceResponse() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);

        Map<String, Object> data = new HashMap<>();
        data.put("name", "Test Product");
        data.put("imageUrls", Arrays.asList("http://image.url/product.jpg"));

        Map<String, Object> seller = new HashMap<>();
        seller.put("merchantId", "merchant-001");
        seller.put("merchantName", "TechStore");
        seller.put("price", 100.0);
        seller.put("stock", 10);

        data.put("sellers", Collections.singletonList(seller));
        response.put("data", data);

        return response;
    }
}
