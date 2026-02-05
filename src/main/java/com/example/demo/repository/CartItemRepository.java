package com.example.demo.repository;

import com.example.demo.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    List<CartItem> findByUserId(String userId);

    // This fixes the "cannot find symbol" error in CartService
    Optional<CartItem> findByUserIdAndProductIdAndVariantId(String userId, Integer productId, String variantId);

    // Securely find an item by its ID and the owner's ID for deletion
    Optional<CartItem> findByIdAndUserId(Long id, String userId);

    @Modifying
    @Transactional
    void deleteByUserId(String userId);
}