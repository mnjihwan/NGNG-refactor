package com.ngng.api.user.point.payment;

import com.ngng.api.product.product.entity.Product;
import com.ngng.api.product.product.repository.ProductRepository;
import com.ngng.api.transaction.entity.TransactionDetails;
import com.ngng.api.transaction.repository.TransactionDetailsRepository;
import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.hibernate.boot.model.naming.Identifier;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.data.repository.query.Param;

import java.lang.reflect.ParameterizedType;
import java.math.BigInteger;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Static query/mapping and Spring projection contract only. No JDBC execution or physical lock proof. */
class PaymentLockQueryContractTest {
    @Test void paymentQueryIsNativeCurrentReadOnMappedColumnsAndReturnsAllScalarRows() throws Exception {
        var method = TransactionDetailsRepository.class.getMethod("lockPaymentTransactions", Long.class);
        Query query = method.getAnnotation(Query.class);
        String id = column(TransactionDetails.class, "id");
        String consumer = TransactionDetails.class.getDeclaredField("consumer").getAnnotation(JoinColumn.class).name();
        String product = TransactionDetails.class.getDeclaredField("product").getAnnotation(JoinColumn.class).name();
        assertTrue(query.nativeQuery());
        assertEquals("SELECT " + id + " AS transactionId, " + consumer + " AS consumerId, price AS price "
                + "FROM transaction_details WHERE " + product + " = :productId FOR UPDATE", query.value());
        assertEquals("productId", method.getParameters()[0].getAnnotation(Param.class).value());
        ParameterizedType type = (ParameterizedType) method.getGenericReturnType();
        assertEquals(List.class, type.getRawType());
        assertArrayEquals(new Object[]{TransactionDetailsRepository.PaymentTransaction.class}, type.getActualTypeArguments());
        assertEquals(Set.of("getTransactionId", "getConsumerId", "getPrice"),
                new HashSet<>(Arrays.stream(TransactionDetailsRepository.PaymentTransaction.class.getDeclaredMethods()).map(m -> m.getName()).toList()));
    }
    @Test void existingProductGuardUsesActualProductPrimaryKeyWithoutAnEntityGraph() throws Exception {
        Query query = ProductRepository.class.getMethod("lockTransactionProduct", Long.class).getAnnotation(Query.class);
        String id = column(Product.class, "productId");
        assertTrue(query.nativeQuery());
        assertEquals("SELECT " + id + " AS productId, user_id AS sellerId FROM product WHERE " + id + " = :productId FOR UPDATE", query.value());
    }
    @Test void scalarAliasesUseSpringProjectionConversionsAndPreserveNulls() {
        var factory = new SpelAwareProxyProjectionFactory();
        var row = factory.createProjection(TransactionDetailsRepository.PaymentTransaction.class,
                Map.of("transactionId", BigInteger.valueOf(88), "consumerId", BigInteger.valueOf(Long.MAX_VALUE), "price", BigInteger.valueOf(8000)));
        assertEquals(88L, row.getTransactionId()); assertEquals(Long.MAX_VALUE, row.getConsumerId()); assertEquals(8000L, row.getPrice());
        Map<String, Object> missing = new HashMap<>(); missing.put("transactionId", null); missing.put("consumerId", null); missing.put("price", null);
        var nulls = factory.createProjection(TransactionDetailsRepository.PaymentTransaction.class, missing);
        assertNull(nulls.getTransactionId()); assertNull(nulls.getConsumerId()); assertNull(nulls.getPrice());
    }
    private String column(Class<?> entity, String field) throws Exception {
        Column mapping = entity.getDeclaredField(field).getAnnotation(Column.class);
        String name = mapping == null || mapping.name().isEmpty() ? field : mapping.name();
        return new CamelCaseToUnderscoresNamingStrategy().toPhysicalColumnName(Identifier.toIdentifier(name), null).getText();
    }
}
