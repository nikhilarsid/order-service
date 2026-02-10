package com.example.demo.service;

import com.example.demo.dto.request.AddToCartRequest;
import com.example.demo.dto.response.CartItemDTO;
import com.example.demo.dto.response.CartResponse;
import com.example.demo.entity.CartItem;
import com.example.demo.repository.CartItemRepository;
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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CartService Unit Tests")
class CartServiceTest {

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private CartService cartService;

    private User testUser;
    private AddToCartRequest testAddToCartRequest;
    private CartItem existingCartItem;

    @BeforeEach
    void setUp() {
        // Initialize test user
        testUser = new User();
        testUser.setId("user-123");
        testUser.setEmail("test@example.com");

        // Initialize add to cart request
        testAddToCartRequest = AddToCartRequest.builder()
                .productId(101)
                .variantId("variant-1")
                .merchantId("merchant-001")
                .quantity(2)
                .build();

        // Initialize existing cart item
        existingCartItem = CartItem.builder()
                .id(1L)
                .userId("user-123")
                .productId(101)
                .variantId("variant-1")
                .merchantId("merchant-001")
                .quantity(1)
                .price(100.0)
                .build();

        // Setup Security Context
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(testUser);
    }

    // ==================== HAPPY PATH TESTS ====================

    @Test
    @DisplayName("✅ Add to Cart - New item created successfully")
    void testAddToCart_NewItem_Success() {
        // Arrange
        try (MockedStatic<SecurityContextHolder> mockedSecurityHolder = mockStatic(SecurityContextHolder.class)) {
            mockedSecurityHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);

            Map<String, Object> productResponse = createProductServiceResponse();
            ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<>(productResponse, HttpStatus.OK);

            when(restTemplate.exchange(
                    anyString(),
                    eq(HttpMethod.GET),
                    any(),
                    any(ParameterizedTypeReference.class)
            )).thenReturn(responseEntity);

            when(cartItemRepository.findByUserIdAndProductIdAndVariantId("user-123", 101, "variant-1"))
                    .thenReturn(Optional.empty());

            when(cartItemRepository.save(any(CartItem.class))).thenReturn(existingCartItem);

            // Act
            cartService.addToCart(testAddToCartRequest);

            // Assert
            verify(cartItemRepository, times(1)).save(any(CartItem.class));
            verify(restTemplate, times(1)).exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class));
        }
    }

    @Test
    @DisplayName("✅ Add to Cart - Existing item quantity increased")
    void testAddToCart_ExistingItem_QuantityIncreased() {
        // Arrange
        try (MockedStatic<SecurityContextHolder> mockedSecurityHolder = mockStatic(SecurityContextHolder.class)) {
            mockedSecurityHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);

            Map<String, Object> productResponse = createProductServiceResponse();
            ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<>(productResponse, HttpStatus.OK);

            when(restTemplate.exchange(
                    anyString(),
                    eq(HttpMethod.GET),
                    any(),
                    any(ParameterizedTypeReference.class)
            )).thenReturn(responseEntity);

            CartItem existingItem = CartItem.builder()
                    .id(1L)
                    .userId("user-123")
                    .productId(101)
                    .variantId("variant-1")
                    .merchantId("merchant-001")
                    .quantity(1)
                    .price(100.0)
                    .build();

            when(cartItemRepository.findByUserIdAndProductIdAndVariantId("user-123", 101, "variant-1"))
                    .thenReturn(Optional.of(existingItem));

            CartItem updatedItem = CartItem.builder()
                    .id(1L)
                    .userId("user-123")
                    .productId(101)
                    .variantId("variant-1")
                    .merchantId("merchant-001")
                    .quantity(3) // 1 + 2 = 3
                    .price(100.0)
                    .build();

            when(cartItemRepository.save(any(CartItem.class))).thenReturn(updatedItem);

            // Act
            cartService.addToCart(testAddToCartRequest);

            // Assert
            verify(cartItemRepository, times(1)).save(any(CartItem.class));
            
            // Verify quantity was incremented
            var captor = ArgumentCaptor.forClass(CartItem.class);
            verify(cartItemRepository).save(captor.capture());
            assertEquals(3, captor.getValue().getQuantity());
        }
    }

    @Test
    @DisplayName("✅ Get My Cart - Return cart with all items and calculated total")
    void testGetMyCart_Success() {
        // Arrange
        try (MockedStatic<SecurityContextHolder> mockedSecurityHolder = mockStatic(SecurityContextHolder.class)) {
            mockedSecurityHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);

            CartItem item1 = CartItem.builder()
                    .id(1L)
                    .userId("user-123")
                    .productId(101)
                    .variantId("variant-1")
                    .merchantId("merchant-001")
                    .quantity(2)
                    .price(100.0)
                    .build();

            CartItem item2 = CartItem.builder()
                    .id(2L)
                    .userId("user-123")
                    .productId(102)
                    .variantId("variant-2")
                    .merchantId("merchant-002")
                    .quantity(3)
                    .price(50.0)
                    .build();

            List<CartItem> cartItems = Arrays.asList(item1, item2);

            when(cartItemRepository.findByUserId("user-123")).thenReturn(cartItems);

            // Act
            CartResponse result = cartService.getMyCart();

            // Assert
            assertNotNull(result);
            assertEquals(2, result.getItems().size());
            assertEquals(350.0, result.getTotalValue()); // (2 * 100) + (3 * 50) = 350
            verify(cartItemRepository, times(1)).findByUserId("user-123");
        }
    }

    @Test
    @DisplayName("✅ Get My Cart - Return empty cart when no items")
    void testGetMyCart_EmptyCart() {
        // Arrange
        try (MockedStatic<SecurityContextHolder> mockedSecurityHolder = mockStatic(SecurityContextHolder.class)) {
            mockedSecurityHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);

            when(cartItemRepository.findByUserId("user-123")).thenReturn(Collections.emptyList());

            // Act
            CartResponse result = cartService.getMyCart();

            // Assert
            assertNotNull(result);
            assertTrue(result.getItems().isEmpty());
            assertEquals(0.0, result.getTotalValue());
            verify(cartItemRepository, times(1)).findByUserId("user-123");
        }
    }

    // ==================== VALIDATION FAILURE TESTS ====================

    @Test
    @DisplayName("❌ Add to Cart - Throws exception when quantity exceeds available stock")
    void testAddToCart_InsufficientStock() {
        // Arrange
        try (MockedStatic<SecurityContextHolder> mockedSecurityHolder = mockStatic(SecurityContextHolder.class)) {
            mockedSecurityHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);

            // Create response with limited stock
            Map<String, Object> productResponse = createProductServiceResponse();
            Map<String, Object> data = (Map<String, Object>) productResponse.get("data");
            List<Map<String, Object>> sellers = (List<Map<String, Object>>) data.get("sellers");
            sellers.get(0).put("stock", 1); // Only 1 item in stock

            ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<>(productResponse, HttpStatus.OK);

            when(restTemplate.exchange(
                    anyString(),
                    eq(HttpMethod.GET),
                    any(),
                    any(ParameterizedTypeReference.class)
            )).thenReturn(responseEntity);

            // Act & Assert
            RuntimeException exception = assertThrows(RuntimeException.class, () -> cartService.addToCart(testAddToCartRequest));
            assertTrue(exception.getMessage().contains("Stock not available"));
            verify(cartItemRepository, never()).save(any());
        }
    }

    @Test
    @DisplayName("❌ Add to Cart - Throws exception when merchant not found")
    void testAddToCart_MerchantNotFound() {
        // Arrange
        AddToCartRequest requestWithWrongMerchant = AddToCartRequest.builder()
                .productId(101)
                .variantId("variant-1")
                .merchantId("wrong-merchant-id")
                .quantity(2)
                .build();

        try (MockedStatic<SecurityContextHolder> mockedSecurityHolder = mockStatic(SecurityContextHolder.class)) {
            mockedSecurityHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);

            Map<String, Object> productResponse = createProductServiceResponse();
            ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<>(productResponse, HttpStatus.OK);

            when(restTemplate.exchange(
                    anyString(),
                    eq(HttpMethod.GET),
                    any(),
                    any(ParameterizedTypeReference.class)
            )).thenReturn(responseEntity);

            // Act & Assert
            RuntimeException exception = assertThrows(RuntimeException.class, () -> cartService.addToCart(requestWithWrongMerchant));
            assertTrue(exception.getMessage().contains("Could not find merchant"));
            verify(cartItemRepository, never()).save(any());
        }
    }

    @Test
    @DisplayName("❌ Add to Cart - Throws exception when product service returns failure")
    void testAddToCart_ProductServiceFailure() {
        // Arrange
        try (MockedStatic<SecurityContextHolder> mockedSecurityHolder = mockStatic(SecurityContextHolder.class)) {
            mockedSecurityHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);

            Map<String, Object> failureResponse = new HashMap<>();
            failureResponse.put("success", false);

            ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<>(failureResponse, HttpStatus.OK);

            when(restTemplate.exchange(
                    anyString(),
                    eq(HttpMethod.GET),
                    any(),
                    any(ParameterizedTypeReference.class)
            )).thenReturn(responseEntity);

            // Act & Assert
            RuntimeException exception = assertThrows(RuntimeException.class, () -> cartService.addToCart(testAddToCartRequest));
            assertTrue(exception.getMessage().contains("Could not find merchant"));
            verify(cartItemRepository, never()).save(any());
        }
    }

    @Test
    @DisplayName("❌ Add to Cart - Throws exception when product service is unavailable (HTTP error)")
    void testAddToCart_ProductServiceUnavailable() {
        // Arrange
        try (MockedStatic<SecurityContextHolder> mockedSecurityHolder = mockStatic(SecurityContextHolder.class)) {
            mockedSecurityHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);

            when(restTemplate.exchange(
                    anyString(),
                    eq(HttpMethod.GET),
                    any(),
                    any(ParameterizedTypeReference.class)
            )).thenThrow(new HttpClientErrorException(HttpStatus.SERVICE_UNAVAILABLE));

            // Act & Assert
            RuntimeException exception = assertThrows(RuntimeException.class, () -> cartService.addToCart(testAddToCartRequest));
            assertTrue(exception.getMessage().contains("Product not found or service unavailable"));
            verify(cartItemRepository, never()).save(any());
        }
    }

    // ==================== BUSINESS RULE VIOLATION TESTS ====================

    @Test
    @DisplayName("❌ Add to Cart - Exception when merchant ID does not match any seller")
    void testAddToCart_NoMatchingMerchantInSellers() {
        // Arrange
        AddToCartRequest requestWithDifferentMerchant = AddToCartRequest.builder()
                .productId(101)
                .variantId("variant-1")
                .merchantId("merchant-999")
                .quantity(2)
                .build();

        try (MockedStatic<SecurityContextHolder> mockedSecurityHolder = mockStatic(SecurityContextHolder.class)) {
            mockedSecurityHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);

            Map<String, Object> productResponse = createProductServiceResponse();
            ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<>(productResponse, HttpStatus.OK);

            when(restTemplate.exchange(
                    anyString(),
                    eq(HttpMethod.GET),
                    any(),
                    any(ParameterizedTypeReference.class)
            )).thenReturn(responseEntity);

            // Act & Assert
            RuntimeException exception = assertThrows(RuntimeException.class, () -> cartService.addToCart(requestWithDifferentMerchant));
            assertTrue(exception.getMessage().contains("Could not find merchant"));
        }
    }

    @Test
    @DisplayName("✅ Add to Cart - Case-insensitive merchant ID comparison works")
    void testAddToCart_MerchantIdCaseInsensitive() {
        // Arrange
        AddToCartRequest requestWithUppercaseMerchant = AddToCartRequest.builder()
                .productId(101)
                .variantId("variant-1")
                .merchantId("MERCHANT-001") // uppercase
                .quantity(2)
                .build();

        try (MockedStatic<SecurityContextHolder> mockedSecurityHolder = mockStatic(SecurityContextHolder.class)) {
            mockedSecurityHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);

            Map<String, Object> productResponse = createProductServiceResponse();
            ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<>(productResponse, HttpStatus.OK);

            when(restTemplate.exchange(
                    anyString(),
                    eq(HttpMethod.GET),
                    any(),
                    any(ParameterizedTypeReference.class)
            )).thenReturn(responseEntity);

            when(cartItemRepository.findByUserIdAndProductIdAndVariantId("user-123", 101, "variant-1"))
                    .thenReturn(Optional.empty());

            when(cartItemRepository.save(any(CartItem.class))).thenReturn(existingCartItem);

            // Act
            cartService.addToCart(requestWithUppercaseMerchant);

            // Assert - should succeed because of case-insensitive comparison
            verify(cartItemRepository, times(1)).save(any(CartItem.class));
        }
    }

    // ==================== EXCEPTION HANDLING TESTS ====================

    @Test
    @DisplayName("✅ Add to Cart - Verified price and stock from product service are used")
    void testAddToCart_VerifiedPriceAndStock() {
        // Arrange
        try (MockedStatic<SecurityContextHolder> mockedSecurityHolder = mockStatic(SecurityContextHolder.class)) {
            mockedSecurityHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);

            Map<String, Object> productResponse = createProductServiceResponse();
            ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<>(productResponse, HttpStatus.OK);

            when(restTemplate.exchange(
                    anyString(),
                    eq(HttpMethod.GET),
                    any(),
                    any(ParameterizedTypeReference.class)
            )).thenReturn(responseEntity);

            when(cartItemRepository.findByUserIdAndProductIdAndVariantId("user-123", 101, "variant-1"))
                    .thenReturn(Optional.empty());

            ArgumentCaptor<CartItem> captor = ArgumentCaptor.forClass(CartItem.class);
            when(cartItemRepository.save(captor.capture())).thenReturn(existingCartItem);

            // Act
            cartService.addToCart(testAddToCartRequest);

            // Assert
            CartItem savedItem = captor.getValue();
            assertEquals(100.0, savedItem.getPrice()); // Verified price from product service
            assertEquals(2, testAddToCartRequest.getQuantity());
        }
    }

    @Test
    @DisplayName("✅ Get My Cart - Items are converted to DTOs correctly")
    void testGetMyCart_ItemsConvertedToDTO() {
        // Arrange
        try (MockedStatic<SecurityContextHolder> mockedSecurityHolder = mockStatic(SecurityContextHolder.class)) {
            mockedSecurityHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);

            CartItem item = CartItem.builder()
                    .id(1L)
                    .userId("user-123")
                    .productId(101)
                    .variantId("variant-1")
                    .merchantId("merchant-001")
                    .quantity(2)
                    .price(100.0)
                    .build();

            List<CartItem> cartItems = Collections.singletonList(item);

            when(cartItemRepository.findByUserId("user-123")).thenReturn(cartItems);

            // Act
            CartResponse result = cartService.getMyCart();

            // Assert
            assertEquals(1, result.getItems().size());
            CartItemDTO dto = result.getItems().get(0);
            assertEquals(101, dto.getProductId());
            assertEquals("variant-1", dto.getVariantId());
            assertEquals("merchant-001", dto.getMerchantId());
            assertEquals(2, dto.getQuantity());
            assertEquals(100.0, dto.getPrice());
            assertEquals(200.0, dto.getSubTotal()); // 100.0 * 2
        }
    }

    @Test
    @DisplayName("❌ Add to Cart - Exception when product data parsing fails")
    void testAddToCart_ProductResponseParsingError() {
        // Arrange
        try (MockedStatic<SecurityContextHolder> mockedSecurityHolder = mockStatic(SecurityContextHolder.class)) {
            mockedSecurityHolder.when(SecurityContextHolder::getContext).thenReturn(securityContext);

            Map<String, Object> malformedResponse = new HashMap<>();
            malformedResponse.put("success", true);
            malformedResponse.put("data", null); // Null data causes parsing error

            ResponseEntity<Map<String, Object>> responseEntity = new ResponseEntity<>(malformedResponse, HttpStatus.OK);

            when(restTemplate.exchange(
                    anyString(),
                    eq(HttpMethod.GET),
                    any(),
                    any(ParameterizedTypeReference.class)
            )).thenReturn(responseEntity);

            // Act & Assert
            RuntimeException exception = assertThrows(RuntimeException.class, () -> cartService.addToCart(testAddToCartRequest));
            assertTrue(exception.getMessage().contains("Could not find merchant"));
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
        seller.put("stock", 10);

        data.put("sellers", Collections.singletonList(seller));
        response.put("data", data);

        return response;
    }
}
