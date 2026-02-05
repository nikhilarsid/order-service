package com.example.demo.entity;

import jakarta.persistence.*; // For JPA annotations
import lombok.*;            // For Lombok annotations

@Entity
@Table(name = "merchant_analytics")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantAnalytics {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id")
    private String merchantId;

    @Column(name = "product_id")
    private Integer productId;

    @Column(name = "variant_id")
    private String variantId;

    @Column(name = "number_of_orders_sold")
    private Integer numberOfOrdersSold;

    @Column(name = "amount_generated")
    private Double amountGenerated;
}