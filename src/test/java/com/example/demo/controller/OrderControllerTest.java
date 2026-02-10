package com.example.demo.controller;

import com.example.demo.repository.MerchantAnalyticsRepository;
import com.example.demo.service.OrderService;
import com.example.demo.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@AutoConfigureMockMvc(addFilters = false)
class OrderControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private OrderService orderService;
    @MockBean private MerchantAnalyticsRepository analyticsRepository;
    @MockBean private JwtService jwtService; // Required for context startup

    @Test
    @WithMockUser
    void placeOrder_ShouldReturnSuccess() throws Exception {
        when(orderService.checkout()).thenReturn("ORD-123");
        mockMvc.perform(post("/api/orders/add")).andExpect(status().isOk());
    }
}