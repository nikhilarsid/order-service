package com.example.demo.repository;

import com.example.demo.entity.MerchantAnalytics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface MerchantAnalyticsRepository extends JpaRepository<MerchantAnalytics, Long> {

    Optional<MerchantAnalytics> findByMerchantIdAndProductIdAndVariantId(String merchantId, Integer productId, String variantId);

    @Query("SELECT SUM(m.numberOfOrdersSold) FROM MerchantAnalytics m WHERE m.merchantId = :merchantId")
    Integer getTotalOrdersByMerchant(String merchantId);

    @Query("SELECT SUM(m.amountGenerated) FROM MerchantAnalytics m WHERE m.merchantId = :merchantId")
    Double getTotalRevenueByMerchant(String merchantId);
}