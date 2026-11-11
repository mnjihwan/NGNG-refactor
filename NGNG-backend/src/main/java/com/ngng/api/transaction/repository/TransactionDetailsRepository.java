package com.ngng.api.transaction.repository;

import com.ngng.api.transaction.entity.TransactionDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionDetailsRepository extends JpaRepository<TransactionDetails,Long> {

    // Current scalar read: do not reuse an entity snapshot or lock eager associations.
    @Query(value = "SELECT transaction_details_id AS transactionId, consumer_id AS consumerId, price AS price, "
            + "product_id AS productId, seller_id AS sellerId, status_id AS statusId "
            + "FROM transaction_details WHERE product_id = :productId FOR UPDATE", nativeQuery = true)
    List<PaymentTransaction> lockPaymentTransactions(@Param("productId") Long productId);

    interface PaymentTransaction {
        Long getTransactionId();
        Long getConsumerId();
        Long getPrice();
        Long getProductId();
        Long getSellerId();
        Long getStatusId();
    }

    // Identification only. Revalidate the product relationship after both row locks.
    @Query(value = "SELECT product_id FROM transaction_details WHERE transaction_details_id = :transactionId", nativeQuery = true)
    Optional<Long> findStatusProductId(@Param("transactionId") Long transactionId);

    // Must follow the product PK scalar lock in the same write transaction.
    // Current scalar read avoids an old persistence-context entity and eager association locks.
    @Query(value = "SELECT transaction_details_id AS transactionId, product_id AS productId, "
            + "consumer_id AS consumerId, seller_id AS sellerId, status_id AS statusId, price AS price, address AS address "
            + "FROM transaction_details WHERE transaction_details_id = :transactionId FOR UPDATE", nativeQuery = true)
    Optional<StatusTransaction> lockStatusTransaction(@Param("transactionId") Long transactionId);

    interface StatusTransaction {
        Long getTransactionId();
        Long getProductId();
        Long getConsumerId();
        Long getSellerId();
        Long getStatusId();
        Long getPrice();
        String getAddress();
    }

    List<TransactionDetails> findAllByProductProductId(Long productId);
    @Query("SELECT t FROM TransactionDetails t WHERE t.consumer.id = :userId")
    List<TransactionDetails> findALlByConsumerId(@Param("userId") Long userId);

    @Query("SELECT t FROM TransactionDetails t WHERE t.seller.id = :userId")
    List<TransactionDetails> findAllBySellerId(@Param("userId") Long userId);


    @Query("SELECT t FROM TransactionDetails t WHERE t.seller.id = :userId and t.status.id =:status")
    List<TransactionDetails> findAllBySellerIdFilterStatus(@Param("userId") Long userId,@Param("status") Long status);


    @Modifying(clearAutomatically = true)
    @Query("UPDATE TransactionDetails t SET "
    + "t.status.id = CASE WHEN :status IS NULL THEN t.status.id ELSE :status END "  // Removed the extra space before t.status_id
    + "WHERE t.id = :id")
    int updateTransactionStatus(@Param("id") Long id,@Param("status") Long status);
}

//UPDATE transaction_details
//SET status_id = CASE WHEN 2 IS NULL THEN transaction_details.status_id ELSE 2 END
//WHERE transaction_details_id = 1;
