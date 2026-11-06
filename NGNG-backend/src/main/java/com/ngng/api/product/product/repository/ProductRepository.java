package com.ngng.api.product.product.repository;

import com.ngng.api.product.product.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // Scalar current read: do not lock eager user/category/status associations.
    @Query(value = "SELECT product_id AS productId, user_id AS sellerId FROM product WHERE product_id = :productId FOR UPDATE", nativeQuery = true)
    Optional<TransactionProduct> lockTransactionProduct(@Param("productId") Long productId);

    interface TransactionProduct {
        Long getProductId();
        Long getSellerId();
    }

    @Query("SELECT p FROM Product p WHERE p.user.id = :userId")
    List<Product> readAllSellProductBySellerId(@Param("userId") Long userId);

    Page<Product> findAllByVisible(Boolean visible, Pageable pageable);
}
