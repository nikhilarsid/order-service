# Order Service - Comprehensive Testing Documentation

## 📋 Overview

This document provides a complete guide to the unit and integration tests for the Order Service microservice. All tests follow enterprise-grade standards with strict adherence to Spring Boot 3.2.2, Java 17, and testing best practices.

## 🏗️ Architecture

### Testing Stack

- **Framework**: JUnit 5
- **Mocking**: Mockito 5.2.0
- **Coverage**: Unit Tests + Integration Tests
- **Spring Version**: Spring Boot 3.2.2
- **Database**: MySQL (TiDB)

### Test Classification

- **Unit Tests**: Service layer tests with mocked dependencies
- **Integration Tests**: Multi-service workflow tests
- **No Spring Context**: Unit tests use `@ExtendWith(MockitoExtension.class)`
- **Spring Context Tests**: Integration tests use `@SpringBootTest`

---

## 📁 Test Files Structure

```
src/test/java/com/example/demo/service/
├── OrderServiceTest.java              # Unit tests for OrderService
├── CartServiceTest.java               # Unit tests for CartService
└── OrderServiceIntegrationTest.java    # Integration tests
```

---

## 🧪 OrderService Unit Tests (`OrderServiceTest.java`)

### Test Coverage Statistics

- **Total Test Cases**: 12
- **Happy Path**: 4 tests
- **Validation Failures**: 3 tests
- **Business Rule Violations**: 2 tests
- **Exception Handling**: 3 tests

### Test Cases

#### ✅ Happy Path Tests

**1. `testCheckout_Success`**

```
Description: Successfully create order from cart items
Scenario: Valid cart with items → Product service returns valid data
Expected: Order created with correct order number and items
```

**2. `testGetOrderHistory_Success`**

```
Description: Return mapped DTO list of orders
Scenario: User has existing orders
Expected: List of OrderResponse DTOs returned
```

**3. `testGetOrderItemDetail_Success`**

```
Description: Return OrderItemDto with correct data
Scenario: Valid order item ID provided
Expected: OrderItemDto with all fields populated
```

**4. `testCheckout_CorrectTotalCalculation`**

```
Description: Correctly calculate total from multiple items
Scenario: Multiple items with different prices/quantities
Expected: Total = sum of (price × quantity) for all items
```

#### ❌ Validation Failure Tests

**5. `testCheckout_EmptyCart`**

```
Description: Throws exception when cart is empty
Scenario: No cart items available
Expected: RuntimeException("Cart is empty")
Assertions: orderRepository.save never called
```

**6. `testCheckout_ProductUnavailable`**

```
Description: Throws exception when product data unavailable
Scenario: Product service returns success=false
Expected: RuntimeException with "Item out of stock or unavailable"
```

**7. `testCheckout_InsufficientStock`**

```
Description: Throws exception when insufficient stock
Scenario: Requested quantity > available stock
Expected: RuntimeException with "Insufficient stock"
```

#### ⚠️ Business Rule Violation Tests

**8. `testCheckout_MerchantMismatch`**

```
Description: Throws exception when merchant ID doesn't match
Scenario: Cart merchant ID ≠ Product service merchant ID
Expected: RuntimeException with "Item out of stock or unavailable"
```

**9. `testCheckout_ExternalServiceError`**

```
Description: Handles external service error gracefully
Scenario: RestTemplate throws HttpClientErrorException
Expected: RuntimeException, cart not cleared
```

#### 🔧 Exception Handling Tests

**10. `testGetOrderItemDetail_NotFound`**

```
Description: Throws exception when item not found
Scenario: OrderItem doesn't exist
Expected: RuntimeException("Order Item not found")
```

**11. `testGetOrderHistory_NoOrders`**

```
Description: Return empty list when no orders exist
Scenario: User has no orders
Expected: Empty list returned successfully
```

**12. `testCheckout_OrderStatusConfirmed`**

```
Description: Order status should be CONFIRMED after creation
Scenario: New order created
Expected: order.getStatus() == OrderStatus.CONFIRMED
```

---

## 🛒 CartService Unit Tests (`CartServiceTest.java`)

### Test Coverage Statistics

- **Total Test Cases**: 13
- **Happy Path**: 3 tests
- **Validation Failures**: 4 tests
- **Business Rule Violations**: 2 tests
- **Exception Handling**: 4 tests

### Test Cases

