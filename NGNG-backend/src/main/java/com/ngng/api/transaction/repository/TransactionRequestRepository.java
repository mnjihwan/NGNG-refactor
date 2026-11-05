package com.ngng.api.transaction.repository;

import com.ngng.api.transaction.entity.TransactionRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Isolation;

import java.util.List;
import java.util.Optional;
import java.sql.Timestamp;

public interface TransactionRequestRepository extends JpaRepository<TransactionRequest, Long> {
     @Query("SELECT r.product.productId FROM TransactionRequest r WHERE r.transactionRequestId = :requestId")
     Optional<Long> findProductIdForUpdate(@Param("requestId") Long requestId);

     @Query(value = "SELECT transaction_request_id AS requestId, product_id AS productId, buyer_id AS buyerId, price AS price, is_accepted AS accepted FROM transaction_request WHERE transaction_request_id = :requestId FOR UPDATE", nativeQuery = true)
     Optional<AcceptanceState> lockAcceptanceState(@Param("requestId") Long requestId);

     @Modifying
     @Query(value = "UPDATE transaction_request SET is_accepted = :accepted, updated_at = :updatedAt WHERE transaction_request_id = :requestId AND (is_accepted IS NULL OR is_accepted <> :accepted)", nativeQuery = true)
     int updateAcceptance(@Param("requestId") Long requestId, @Param("accepted") boolean accepted, @Param("updatedAt") Timestamp updatedAt);

     @Query("SELECT r.transactionRequestId FROM TransactionRequest r WHERE r.product.productId = :productId")
     List<Long> findVisibleIdsByProductId(@Param("productId") Long productId);

     @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED, readOnly = true)
     @Query("SELECT r.transactionRequestId FROM TransactionRequest r WHERE r.product.productId = :productId")
     List<Long> findCommittedIdsByProductId(@Param("productId") Long productId);

     interface AcceptanceState {
          Long getRequestId();
          Long getProductId();
          Long getBuyerId();
          Long getPrice();
          Boolean getAccepted();
     }
     List<TransactionRequest> findAllByProductProductId(Long productId);
     Optional<TransactionRequest> findByProductProductIdAndBuyerUserId(Long productId, Long buyerId);


}
