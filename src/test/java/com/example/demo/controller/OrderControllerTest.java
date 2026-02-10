package com.example.demo.controller;

import com.example.demo.dto.response.OrderItemDto;
import com.example.demo.dto.response.OrderResponse;
import com.example.demo.entity.MerchantAnalytics;
import com.example.demo.repository.MerchantAnalyticsRepository;
import com.example.demo.service.OrderService;
import com.example.demo.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@AutoConfigureMockMvc(addFilters = false)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @MockBean
    private MerchantAnalyticsRepository analyticsRepository;

    @MockBean
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    private OrderResponse orderResponse;
    private OrderItemDto orderItemDto;
    private MerchantAnalytics merchantAnalytics;

    @BeforeEach
    void setUp() {
        // Setup OrderResponse
        orderResponse = OrderResponse.builder()
                .orderNumber("ORD-12345")
                .totalAmount(100.0)
                .status("CONFIRMED")
                .build();

        // Setup OrderItemDto
        orderItemDto = OrderItemDto.builder()
                .productId(101)
                .merchantName("Tech Store")
                .quantity(2)
                .price(50.0)
                .build();

        // ✅ FIX: Use correct field names from your Entity
        merchantAnalytics = MerchantAnalytics.builder()
                .id(1L)
                .merchantId("merchant-001")
                .productId(101)
                .variantId("var-1")
                .numberOfOrdersSold(10)      // Was 'totalOrders'
                .amountGenerated(1000.0)     // Was 'totalRevenue'
                .build();
    }

    // ==========================================
    // 1. Place Order Test (POST)
    // ==========================================
    @Test
    @DisplayName("✅ Place Order - Success")
    void placeOrder_Success() throws Exception {
        when(orderService.checkout()).thenReturn("ORD-12345");

        mockMvc.perform(post("/api/orders/add"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Order placed successfully"))
                .andExpect(jsonPath("$.data").value("ORD-12345"));

        verify(orderService, times(1)).checkout();
    }

    // ==========================================
    // 2. View Order History Test (GET)
    // ==========================================
    @Test
    @DisplayName("✅ Get Order History - Success")
    void getOrderHistory_Success() throws Exception {
        List<OrderResponse> history = Collections.singletonList(orderResponse);
        when(orderService.getOrderHistory()).thenReturn(history);

        mockMvc.perform(get("/api/orders/view"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Order history fetched"))
                .andExpect(jsonPath("$.data[0].orderNumber").value("ORD-12345"));

        verify(orderService, times(1)).getOrderHistory();
    }

    // ==========================================
    // 3. View Item Details Test (GET)
    // ==========================================
    @Test
    @DisplayName("✅ View Item Detail - Success")
    void viewItem_Success() throws Exception {
        Long itemId = 1L;
        when(orderService.getOrderItemDetail(itemId)).thenReturn(orderItemDto);

        mockMvc.perform(get("/api/orders/viewItem/{itemId}", itemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.merchantName").value("Tech Store"))
                .andExpect(jsonPath("$.data.productId").value(101));

        verify(orderService, times(1)).getOrderItemDetail(itemId);
    }

    // ==========================================
    // 4. Merchant Analytics Tests (GET)
    // ==========================================

    @Test
    @DisplayName("✅ Get Total Orders by Merchant - Success")
    void getTotalOrders_Success() throws Exception {
        String merchantId = "merchant-001";
        when(analyticsRepository.getTotalOrdersByMerchant(merchantId)).thenReturn(50);

        mockMvc.perform(get("/api/orders/merchant/{merchantId}/total-orders", merchantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(50));

        verify(analyticsRepository, times(1)).getTotalOrdersByMerchant(merchantId);
    }

    @Test
    @DisplayName("✅ Get Total Revenue by Merchant - Success")
    void getTotalRevenue_Success() throws Exception {
        String merchantId = "merchant-001";
        when(analyticsRepository.getTotalRevenueByMerchant(merchantId)).thenReturn(5000.0);

        mockMvc.perform(get("/api/orders/merchant/{merchantId}/total-revenue", merchantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(5000.0));

        verify(analyticsRepository, times(1)).getTotalRevenueByMerchant(merchantId);
    }

    @Test
    @DisplayName("✅ Get Specific Stats - Success (Found)")
    void getSpecificStats_Found() throws Exception {
        String merchantId = "merchant-001";
        Integer productId = 101;
        String variantId = "var-1";

        when(analyticsRepository.findByMerchantIdAndProductIdAndVariantId(merchantId, productId, variantId))
                .thenReturn(Optional.of(merchantAnalytics));

        mockMvc.perform(get("/api/orders/merchant/{merchantId}/stats", merchantId)
                        .param("productId", String.valueOf(productId))
                        .param("variantId", variantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.merchantId").value("merchant-001"))
                // ✅ FIX: Updated JSON path to match entity field name
                .andExpect(jsonPath("$.data.numberOfOrdersSold").value(10));

        verify(analyticsRepository, times(1)).findByMerchantIdAndProductIdAndVariantId(merchantId, productId, variantId);
    }

    @Test
    @DisplayName("✅ Get Specific Stats - Success (Not Found / Empty)")
    void getSpecificStats_NotFound() throws Exception {
        String merchantId = "merchant-001";
        Integer productId = 999;
        String variantId = "unknown";

        when(analyticsRepository.findByMerchantIdAndProductIdAndVariantId(merchantId, productId, variantId))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/orders/merchant/{merchantId}/stats", merchantId)
                        .param("productId", String.valueOf(productId))
                        .param("variantId", variantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                // ✅ FIX: Updated JSON path to match entity field name
                .andExpect(jsonPath("$.data.numberOfOrdersSold").doesNotExist());

        verify(analyticsRepository, times(1)).findByMerchantIdAndProductIdAndVariantId(merchantId, productId, variantId);
    }
}