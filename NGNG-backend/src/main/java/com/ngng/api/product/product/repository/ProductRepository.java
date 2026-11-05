package com.ngng.api.product.product.repository;

import com.ngng.api.product.product.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.sql.Timestamp;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // Scalar current read: do not lock eager user/category/status associations.
    @Query(value = "SELECT product_id AS productId, user_id AS sellerId FROM product WHERE product_id = :productId FOR UPDATE", nativeQuery = true)
    Optional<TransactionProduct> lockTransactionProduct(@Param("productId") Long productId);

    @Modifying
    @Query(value = "UPDATE product SET for_sale = false, updated_at = :updatedAt WHERE product_id = :productId AND (for_sale IS NULL OR for_sale <> false)", nativeQuery = true)
    int markTransactionSold(@Param("productId") Long productId, @Param("updatedAt") Timestamp updatedAt);

    interface TransactionProduct {
        Long getProductId();
        Long getSellerId();
    }

    @Query("SELECT p FROM Product p WHERE p.user.id = :userId")
    List<Product> readAllSellProductBySellerId(@Param("userId") Long userId);

    Page<Product> findAllByVisible(Boolean visible, Pageable pageable);
}
