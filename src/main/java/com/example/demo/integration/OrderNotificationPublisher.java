package com.example.demo.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.demo.entity.Order;
import com.example.demo.entity.OrderItem;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.client.RestTemplate;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OrderNotificationPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderNotificationPublisher.class);
    private final RestTemplate restTemplate;
    private static final String PRODUCT_URL = "https://product-service-jzzf.onrender.com/api/v1/products/";

    public void sendOrderConfirmation(Order order, String userEmail) {
        StringBuilder sb = new StringBuilder("\nConfirmation for Order #").append(order.getId()).append("\n");
        sb.append("Customer: ").append(userEmail).append("\n");
        sb.append("------------------------------------------\n");

        for (OrderItem item : order.getItems()) {
            String name = "Item #" + item.getProductId();
            try {
                String url = UriComponentsBuilder.fromHttpUrl(PRODUCT_URL + item.getProductId())
                        .queryParam("variantId", item.getVariantId())
                        .toUriString();
                // Logic to fetch name if needed
            } catch (Exception e) {
                log.warn("Could not fetch name for product {}: {}", item.getProductId(), e.getMessage());
            }
            sb.append(String.format("%-20s x%-3d $%.2f\n", name, item.getQuantity(), item.getPrice()));
        }

        sb.append("------------------------------------------\n");
        sb.append("Total: $").append(order.getTotalAmount());
        log.info(sb.toString());
    }
}