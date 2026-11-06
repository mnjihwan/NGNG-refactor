package com.ngng.api.transaction.repository;

import com.ngng.api.transaction.entity.TransactionRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TransactionRequestRepository extends JpaRepository<TransactionRequest, Long> {
     @Query("SELECT r.product.productId FROM TransactionRequest r WHERE r.transactionRequestId = :requestId")
     Optional<Long> findProductIdForUpdate(@Param("requestId") Long requestId);

     List<TransactionRequest> findAllByProductProductId(Long productId);
     Optional<TransactionRequest> findByProductProductIdAndBuyerUserId(Long productId, Long buyerId);


}
