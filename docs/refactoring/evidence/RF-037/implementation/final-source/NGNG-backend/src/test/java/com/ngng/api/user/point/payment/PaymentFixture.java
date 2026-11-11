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
    private static final ThreadLocal<long[]> binding = ThreadLocal.withInitial(() -> new long[]{88,42,7,999,200});

    public static PaymentVerificationGate gate() {
        long[] b = binding.get();
        return gate(b[0], b[1], b[2], b[3], b[4]);
    }
    public static PaymentVerificationGate gate(long id, long product, long buyer, long seller, long price) {
        return gateBindings(new long[]{id, product, buyer, seller, price});
    }
    public static PaymentVerificationGate gateBindings(long[]... bindings) {
        try {
            java.nio.file.Path file = java.nio.file.Files.createTempFile("rf037-synthetic-", ".json");
            try {
                StringBuilder rows = new StringBuilder();
                for (long[] row : bindings) {
                    if (!rows.isEmpty()) rows.append(",");
                    rows.append("{\"transactionId\":" + row[0] + ",\"productId\":" + row[1] + ",\"buyerId\":" + row[2] + ",\"sellerId\":" + row[3] + ",\"price\":" + row[4] + ",\"verificationId\":\"synthetic-" + row[0] + "\",\"verifiedBy\":\"test-only\",\"evidenceRef\":\"synthetic-no-real-ledger\"}");
                }
                String json = "{\"formatVersion\":1,\"approvalVersion\":\"synthetic\",\"environmentId\":\"synthetic\",\"databaseGeneration\":\"synthetic\","
                        + "\"verifiedAt\":\"2020-01-01T00:00:00Z\",\"validFrom\":\"2020-01-01T00:00:00Z\",\"validUntil\":\"2099-01-01T00:00:00Z\",\"entries\":[" + rows + "]}";
                java.nio.file.Files.writeString(file, json);
                return PaymentVerificationGate.fromFile(file, new PaymentVerificationGate.Expectations("synthetic", "synthetic", "synthetic", true, true));
            } finally { java.nio.file.Files.deleteIfExists(file); }
        } catch (java.io.IOException failure) { throw new AssertionError(failure); }
    }
    public static com.ngng.api.user.point.repository.PaymentSettlementRepository settlements() {
        var repository = org.mockito.Mockito.mock(com.ngng.api.user.point.repository.PaymentSettlementRepository.class);
        when(repository.markPaid(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(1);
        return repository;
    }

    public static void authenticate() {
        User principal = User.builder().email("rf035-service@example.invalid").build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CustomUserDetails(principal), null, List.of()));
    }

    public static void allowPayment(ProductRepository products, TransactionDetailsRepository details,
                                    Long consumerId, Long price) {
        if (consumerId != null && price != null && consumerId > 0 && price > 0) binding.set(new long[]{88,42,consumerId,999,price});
        when(products.lockTransactionProduct(42L)).thenReturn(Optional.of(new ProductRow(42L, 999L)));
        when(details.lockPaymentTransactions(42L)).thenReturn(List.of(new TransactionRow(88L, consumerId, price)));
    }

    public record ProductRow(Long productId, Long sellerId) implements ProductRepository.TransactionProduct {
        public Long getProductId() { return productId; }
        public Long getSellerId() { return sellerId; }
    }

    public record TransactionRow(Long transactionId, Long consumerId, Long price, Long productId, Long sellerId, Long statusId)
            implements TransactionDetailsRepository.PaymentTransaction {
        public TransactionRow(Long transactionId, Long consumerId, Long price) { this(transactionId, consumerId, price, 42L, 999L, 1L); }
        public Long getTransactionId() { return transactionId; }
        public Long getConsumerId() { return consumerId; }
        public Long getPrice() { return price; }
        public Long getProductId() { return productId; }
        public Long getSellerId() { return sellerId; }
        public Long getStatusId() { return statusId; }
    }
}
