package com.ngng.api.user.point.repository;

import com.ngng.api.transaction.entity.TransactionDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Internal payment writer. Caller holds product, transaction and buyer/balance locks. */
public interface PaymentSettlementRepository extends JpaRepository<TransactionDetails, Long> {
    // Do not clear the persistence context or commit independently of the debit.
    @Modifying
    @Query(value = "UPDATE transaction_details SET status_id = 2, updated_at = CURRENT_TIMESTAMP(6) "
            + "WHERE transaction_details_id = :transactionId AND product_id = :productId "
            + "AND consumer_id = :buyerId AND seller_id = :sellerId AND price = :price AND status_id = 1",
            nativeQuery = true)
    int markPaid(@Param("transactionId") Long transactionId, @Param("productId") Long productId,
                 @Param("buyerId") Long buyerId, @Param("sellerId") Long sellerId, @Param("price") Long price);
}
