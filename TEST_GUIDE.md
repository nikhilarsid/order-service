# 🧪 Test Suite Guide - Order Service

## Quick Start

### Prerequisites

- Java 17 or higher
- Gradle 7.0+
- Spring Boot 3.2.2

### Build Project

```bash
./gradlew clean build
```

### Run All Tests

```bash
./gradlew test
```

### Run With Test Report

```bash
./gradlew test
# Open: build/reports/tests/test/index.html
```

---

## Test Files Overview

### 📦 Unit Tests (No Spring Context)

#### `OrderServiceTest.java` - 12 Tests

Service layer logic for order creation and retrieval.

```bash
./gradlew test --tests OrderServiceTest
```

**Coverage Areas:**

- ✅ Successful checkout with single/multiple items
- ✅ Order history retrieval
- ✅ Empty cart validation
- ✅ Insufficient stock handling
- ✅ Merchant ID mismatch detection
- ✅ External service error handling
- ✅ Total amount calculation
- ✅ Order status verification

#### `CartServiceTest.java` - 13 Tests

Service layer logic for cart management.

```bash
./gradlew test --tests CartServiceTest
```

**Coverage Areas:**

- ✅ Add new items to cart
- ✅ Update existing item quantities
- ✅ Retrieve cart with total calculation
- ✅ Stock validation
- ✅ Merchant validation
- ✅ External service error handling
- ✅ DTO conversion
- ✅ Case-insensitive merchant matching

---

### 🔄 Integration Tests (With Spring Context)

#### `OrderServiceIntegrationTest.java` - 8 Tests

End-to-end workflows combining multiple services.

```bash
./gradlew test --tests OrderServiceIntegrationTest
```

**Coverage Areas:**

- 🔄 Complete add-to-cart → checkout flow
- 🔄 Multi-item checkout with total calculation
- 🔄 Partial out-of-stock handling
- 🔄 Rollback on failure scenarios
- 🔄 Empty cart handling
- 🔄 Large quantity processing (100+ items)
- 🔄 Decimal price precision

---

## Test Execution Examples

### Run Single Test Method

```bash
./gradlew test --tests OrderServiceTest.testCheckout_Success
./gradlew test --tests CartServiceTest.testAddToCart_NewItem_Success
./gradlew test --tests OrderServiceIntegrationTest.testAddToCartThenCheckout_CompleteFlow
```

### Run Test Class with Filtering

```bash
./gradlew test --tests "*Service*Test"
./gradlew test --tests "*Integration*"
```

### Run with Detailed Output

```bash
./gradlew test -i
```

### Run with Coverage Report (Gradle)

```bash
./gradlew test jacocoTestReport
# Open: build/reports/jacoco/test/html/index.html
```

---

## Understanding Test Names

All tests follow a descriptive naming pattern:

```
test[MethodUnderTest]_[Scenario]_[ExpectedResult]
```

**Examples:**

- `testCheckout_Success` → Checkout method succeeds
- `testCheckout_EmptyCart` → Checkout with empty cart validation
- `testAddToCart_InsufficientStock` → Add to cart with insufficient stock
- `testGetMyCart_CorrectCalculation` → Cart calculates total correctly

**Display Names** use emojis for quick scanning:

- ✅ Happy path tests (success scenarios)
- ❌ Validation failure tests
- ⚠️ Business rule violation tests
- 🔧 Exception handling tests
- 🔄 Integration tests

---

## Test Structure

### Unit Test Template (No Spring)

```java
@ExtendWith(MockitoExtension.class)
class ServiceTest {
    @Mock
    private Repository repository;

    @InjectMocks
    private Service service;

    @Test
    void testMethod_Scenario_Expected() {
        // Arrange: Set up test data

        // Act: Execute method

        // Assert: Verify results
    }
}
```

### Integration Test Template (With Spring)

```java
@SpringBootTest
@AutoConfigureMockMvc
class ServiceIntegrationTest {
    @Autowired
    private Service service;

    @MockBean
    private ExternalService externalService;

    @Test
    void testCompleteWorkflow() {
        // Test multi-service interaction
    }
}
```

---

## Key Testing Patterns Used

### 1. Mocking SecurityContext

```java
try (MockedStatic<SecurityContextHolder> mockedSecurityHolder =
     mockStatic(SecurityContextHolder.class)) {
    mockedSecurityHolder.when(SecurityContextHolder::getContext)
        .thenReturn(securityContext);

    // Test method that uses SecurityContextHolder
}
```

### 2. Mocking RestTemplate

