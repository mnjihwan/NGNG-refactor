package com.ngng.chat.product.dto;

import com.ngng.chat.product.entity.TransactionRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TransactionRequestDTO {
    Long requestId;
    Long productId;
    Long sellerId;
    Long buyerId;
    Long price;
    Timestamp createdAt;
    Timestamp updatedAt;
    Boolean isAccepted;
    public TransactionRequestDTO (TransactionRequest data) {
        this.requestId = data.getTransactionRequestId();
        this.productId = data.getProduct().getId();
        this.sellerId = data.getSeller().getId();
        this.buyerId = data.getBuyer().getId();
        this.price = data.getPrice();
        this.createdAt = data.getCreatedAt();
        this.updatedAt = data.getUpdatedAt();
        this.isAccepted = data.getIsAccepted();

    }
}