#### ✅ Happy Path Tests

**1. `testAddToCart_NewItem_Success`**

```
Description: New item created successfully
Scenario: Product not in cart, valid data from product service
Expected: New CartItem saved with verified price
Verifications: cartItemRepository.save called once
```

**2. `testAddToCart_ExistingItem_QuantityIncreased`**

```
Description: Existing item quantity increased
Scenario: Product already in cart
Expected: Quantity incremented by request quantity
Verifications: CartItem.quantity = existing + request.quantity
```

**3. `testGetMyCart_Success`**

```
Description: Return cart with all items and calculated total
Scenario: User has multiple items
Expected: CartResponse with correct total value
Calculation: Total = Σ(price × quantity)
```

#### ❌ Validation Failure Tests

**4. `testAddToCart_InsufficientStock`**

```
Description: Throws exception when quantity exceeds stock
Scenario: request.quantity > available.stock
Expected: RuntimeException("Stock not available")
Assertions: cartItemRepository.save never called
```

**5. `testAddToCart_MerchantNotFound`**

```
Description: Throws exception when merchant not found
Scenario: Merchant ID not in sellers list
Expected: RuntimeException("Could not find merchant")
```

**6. `testAddToCart_ProductServiceFailure`**

```
Description: Throws exception when service returns failure
Scenario: Product service success=false
Expected: RuntimeException("Could not find merchant")
```

**7. `testAddToCart_ProductServiceUnavailable`**

```
Description: Throws exception when service unavailable
Scenario: HTTP error from product service
Expected: RuntimeException("Product not found or service unavailable")
```

#### ⚠️ Business Rule Violation Tests

**8. `testAddToCart_NoMatchingMerchantInSellers`**

```
Description: Exception when merchant doesn't match any seller
Scenario: merchantId not in any seller record
Expected: RuntimeException("Could not find merchant")
```

**9. `testAddToCart_MerchantIdCaseInsensitive`**

```
Description: Case-insensitive merchant ID comparison works
Scenario: Merchant ID has different casing
Expected: Match found, item added successfully
```

#### 🔧 Exception Handling Tests

**10. `testAddToCart_VerifiedPriceAndStock`**

```
Description: Verified price and stock from product service used
Scenario: Valid product data received
Expected: CartItem saved with service-provided values
```

**11. `testGetMyCart_EmptyCart`**

```
Description: Return empty cart when no items
Scenario: No cart items for user
Expected: CartResponse with empty items list, 0.0 total
```

**12. `testGetMyCart_ItemsConvertedToDTO`**

```
Description: Items converted to DTOs correctly
Scenario: Get cart with items
Expected: CartItemDTO fields match CartItem fields
```

**13. `testAddToCart_ProductResponseParsingError`**

```
Description: Exception when product data parsing fails
Scenario: Malformed product service response
Expected: RuntimeException("Could not find merchant")
```

---

## 🔄 Integration Tests (`OrderServiceIntegrationTest.java`)

### Test Coverage Statistics

- **Total Test Cases**: 8
- **End-to-End Flows**: 2 tests
- **Recovery Scenarios**: 2 tests
- **Edge Cases**: 4 tests

### Test Cases

#### 🔄 Complete Workflows

**1. `testAddToCartThenCheckout_CompleteFlow`**

```
Description: Add item to cart, then checkout successfully
Scenario: Complete purchase flow
Steps:
  1. User adds item to cart
  2. Cart item saved to database
  3. User initiates checkout
  4. Order created with all items
  5. Cart cleared after successful checkout
Expected: OrderNumber returned, cart cleared
```

**2. `testMultipleItemsCheckout_CorrectTotalAndItems`**

```
Description: Multiple items in cart processed correctly
Scenario: Checkout with 2 different items
Expected:
  - 2 OrderItems created
  - Total correctly calculated
  - Cart cleared
```

#### 🔄 Failure Handling

**3. `testCheckout_PartialOutOfStock_EntireCheckoutFails`**

```
Description: Entire checkout fails if any item is out of stock
Scenario: Item 1 available, Item 2 out of stock
Expected: Checkout fails, cart unchanged
```

**4. `testCheckout_RollbackOnFailure_CartUnchanged`**

```
Description: Cart remains unchanged if checkout fails mid-process
Scenario: External service error during checkout
Expected: Cart not deleted, rollback successful
```

#### 🔄 Edge Cases

