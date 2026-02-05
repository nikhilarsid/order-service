package com.example.demo.integration;

import com.example.demo.entity.Order;
import com.example.demo.entity.OrderItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderNotificationPublisher {

    private final RestTemplate restTemplate;
    private final String PRODUCT_URL = "https://product-service-jzzf.onrender.com/api/v1/products/";

    public void sendOrderConfirmation(Order order, String userEmail) {
        StringBuilder sb = new StringBuilder("\nConfirmation for Order #").append(order.getId()).append("\n");

        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "Mozilla/5.0");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        for (OrderItem item : order.getItems()) {
            String name = "Item #" + item.getProductId();
            try {
                String url = UriComponentsBuilder.fromHttpUrl(PRODUCT_URL + item.getProductId())
                        .queryParam("variantId", item.getVariantId())
                        .toUriString();

                ResponseEntity<Map<String, Object>> res = restTemplate.exchange(
                        url, HttpMethod.GET, entity, new ParameterizedTypeReference<>() {}
                );

                Map<String, Object> body = res.getBody();
                if (body != null && (Boolean) body.get("success")) {
                    List<Map<String, Object>> data = (List<Map<String, Object>>) body.get("data");
                    if (data != null && !data.isEmpty()) name = (String) data.get(0).get("name");
                }
            } catch (Exception e) {
                log.warn("Could not fetch name for product {}: {}", item.getProductId(), e.getMessage());
            }
            sb.append(String.format("%-20s x%-3d $%.2f\n", name, item.getQuantity(), item.getPrice()));
        }
        sb.append("Total: $").append(order.getTotalAmount());
        log.info(sb.toString());
    }
}