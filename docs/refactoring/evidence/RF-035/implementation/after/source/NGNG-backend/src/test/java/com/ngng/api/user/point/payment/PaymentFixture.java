package com.ngng.api.user.point.payment;

import com.ngng.api.global.security.custom.CustomUserDetails;
import com.ngng.api.product.product.repository.ProductRepository;
import com.ngng.api.transaction.repository.TransactionDetailsRepository;
import com.ngng.api.user.user.entity.User;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;

/** Synthetic scalar rows only. These doubles do not prove physical SQL locking. */
public final class PaymentFixture {
    private PaymentFixture() { }

    public static void authenticate() {
        User principal = User.builder().email("rf035-service@example.invalid").build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CustomUserDetails(principal), null, List.of()));
    }

    public static void allowPayment(ProductRepository products, TransactionDetailsRepository details,
                                    Long consumerId, Long price) {
        when(products.lockTransactionProduct(42L)).thenReturn(Optional.of(new ProductRow(42L, 999L)));
        when(details.lockPaymentTransactions(42L)).thenReturn(List.of(new TransactionRow(88L, consumerId, price)));
    }

    public record ProductRow(Long productId, Long sellerId) implements ProductRepository.TransactionProduct {
        public Long getProductId() { return productId; }
        public Long getSellerId() { return sellerId; }
    }

    public record TransactionRow(Long transactionId, Long consumerId, Long price)
            implements TransactionDetailsRepository.PaymentTransaction {
        public Long getTransactionId() { return transactionId; }
        public Long getConsumerId() { return consumerId; }
        public Long getPrice() { return price; }
    }
}
