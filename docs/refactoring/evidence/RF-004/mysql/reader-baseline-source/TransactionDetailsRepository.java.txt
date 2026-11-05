package com.ngng.api.transaction.repository;

import com.ngng.api.transaction.entity.TransactionDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Isolation;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionDetailsRepository extends JpaRepository<TransactionDetails,Long> {

    @Query("SELECT d.id FROM TransactionDetails d WHERE d.product.productId = :productId")
    List<Long> findVisibleIdsByProductId(@Param("productId") Long productId);

    // A short independent read avoids the outer RR snapshot without gap locks.
    // The caller retains its product lock and merges its own uncommitted IDs.
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED, readOnly = true)
    @Query("SELECT d.id FROM TransactionDetails d WHERE d.product.productId = :productId")
    List<Long> findCommittedIdsByProductId(@Param("productId") Long productId);

    @Query(value = "SELECT transaction_details_id AS id, consumer_id AS buyerId, seller_id AS sellerId, price AS price FROM transaction_details WHERE transaction_details_id = :detailsId FOR UPDATE", nativeQuery = true)
    Optional<AcceptanceDetails> lockAcceptanceDetailsById(@Param("detailsId") Long detailsId);

    interface AcceptanceDetails {
        Long getId();
        Long getBuyerId();
        Long getSellerId();
        Long getPrice();
    }



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