**5. `testGetCart_CalculatesTotalCorrectly`**

```
Description: Cart calculation with real entities
Scenario: Multiple items with various quantities/prices
Calculation: (3 × 50) + (2 × 75) = 300
Expected: Total matches calculation
```

**6. `testGetCart_EmptyCart_ReturnsValidResponse`**

```
Description: Handle empty cart gracefully
Scenario: User has no items in cart
Expected: Valid CartResponse with empty items, 0.0 total
```

**7. `testCheckout_LargeQuantities`**

```
Description: Large quantity cart checkout (100 items)
Scenario: High volume item checkout
Calculation: 100 × 10 = 1000
Expected: Order total correct, checkout succeeds
```

**8. `testCheckout_DecimalPriceCalculations`**

```
Description: Decimal price calculations
Scenario: Prices like 19.99
Calculation: 3 × 19.99 = 59.97
Expected: Precision maintained, no floating point errors
```

---

## ✅ Assertion Strategies

### DTO Assertions

```java
// ✅ Correct: Assert DTO fields
assertEquals(101, result.getProductId());
assertEquals("TechStore", result.getMerchantName());

// ❌ Wrong: Asserting entities
assertEquals(orderEntity, result);
```

### Repository Interaction Assertions

```java
// ✅ Correct: Verify save operations
verify(orderRepository, times(2)).save(any(Order.class));

// ✅ Correct: Verify deletion
verify(cartItemRepository, times(1)).deleteAll(cartItems);

// ❌ Wrong: No verification
```

### Exception Assertions

```java
// ✅ Correct: Verify exception type and message
RuntimeException exception = assertThrows(RuntimeException.class, () -> orderService.checkout());
assertEquals("Cart is empty", exception.getMessage());

// ✅ Correct: Verify exception cause
assertTrue(exception.getMessage().contains("Insufficient stock"));
```

### Mock Verification

```java
// ✅ Correct: Verify side effects didn't happen
verify(cartItemRepository, never()).deleteAll(any());

// ✅ Correct: Capture and verify arguments
ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
verify(orderRepository).save(captor.capture());
Order savedOrder = captor.getValue();
assertEquals(OrderStatus.CONFIRMED, savedOrder.getStatus());
```

---

## 🔐 Security Context Mocking

All tests mock the SecurityContextHolder to extract the authenticated user:

```java
@BeforeEach
void setUp() {
    when(securityContext.getAuthentication()).thenReturn(authentication);
    when(authentication.getPrincipal()).thenReturn(testUser);
}

// Usage
try (MockedStatic<SecurityContextHolder> mockedSecurityHolder =
     mockStatic(SecurityContextHolder.class)) {
    mockedSecurityHolder.when(SecurityContextHolder::getContext)
        .thenReturn(securityContext);

    // Run service method
}
```

---

## 📊 Mock Setup Patterns

### RestTemplate Mocking

```java
// Mock GET request for product details
ResponseEntity<Map<String, Object>> responseEntity =
    new ResponseEntity<>(productResponse, HttpStatus.OK);

when(restTemplate.exchange(
    anyString(),
    eq(HttpMethod.GET),
    isNull(),
    any(ParameterizedTypeReference.class)
)).thenReturn(responseEntity);
```

### Repository Mocking

```java
// Mock repository save
when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

// Mock repository findById
when(orderItemRepository.findById(1L)).thenReturn(Optional.of(orderItem));

// Mock repository findAll
when(orderRepository.findByUserIdOrderByCreatedAtDesc("user-123"))
    .thenReturn(orders);
```

---

## 🚀 Running Tests

### Run All Tests

```bash
./gradlew test
```

### Run Specific Test Class

```bash
./gradlew test --tests OrderServiceTest
./gradlew test --tests CartServiceTest
./gradlew test --tests OrderServiceIntegrationTest
```

### Run Specific Test Method

```bash
./gradlew test --tests OrderServiceTest.testCheckout_Success
```

### Generate Test Report

```bash
./gradlew test --tests "*Test"
# Report generated: build/reports/tests/test/index.html
```

---

## 📈 Code Coverage Requirements

### Target Coverage

- **Unit Tests**: 85%+ code coverage
- **Integration Tests**: 70%+ critical path coverage
- **Overall**: 80%+ service layer coverage

### Coverage Exclusions

- Entity getters/setters
- Lombok-generated code
- Configuration classes
- Spring auto-configuration

