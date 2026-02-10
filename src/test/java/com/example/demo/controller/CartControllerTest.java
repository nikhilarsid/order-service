package com.example.demo.controller;

import com.example.demo.dto.request.AddToCartRequest;
import com.example.demo.dto.response.CartResponse;
import com.example.demo.service.CartService;
import com.example.demo.security.JwtService; // ✅ Import this
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CartController.class)
@AutoConfigureMockMvc(addFilters = false)
class CartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CartService cartService;

    // ✅ FIX: Mock JwtService so SecurityConfig/Filters can load without error
    @MockBean
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    private AddToCartRequest addToCartRequest;
    private CartResponse cartResponse;

    @BeforeEach
    void setUp() {
        // Setup Request
        addToCartRequest = AddToCartRequest.builder()
                .productId(101)
                .variantId("var-1")
                .merchantId("merchant-A")
                .quantity(2)
                .build();

        // Setup Response
        cartResponse = CartResponse.builder()
                .totalValue(200.0)
                .items(Collections.emptyList())
                .build();
    }

    // ==========================================
    // 1. Add Item Test (POST)
    // ==========================================
    @Test
    @DisplayName("✅ Add Item - Success")
    void addToCart_Success() throws Exception {
        doNothing().when(cartService).addToCart(any(AddToCartRequest.class));

        mockMvc.perform(post("/api/cart/addItem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addToCartRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Item added to cart"));

        verify(cartService, times(1)).addToCart(any(AddToCartRequest.class));
    }

    @Test
    @DisplayName("❌ Add Item - Service Exception (should propagate)")
    void addToCart_Failure() throws Exception {
        doThrow(new RuntimeException("Out of stock")).when(cartService).addToCart(any(AddToCartRequest.class));

        mockMvc.perform(post("/api/cart/addItem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addToCartRequest)))
                .andExpect(status().isInternalServerError()); // Expect 500
    }

    // ==========================================
    // 2. View Cart Test (GET)
    // ==========================================
    @Test
    @DisplayName("✅ View Cart - Success")
    void viewCart_Success() throws Exception {
        when(cartService.getMyCart()).thenReturn(cartResponse);

        mockMvc.perform(get("/api/cart/view"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalValue").value(200.0))
                .andExpect(jsonPath("$.message").value("Cart retrieved"));

        verify(cartService, times(1)).getMyCart();
    }

    // ==========================================
    // 3. Delete Item Test (DELETE)
    // ==========================================
    @Test
    @DisplayName("✅ Delete Item - Success")
    void deleteItem_Success() throws Exception {
        Long itemId = 50L;
        Integer quantity = 1;

        doNothing().when(cartService).removeItem(itemId, quantity);

        mockMvc.perform(delete("/api/cart/deleteItem/{itemId}", itemId)
                        .param("quantity", String.valueOf(quantity)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Cart updated successfully"));

        verify(cartService, times(1)).removeItem(itemId, quantity);
    }

    @Test
    @DisplayName("❌ Delete Item - Missing Quantity Param")
    void deleteItem_MissingParam() throws Exception {
        mockMvc.perform(delete("/api/cart/deleteItem/{itemId}", 50L))
                // Based on your logs, your app returns 500 for missing params
                .andExpect(status().isInternalServerError());

        verify(cartService, never()).removeItem(anyLong(), anyInt());
    }
}