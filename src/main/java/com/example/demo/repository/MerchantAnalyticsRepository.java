package com.example.demo.repository;

import com.example.demo.entity.MerchantAnalytics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface MerchantAnalyticsRepository extends JpaRepository<MerchantAnalytics, Long> {

    Optional<MerchantAnalytics> findByMerchantIdAndProductIdAndVariantId(String mId, Integer pId, String vId);

    @Query("SELECT SUM(m.numberOfOrdersSold) FROM MerchantAnalytics m WHERE m.merchantId = ?1")
    Integer getTotalOrdersByMerchant(String merchantId);

    @Query("SELECT SUM(m.amountGenerated) FROM MerchantAnalytics m WHERE m.merchantId = ?1")
    Double getTotalRevenueByMerchant(String merchantId);
}