---

## 🎯 Testing Best Practices Applied

### ✅ What We Do

1. **No Spring Context in Unit Tests** - Use `@ExtendWith(MockitoExtension.class)`
2. **Mock All External Dependencies** - Repositories, RestTemplate, etc.
3. **Use DTOs Only** - Never assert entities
4. **Descriptive Test Names** - Follow pattern: `test[Method]_[Scenario]_[Expected]`
5. **Single Responsibility** - One assertion per test (or related assertions)
6. **Setup-Act-Assert Pattern** - Clear test structure
7. **Meaningful Assertions** - Verify business logic, not implementation
8. **Exception Testing** - Verify both type and message

### ❌ What We Don't Do

- Hit real databases
- Use `@SpringBootTest` for unit tests
- Make real HTTP calls
- Test getters/setters
- Have flaky tests
- Skip negative test cases
- Use hard-coded magic numbers

---

## 🔍 Example Test Walkthrough

```java
@Test
@DisplayName("✅ Checkout - Successfully create order from cart items")
void testCheckout_Success() {
    // ARRANGE: Set up test data and mocks
    List<CartItem> cartItems = Collections.singletonList(testCartItem);

    try (MockedStatic<SecurityContextHolder> mockedSecurityHolder =
         mockStatic(SecurityContextHolder.class)) {
        mockedSecurityHolder.when(SecurityContextHolder::getContext)
            .thenReturn(securityContext);

        when(cartItemRepository.findByUserId("user-123"))
            .thenReturn(cartItems);
        when(orderRepository.save(any(Order.class)))
            .thenReturn(testOrder);

        Map<String, Object> responseBody = createProductServiceResponse();
        ResponseEntity<Map<String, Object>> responseEntity =
            new ResponseEntity<>(responseBody, HttpStatus.OK);

        when(restTemplate.exchange(
            anyString(),
            eq(HttpMethod.GET),
            isNull(),
            any(ParameterizedTypeReference.class)
        )).thenReturn(responseEntity);

        when(orderItemRepository.save(any(OrderItem.class)))
            .thenReturn(testOrderItem);

        // ACT: Execute the method under test
        String orderNumber = orderService.checkout();

        // ASSERT: Verify the results
        assertNotNull(orderNumber);
        assertEquals("ORD-123-UUID", orderNumber);

        // VERIFY: Verify interactions
        verify(orderRepository, times(2)).save(any(Order.class));
        verify(orderItemRepository, times(1)).save(any(OrderItem.class));
        verify(cartItemRepository, times(1)).deleteAll(cartItems);
    }
}
```

---

## 📝 Custom Exception Classes

### BusinessRuleViolationException

Used when business rules are violated.

```java
throw new BusinessRuleViolationException(
    "Insufficient stock for product: " + productName,
    "INSUFFICIENT_STOCK",
    "Requested: 5, Available: 2"
);
```

### ResourceNotFoundException

Used when a resource doesn't exist.

```java
throw new ResourceNotFoundException(
    "Order Item",
    "id",
    itemId
);
```

### ValidationException

Used for input validation failures.

```java
throw new ValidationException(
    "quantity",
    "0",
    "Quantity must be positive"
);
```

### ExternalServiceException

Used for external service failures.

```java
throw new ExternalServiceException(
    "Product service unavailable",
    "ProductService",
    "https://product-service.com/api/v1/products/",
    503
);
```

---

## 🔄 CI/CD Integration

### GitHub Actions Example

```yaml
name: Tests
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - name: Set up JDK 17
        uses: actions/setup-java@v2
        with:
          java-version: "17"
      - name: Run tests
        run: ./gradlew test
      - name: Upload coverage
        uses: codecov/codecov-action@v2
```

---

## 📚 References

- [JUnit 5 Documentation](https://junit.org/junit5/docs/current/user-guide/)
- [Mockito Documentation](https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html)
- [Spring Boot Testing Guide](https://spring.io/guides/gs/testing-web/)
- [Testing Best Practices](https://spring.io/guides/tutorials/spring-security-and-angular-js/)

---

## 📞 Support & Questions

For questions or issues with tests:

1. Check test documentation above
2. Review similar test cases
3. Consult Spring Boot testing guides
4. Contact development team

---

**Document Version**: 1.0
**Last Updated**: February 2026
**Spring Boot Version**: 3.2.2
**Java Version**: 17
