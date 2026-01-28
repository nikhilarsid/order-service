package com.example.demo.integration;

import com.example.demo.dto.response.ApiResponse;
import com.example.demo.entity.Order;
import com.example.demo.entity.OrderItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderNotificationPublisher {

    private final RestTemplate restTemplate;
    private final String INVENTORY_URL = "http://localhost:8082/api/v1/inventory/";
    private final String PRODUCT_URL = "http://localhost:8095/api/v1/products/";

    public void sendOrderConfirmation(Order order, String userEmail) {
        StringBuilder emailBody = new StringBuilder();
        emailBody.append("\n========================================\n");
        emailBody.append("        ORDER CONFIRMATION EMAIL        \n");
        emailBody.append("========================================\n");
        emailBody.append("To: ").append(userEmail).append("\n");
        emailBody.append("Subject: Your Order #").append(order.getId()).append(" is Confirmed!\n\n");
        emailBody.append(String.format("%-30s %-10s %-10s\n", "Item", "Qty", "Price"));
        emailBody.append("--------------------------------------------------\n");

        for (OrderItem item : order.getItems()) {
            String displayName;
            try {
                ResponseEntity<ApiResponse<Map<String, Object>>> invResponse = restTemplate.exchange(
                        INVENTORY_URL + item.getMerchantProductId(),
                        HttpMethod.GET, null, new ParameterizedTypeReference<>() {}
                );
                String productId = (String) invResponse.getBody().getData().get("productId");

                ResponseEntity<ApiResponse<Map<String, Object>>> prodResponse = restTemplate.exchange(
                        PRODUCT_URL + productId,
                        HttpMethod.GET, null, new ParameterizedTypeReference<>() {}
                );
                displayName = (String) prodResponse.getBody().getData().get("name");
            } catch (Exception e) {
                displayName = "Item #" + item.getMerchantProductId();
            }

            emailBody.append(String.format("%-30s %-10d $%-10.2f\n", displayName, item.getQuantity(), item.getPrice()));
        }

        emailBody.append("--------------------------------------------------\n");
        emailBody.append("Total Amount: $").append(order.getTotalAmount()).append("\n");
        emailBody.append("========================================\n");
        log.info(emailBody.toString());
    }
}