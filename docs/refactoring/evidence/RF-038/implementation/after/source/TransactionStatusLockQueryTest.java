package com.ngng.api.transaction.status;

import com.ngng.api.transaction.repository.TransactionDetailsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.data.repository.query.Param;
import java.math.BigInteger;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** SQL declaration/projection contracts only, never a MySQL lock execution. */
class TransactionStatusLockQueryTest {
    @Test void identificationQueryIsScalarWithoutClaimingAStateLock() throws Exception {
        var method = TransactionDetailsRepository.class.getMethod("findStatusProductId",Long.class);
        var query = method.getAnnotation(Query.class);
        assertTrue(query.nativeQuery());
        assertEquals("SELECT product_id FROM transaction_details WHERE transaction_details_id = :transactionId",query.value());
        assertEquals("transactionId",method.getParameters()[0].getAnnotation(Param.class).value());
        assertEquals(Optional.class,method.getReturnType());
    }
    @Test void currentLockReadsOnlyTransactionScalarsByPrimaryKey() throws Exception {
        var method = TransactionDetailsRepository.class.getMethod("lockStatusTransaction",Long.class);
        var query = method.getAnnotation(Query.class);
        assertTrue(query.nativeQuery());
        assertTrue(query.value().endsWith("WHERE transaction_details_id = :transactionId FOR UPDATE"));
        assertFalse(query.value().toUpperCase(Locale.ROOT).contains("JOIN"));
        for (String alias : List.of("transactionId","productId","consumerId","sellerId","statusId","price","address")) {
            assertTrue(query.value().contains(" AS " + alias),alias);
        }
        assertEquals("transactionId",method.getParameters()[0].getAnnotation(Param.class).value());
        assertEquals(Optional.class,method.getReturnType());
    }
    @Test void projectionAliasesConvertNumericScalarsAndPreserveNullableData() {
        var values = new HashMap<String,Object>();
        values.put("transactionId",BigInteger.valueOf(88)); values.put("productId",BigInteger.valueOf(7));
        values.put("consumerId",BigInteger.valueOf(2000)); values.put("sellerId",BigInteger.valueOf(1000));
        values.put("statusId",BigInteger.valueOf(2)); values.put("price",BigInteger.valueOf(300)); values.put("address","synthetic-address");
        var factory = new SpelAwareProxyProjectionFactory();
        var row = factory.createProjection(TransactionDetailsRepository.StatusTransaction.class,values);
        assertEquals(88L,row.getTransactionId()); assertEquals(7L,row.getProductId());
        assertEquals(2000L,row.getConsumerId()); assertEquals(1000L,row.getSellerId());
        assertEquals(2L,row.getStatusId()); assertEquals(300L,row.getPrice()); assertEquals("synthetic-address",row.getAddress());
        values.clear(); row = factory.createProjection(TransactionDetailsRepository.StatusTransaction.class,values);
        assertNull(row.getTransactionId()); assertNull(row.getConsumerId()); assertNull(row.getStatusId()); assertNull(row.getAddress());
    }
}