```java
ResponseEntity<Map<String, Object>> responseEntity =
    new ResponseEntity<>(productData, HttpStatus.OK);

when(restTemplate.exchange(
    anyString(),
    eq(HttpMethod.GET),
    any(),
    any(ParameterizedTypeReference.class)
)).thenReturn(responseEntity);
```

### 3. Repository Mocking

```java
when(orderRepository.findByUserIdOrderByCreatedAtDesc("user-123"))
    .thenReturn(orders);
```

### 4. ArgumentCaptor for Verification

```java
ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
verify(orderRepository).save(captor.capture());
Order savedOrder = captor.getValue();
assertEquals(OrderStatus.CONFIRMED, savedOrder.getStatus());
```

### 5. Exception Testing

```java
RuntimeException exception = assertThrows(RuntimeException.class,
    () -> orderService.checkout());
assertEquals("Cart is empty", exception.getMessage());
```

---

## Common Test Scenarios

### ✅ Happy Path: Successful Checkout

1. Cart has valid items
2. Product service returns available stock
3. All items added to order
4. Cart cleared after checkout
5. Order number returned

### ❌ Validation: Empty Cart

1. Cart has no items
2. Checkout called
3. RuntimeException thrown
4. Database not modified

### ⚠️ Business Rule: Insufficient Stock

1. Requested quantity > available stock
2. Checkout fails
3. Order not created
4. Cart remains unchanged

### 🔄 Integration: Complete Flow

1. Add item to cart (validation passed)
2. Item saved to database
3. Retrieve cart (correct total calculated)
4. Initiate checkout
5. Order created with items
6. Cart cleared

---

## Troubleshooting

### Tests Fail with "ClassNotFoundException"

**Solution:** Ensure Mockito dependency is added to build.gradle

```groovy
testImplementation 'org.mockito:mockito-core:5.2.0'
testImplementation 'org.mockito:mockito-junit-jupiter:5.2.0'
```

### SecurityContext Not Found

**Solution:** Use MockedStatic to mock SecurityContextHolder

```java
try (MockedStatic<SecurityContextHolder> mockedSecurityHolder =
     mockStatic(SecurityContextHolder.class)) {
    mockedSecurityHolder.when(SecurityContextHolder::getContext)
        .thenReturn(securityContext);
    // Run test
}
```

### RestTemplate Returns Null

**Solution:** Ensure ResponseEntity is properly mocked

```java
ResponseEntity<Map<String, Object>> responseEntity =
    new ResponseEntity<>(data, HttpStatus.OK);
when(restTemplate.exchange(...)).thenReturn(responseEntity);
```

### Test Fails with "Interaction not found"

**Solution:** Ensure verify() is called with correct arguments

```java
// Use ArgumentCaptor to capture actual arguments
ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
verify(repository).save(captor.capture());
// Now check the captured value
```

---

## Best Practices Followed

✅ **No Spring Context in Unit Tests** - Faster execution
✅ **Mocked Dependencies** - Isolated testing
✅ **Descriptive Names** - Easy to understand test purpose
✅ **Setup-Act-Assert** - Clear test structure
✅ **No Database Hits** - Repositories mocked
✅ **DTO Assertions** - Testing contracts, not implementations
✅ **Exception Verification** - Both type and message
✅ **Interaction Verification** - Ensure side effects happen

---

## Test Metrics

| Metric                   | Value      |
| ------------------------ | ---------- |
| **Total Tests**          | 33         |
| **Unit Tests**           | 25         |
| **Integration Tests**    | 8          |
| **Average Setup Time**   | < 100ms    |
| **Total Execution**      | ~2 seconds |
| **Code Coverage Target** | 80%+       |

---

## Continuous Integration

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
```

### GitLab CI Example

```yaml
test:
  image: openjdk:17-slim
  script:
    - ./gradlew test
  artifacts:
    reports:
      junit: build/test-results/test/TEST-*.xml
```

---

## Next Steps

1. ✅ Run tests: `./gradlew test`
2. ✅ Check coverage: `./gradlew jacocoTestReport`
3. ✅ Add to CI/CD pipeline
4. ✅ Extend test coverage as new features added
5. ✅ Update tests when requirements change

---

## Reference Documents

- [TESTING_DOCUMENTATION.md](./TESTING_DOCUMENTATION.md) - Comprehensive testing guide
- [Build.gradle](./build.gradle) - Project dependencies
- [Application Properties](./src/main/resources/application.properties) - Configuration

---

**Last Updated**: February 2026
**Spring Boot Version**: 3.2.2
**Java Version**: 17
**Test Framework**: JUnit 5 + Mockito 5.2.0
