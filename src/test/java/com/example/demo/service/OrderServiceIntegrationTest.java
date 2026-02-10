package com.example.demo.service;

import com.example.demo.dto.request.AddToCartRequest;
import com.example.demo.dto.response.CartResponse;
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
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Integration Tests - Order and Cart Services")
class OrderServiceIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private CartService cartService;

    @MockBean
    private OrderRepository orderRepository;

    @MockBean
    private OrderItemRepository orderItemRepository;

    @MockBean
    private CartItemRepository cartItemRepository;

    @MockBean
    private RestTemplate restTemplate;

    @MockBean
    private SecurityContext securityContext;

    @MockBean
    private Authentication authentication;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId("user-integration-test");
        testUser.setEmail("integration@test.com");

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(testUser);
    }

    // ==================== INTEGRATION TESTS: ADD TO CART + CHECKOUT ====================

    @Test
    @DisplayName("🔄 Integration: Add item to cart, then checkout successfully")
    void testAddToCartThenCheckout_CompleteFlow() {
        // Arrange
        AddToCartRequest addRequest = AddToCartRequest.builder()
                .productId(101)
                .variantId("variant-1")
                .merchantId("merchant-001")
                .quantity(2)
                .build();

        CartItem savedCartItem = CartItem.builder()
                .id(1L)
                .userId("user-integration-test")
                .productId(101)
                .variantId("variant-1")
                .merchantId("merchant-001")
                .quantity(2)
                .price(100.0)
                .build();

        Order order = Order.builder()
                .id(1L)
                .orderNumber("ORD-INTEGRATION-001")
                .userId("user-integration-test")
                .status(OrderStatus.CONFIRMED)
                .items(new ArrayList<>())
                .build();

        OrderItem orderItem = OrderItem.builder()
                .id(1L)
                .productId(101)
                .variantId("variant-1")
                .merchantId("merchant-001")
                .merchantName("TechStore")
                .quantity(2)
                .price(100.0)
                .imageUrl("http://image.url/product.jpg")
                .order(order)
                .build();

        try (MockedStatic<SecurityContextHolder> mockedSecurityHolder = mockStatic(SecurityContextHolder.class)) {
            mockedSecurityHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);

            // Mock product service for add to cart
            Map<String, Object> productResponse = createProductServiceResponse();
            ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<>(productResponse, HttpStatus.OK);

            when(restTemplate.exchange(
                    anyString(),
                    eq(HttpMethod.GET),
                    any(),
                    any(ParameterizedTypeReference.class)
            )).thenReturn(responseEntity);

            when(cartItemRepository.findByUserIdAndProductIdAndVariantId(
                    "user-integration-test", 101, "variant-1"))
                    .thenReturn(Optional.empty());

            when(cartItemRepository.save(any(CartItem.class))).thenReturn(savedCartItem);

            // Step 1: Add to cart
            cartService.addToCart(addRequest);

            verify(cartItemRepository, times(1)).save(any(CartItem.class));

            // Step 2: Checkout
            when(cartItemRepository.findByUserId("user-integration-test"))
                    .thenReturn(Collections.singletonList(savedCartItem));

            when(orderRepository.save(any(Order.class))).thenReturn(order);
            when(orderItemRepository.save(any(OrderItem.class))).thenReturn(orderItem);

            String orderNumber = orderService.checkout();

            // Assert
            assertNotNull(orderNumber);
            assertEquals("ORD-INTEGRATION-001", orderNumber);
            verify(cartItemRepository, times(1)).deleteAll(any());
        }
    }

    @Test
    @DisplayName("🔄 Integration: Multiple items in cart processed correctly in checkout")
    void testMultipleItemsCheckout_CorrectTotalAndItems() {
        // Arrange
        CartItem item1 = CartItem.builder()
                .id(1L)
                .userId("user-integration-test")
                .productId(101)
                .variantId("var-1")
                .merchantId("merchant-001")
                .quantity(2)
                .price(100.0)
                .build();

        CartItem item2 = CartItem.builder()
                .id(2L)
                .userId("user-integration-test")
                .productId(102)
                .variantId("var-2")
                .merchantId("merchant-002")
                .quantity(1)
                .price(200.0)
                .build();

        List<CartItem> cartItems = Arrays.asList(item1, item2);

        Order savedOrder = Order.builder()
                .id(1L)
                .orderNumber("ORD-MULTI-001")
                .userId("user-integration-test")
                .status(OrderStatus.CONFIRMED)
                .items(new ArrayList<>())
                .build();

        try (MockedStatic<SecurityContextHolder> mockedSecurityHolder = mockStatic(SecurityContextHolder.class)) {
            mockedSecurityHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);

            when(cartItemRepository.findByUserId("user-integration-test")).thenReturn(cartItems);
            when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

            // Mock product service responses for both items
            ResponseEntity<Map<String, Object>> response1 = new ResponseEntity<>(
                    createProductServiceResponse(), HttpStatus.OK);
            ResponseEntity<Map<String, Object>> response2 = new ResponseEntity<>(
                    createProductServiceResponse(), HttpStatus.OK);

            when(restTemplate.exchange(
                    contains("101"),
                    eq(HttpMethod.GET),
                    isNull(),
                    any(ParameterizedTypeReference.class)
            )).thenReturn(response1);

            when(restTemplate.exchange(
                    contains("102"),
                    eq(HttpMethod.GET),
                    isNull(),
                    any(ParameterizedTypeReference.class)
            )).thenReturn(response2);

            when(orderItemRepository.save(any(OrderItem.class)))
                    .thenReturn(OrderItem.builder().id(1L).build());

            // Act
            orderService.checkout();

            // Assert - verify 2 order items created
            verify(orderItemRepository, times(2)).save(any(OrderItem.class));
            verify(cartItemRepository, times(1)).deleteAll(cartItems);
        }
    }

    @Test
    @DisplayName("🔄 Integration: Checkout fails if any item is out of stock")
    void testCheckout_PartialOutOfStock_EntireCheckoutFails() {
        // Arrange
        CartItem item1 = CartItem.builder()
                .userId("user-integration-test")
                .productId(101)
                .variantId("var-1")
                .merchantId("merchant-001")
                .quantity(2)
                .price(100.0)
                .build();

        CartItem item2 = CartItem.builder()
                .userId("user-integration-test")
                .productId(102)
                .variantId("var-2")
                .merchantId("merchant-002")
                .quantity(10)
                .price(200.0)
                .build();

        List<CartItem> cartItems = Arrays.asList(item1, item2);

        Order savedOrder = Order.builder()
                .id(1L)
                .orderNumber("ORD-FAIL-001")
                .userId("user-integration-test")
                .status(OrderStatus.CONFIRMED)
                .items(new ArrayList<>())
                .build();

        try (MockedStatic<SecurityContextHolder> mockedSecurityHolder = mockStatic(SecurityContextHolder.class)) {
            mockedSecurityHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);

            when(cartItemRepository.findByUserId("user-integration-test")).thenReturn(cartItems);
            when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

            // Item 1 - OK
            ResponseEntity<Map<String, Object>> response1 = new ResponseEntity<>(
                    createProductServiceResponse(), HttpStatus.OK);

            // Item 2 - Out of stock
            Map<String, Object> response2Data = createProductServiceResponse();
            Map<String, Object> data = (Map<String, Object>) response2Data.get("data");
            List<Map<String, Object>> sellers = (List<Map<String, Object>>) data.get("sellers");
            sellers.get(0).put("stock", 2); // Only 2 in stock, requesting 10

            ResponseEntity<Map<String, Object>> response2 = new ResponseEntity<>(response2Data, HttpStatus.OK);

            when(restTemplate.exchange(
                    contains("101"),
                    eq(HttpMethod.GET),
                    isNull(),
                    any(ParameterizedTypeReference.class)
            )).thenReturn(response1);

            when(restTemplate.exchange(
                    contains("102"),
                    eq(HttpMethod.GET),
                    isNull(),
                    any(ParameterizedTypeReference.class)
            )).thenReturn(response2);

            // Act & Assert
            assertThrows(RuntimeException.class, () -> orderService.checkout());
            
            // Verify cart was NOT cleared
            verify(cartItemRepository, never()).deleteAll(any());
        }
    }

    // ==================== RECOVERY AND ROLLBACK TESTS ====================

    @Test
    @DisplayName("🔄 Integration: Cart remains unchanged if checkout fails mid-process")
    void testCheckout_RollbackOnFailure_CartUnchanged() {
        // Arrange
        CartItem cartItem = CartItem.builder()
                .userId("user-integration-test")
                .productId(101)
                .variantId("var-1")
                .merchantId("merchant-001")
                .quantity(2)
                .price(100.0)
                .build();

        List<CartItem> cartItems = Collections.singletonList(cartItem);

        Order savedOrder = Order.builder()
                .id(1L)
                .orderNumber("ORD-ROLLBACK-001")
                .userId("user-integration-test")
                .status(OrderStatus.CONFIRMED)
                .items(new ArrayList<>())
                .build();

        try (MockedStatic<SecurityContextHolder> mockedSecurityHolder = mockStatic(SecurityContextHolder.class)) {
            mockedSecurityHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);

            when(cartItemRepository.findByUserId("user-integration-test")).thenReturn(cartItems);
            when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

            // Mock external service throwing exception
            when(restTemplate.exchange(
                    anyString(),
                    eq(HttpMethod.GET),
                    isNull(),
                    any(ParameterizedTypeReference.class)
            )).thenThrow(new RuntimeException("Service unavailable"));

            // Act & Assert
            assertThrows(RuntimeException.class, () -> orderService.checkout());

            // Verify cart was NOT deleted
            verify(cartItemRepository, never()).deleteAll(any());
        }
    }

    @Test
    @DisplayName("🔄 Integration: Verify cart calculation with real entities")
    void testGetCart_CalculatesTotalCorrectly() {
        // Arrange
        try (MockedStatic<SecurityContextHolder> mockedSecurityHolder = mockStatic(SecurityContextHolder.class)) {
            mockedSecurityHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);

            CartItem item1 = CartItem.builder()
                    .id(1L)
                    .userId("user-integration-test")
                    .productId(101)
                    .variantId("var-1")
                    .merchantId("merchant-001")
                    .quantity(3)
                    .price(50.0)
                    .build();

            CartItem item2 = CartItem.builder()
                    .id(2L)
                    .userId("user-integration-test")
                    .productId(102)
                    .variantId("var-2")
                    .merchantId("merchant-002")
                    .quantity(2)
                    .price(75.0)
                    .build();

            List<CartItem> cartItems = Arrays.asList(item1, item2);

            when(cartItemRepository.findByUserId("user-integration-test")).thenReturn(cartItems);

            // Act
            CartResponse cartResponse = cartService.getMyCart();

            // Assert
            assertEquals(2, cartResponse.getItems().size());
            // Total: (3 * 50) + (2 * 75) = 150 + 150 = 300
            assertEquals(300.0, cartResponse.getTotalValue());
        }
    }

    @Test
    @DisplayName("🔄 Integration: Handle empty cart gracefully")
    void testGetCart_EmptyCart_ReturnsValidResponse() {
        // Arrange
        try (MockedStatic<SecurityContextHolder> mockedSecurityHolder = mockStatic(SecurityContextHolder.class)) {
            mockedSecurityHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);

            when(cartItemRepository.findByUserId("user-integration-test"))
                    .thenReturn(Collections.emptyList());

            // Act
            CartResponse cartResponse = cartService.getMyCart();

            // Assert
            assertNotNull(cartResponse);
            assertTrue(cartResponse.getItems().isEmpty());
            assertEquals(0.0, cartResponse.getTotalValue());
        }
    }

    // ==================== EDGE CASES ====================

    @Test
    @DisplayName("🔄 Integration: Large quantity cart checkout")
    void testCheckout_LargeQuantities() {
        // Arrange
        CartItem item = CartItem.builder()
                .userId("user-integration-test")
                .productId(101)
                .variantId("var-1")
                .merchantId("merchant-001")
                .quantity(100)
                .price(10.0)
                .build();

        Order savedOrder = Order.builder()
                .id(1L)
                .orderNumber("ORD-LARGE-001")
                .userId("user-integration-test")
                .status(OrderStatus.CONFIRMED)
                .items(new ArrayList<>())
                .build();

        try (MockedStatic<SecurityContextHolder> mockedSecurityHolder = mockStatic(SecurityContextHolder.class)) {
            mockedSecurityHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);

            when(cartItemRepository.findByUserId("user-integration-test"))
                    .thenReturn(Collections.singletonList(item));

            when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

            Map<String, Object> productResponse = createProductServiceResponse();
            Map<String, Object> data = (Map<String, Object>) productResponse.get("data");
            List<Map<String, Object>> sellers = (List<Map<String, Object>>) data.get("sellers");
            sellers.get(0).put("stock", 500);

            ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<>(productResponse, HttpStatus.OK);

            when(restTemplate.exchange(
                    anyString(),
                    eq(HttpMethod.GET),
                    isNull(),
                    any(ParameterizedTypeReference.class)
            )).thenReturn(responseEntity);

            when(orderItemRepository.save(any(OrderItem.class)))
                    .thenReturn(OrderItem.builder().id(1L).build());

            // Act
            String orderNumber = orderService.checkout();

            // Assert
            assertNotNull(orderNumber);
            assertEquals("ORD-LARGE-001", orderNumber);

            // Verify order total is correct: 100 * 10 = 1000
            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository, times(2)).save(orderCaptor.capture());
            
            Order finalOrder = orderCaptor.getAllValues().get(1);
            assertEquals(1000.0, finalOrder.getTotalAmount());
        }
    }

    @Test
    @DisplayName("🔄 Integration: Decimal price calculations")
    void testCheckout_DecimalPriceCalculations() {
        // Arrange
        CartItem item = CartItem.builder()
                .userId("user-integration-test")
                .productId(101)
                .variantId("var-1")
                .merchantId("merchant-001")
                .quantity(3)
                .price(19.99)
                .build();

        Order savedOrder = Order.builder()
                .id(1L)
                .orderNumber("ORD-DECIMAL-001")
                .userId("user-integration-test")
                .status(OrderStatus.CONFIRMED)
                .items(new ArrayList<>())
                .build();

        try (MockedStatic<SecurityContextHolder> mockedSecurityHolder = mockStatic(SecurityContextHolder.class)) {
            mockedSecurityHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);

            when(cartItemRepository.findByUserId("user-integration-test"))
                    .thenReturn(Collections.singletonList(item));

            when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

            Map<String, Object> productResponse = createProductServiceResponse();
            Map<String, Object> data = (Map<String, Object>) productResponse.get("data");
            List<Map<String, Object>> sellers = (List<Map<String, Object>>) data.get("sellers");
            sellers.get(0).put("price", 19.99);

            ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<>(productResponse, HttpStatus.OK);

            when(restTemplate.exchange(
                    anyString(),
                    eq(HttpMethod.GET),
                    isNull(),
                    any(ParameterizedTypeReference.class)
            )).thenReturn(responseEntity);

            when(orderItemRepository.save(any(OrderItem.class)))
                    .thenReturn(OrderItem.builder().id(1L).build());

            // Act
            orderService.checkout();

            // Assert - verify correct decimal handling (3 * 19.99 = 59.97)
            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository, times(2)).save(orderCaptor.capture());
            
            Order finalOrder = orderCaptor.getAllValues().get(1);
            assertEquals(59.97, finalOrder.getTotalAmount(), 0.01);
        }
    }

    // ==================== HELPER METHODS ====================

    private Map<String, Object> createProductServiceResponse() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);

        Map<String, Object> data = new HashMap<>();
        data.put("name", "Test Product");
        data.put("imageUrls", Collections.singletonList("http://image.url/product.jpg"));

        Map<String, Object> seller = new HashMap<>();
        seller.put("merchantId", "merchant-001");
        seller.put("merchantName", "TechStore");
        seller.put("price", 100.0);
        seller.put("stock", 100);

        data.put("sellers", Collections.singletonList(seller));
        response.put("data", data);

        return response;
    }
}
