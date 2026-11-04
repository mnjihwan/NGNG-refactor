package com.ngng.chat.transaction.entity.repository;

import com.ngng.chat.transaction.entity.TransactionDetails;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TransactionDetailsRepository extends JpaRepository<TransactionDetails, Long> {
    public Optional<TransactionDetails> findByProductId(Long productId);
}
