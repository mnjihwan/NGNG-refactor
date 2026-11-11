package com.ngng.api.user.point.payment;

import com.ngng.api.product.product.repository.ProductRepository;
import com.ngng.api.transaction.repository.TransactionDetailsRepository;
import com.ngng.api.user.point.dto.PaymentPointRequestDto;
import com.ngng.api.user.point.entity.PointHistory;
import com.ngng.api.user.point.repository.PaymentSettlementRepository;
import com.ngng.api.user.point.repository.PointHistoryRepository;
import com.ngng.api.user.point.service.PointHistoryService;
import com.ngng.api.user.user.entity.User;
import com.ngng.api.user.user.repository.UserRepository;
import com.ngng.api.user.user.service.AuthService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual immutable manifest gate + Spring advice, repository doubles. No physical DB rollback proof. */
class PaymentAtomicityTest {
    final ProductRepository products = mock(ProductRepository.class);
    final TransactionDetailsRepository details = mock(TransactionDetailsRepository.class);
    final UserRepository users = mock(UserRepository.class);
    final PointHistoryRepository histories = mock(PointHistoryRepository.class);
    final AuthService auth = mock(AuthService.class);
    final PaymentSettlementRepository settlements = PaymentFixture.settlements();
    final User buyer = User.builder().userId(7L).name("Fixture").build();
    final PaymentPointRequestDto request = new PaymentPointRequestDto(200L,42L);
    final RecordingTransactions transactions = new RecordingTransactions();
    @BeforeEach void setup() {
        PaymentFixture.authenticate(); PaymentFixture.allowPayment(products,details,7L,200L);
        when(auth.readAuthUser()).thenReturn(buyer);
        when(users.lockUserIdForPointUpdate(7L)).thenReturn(Optional.of(7L));
        when(histories.findLatestCostForUpdate(7L)).thenReturn(Optional.of(1000L));
        when(histories.save(any())).thenAnswer(call -> call.getArgument(0));
    }
    @AfterEach void clear() { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    PointHistoryService service(PaymentVerificationGate gate) {
        var target = new PointHistoryService(histories,auth,users,products,details,gate,settlements);
        var factory = new ProxyFactory(target); factory.setProxyTargetClass(true);
        var advice = new TransactionInterceptor(); advice.setTransactionManager(transactions);
        advice.setTransactionAttributeSource(new AnnotationTransactionAttributeSource()); factory.addAdvice(advice);
        return (PointHistoryService)factory.getProxy();
    }
    void conflict(PointHistoryService service) {
        var error = assertThrows(ResponseStatusException.class, () -> service.payment(request));
        assertEquals(409,error.getStatusCode().value());
    }
    @Test void debitFlushAndStatusTransitionCommitThroughOneAdviceInLockOrder() {
        assertEquals(800L,service(PaymentFixture.gate()).payment(request).getCost());
        var order = inOrder(products,details,users,histories,settlements);
        order.verify(products).lockTransactionProduct(42L); order.verify(details).lockPaymentTransactions(42L);
        order.verify(users).lockUserIdForPointUpdate(7L); order.verify(histories).findLatestCostForUpdate(7L);
        order.verify(histories).save(any()); order.verify(histories).flush();
        order.verify(settlements).markPaid(88L,42L,7L,999L,200L); order.verifyNoMoreInteractions();
        verify(details,never()).findById(any()); verify(details,never()).save(any());
        assertEquals(1,transactions.commits); assertEquals(0,transactions.rollbacks);
        assertEquals(TransactionDefinition.ISOLATION_READ_COMMITTED,transactions.isolation);
    }
    @Test void unverifiedIncludingFreshPendingTransactionFailsBeforeBalanceOrWrite() {
        conflict(service(PaymentVerificationGate.denyAll()));
        verify(users,never()).lockUserIdForPointUpdate(any()); verifyNoInteractions(histories,settlements);
    }
    @ParameterizedTest @CsvSource({"89,42,7,999,200","88,43,7,999,200","88,42,8,999,200","88,42,7,998,200","88,42,7,999,201"})
    void wholeApprovalTupleMustMatch(long id,long product,long buyerId,long seller,long price) {
        conflict(service(PaymentFixture.gate(id,product,buyerId,seller,price)));
        verifyNoInteractions(histories,settlements); verify(users,never()).lockUserIdForPointUpdate(any());
    }
    @ParameterizedTest @NullSource @ValueSource(longs={0,2,3,4,5,6,7,8})
    void nonpendingOrIncompleteStatusNeverDebits(Long status) {
        when(details.lockPaymentTransactions(42L)).thenReturn(List.of(new PaymentFixture.TransactionRow(88L,7L,200L,42L,999L,status)));
        conflict(service(PaymentFixture.gate())); verifyNoInteractions(histories,settlements);
    }
    @ParameterizedTest @NullSource @ValueSource(longs={0,-1})
    void incompleteSellerCannotBeReplacedByCurrentProductOwner(Long seller) {
        when(details.lockPaymentTransactions(42L)).thenReturn(List.of(new PaymentFixture.TransactionRow(88L,7L,200L,42L,seller,1L)));
        conflict(service(PaymentFixture.gate())); verifyNoInteractions(histories,settlements);
    }
    @Test void successThenRetryReturns409WithOneAppendAndOneTransition() {
        AtomicLong status = new AtomicLong(1);
        when(details.lockPaymentTransactions(42L)).thenAnswer(call -> List.of(new PaymentFixture.TransactionRow(88L,7L,200L,42L,999L,status.get())));
        when(settlements.markPaid(88L,42L,7L,999L,200L)).thenAnswer(call -> { status.set(2); return 1; });
        var service = service(PaymentFixture.gate()); service.payment(request); conflict(service);
        verify(histories).save(any()); verify(histories).flush(); verify(settlements).markPaid(88L,42L,7L,999L,200L);
        assertEquals(1,transactions.commits); assertEquals(1,transactions.rollbacks);
    }
    @ParameterizedTest @ValueSource(ints={0,2})
    void conditionalTransitionMismatchInvokesRollbackAdviceAfterDebitFlush(int changed) {
        when(settlements.markPaid(any(),any(),any(),any(),any())).thenReturn(changed);
        conflict(service(PaymentFixture.gate())); verify(histories).save(any()); verify(histories).flush();
        assertEquals(0,transactions.commits); assertEquals(1,transactions.rollbacks);
    }
    @ParameterizedTest @ValueSource(strings={"save","flush","transition","commit"})
    void writeAndCommitFailuresEscapeWithoutSuccessfulResult(String stage) {
        var error = new IllegalStateException("synthetic-"+stage);
        switch(stage) {
            case "save" -> doThrow(error).when(histories).save(any());
            case "flush" -> doThrow(error).when(histories).flush();
            case "transition" -> doThrow(error).when(settlements).markPaid(any(),any(),any(),any(),any());
            case "commit" -> transactions.commitFailure=error;
        }
        assertSame(error,assertThrows(IllegalStateException.class,()->service(PaymentFixture.gate()).payment(request)));
        assertEquals(0,transactions.commits);
        if (!stage.equals("commit")) assertEquals(1,transactions.rollbacks);
        if(stage.equals("save")||stage.equals("flush")) verifyNoInteractions(settlements);
    }
    @Test void expiryWhileWaitingForBalancePreventsEveryWrite() throws Exception {
        var now = new java.util.concurrent.atomic.AtomicReference<>(java.time.Instant.parse("2030-01-01T00:00:00Z"));
        var nanos = new AtomicLong();
        java.time.Clock clock = new java.time.Clock() {
            public java.time.ZoneId getZone(){return java.time.ZoneOffset.UTC;}
            public java.time.Clock withZone(java.time.ZoneId zone){return this;}
            public java.time.Instant instant(){return now.get();}
        };
        var file=java.nio.file.Files.createTempFile("rf037-expiry-", ".json");
        PaymentVerificationGate gate;
        try {
            java.nio.file.Files.writeString(file,"{\"formatVersion\":1,\"approvalVersion\":\"s\",\"environmentId\":\"s\",\"databaseGeneration\":\"s\",\"verifiedAt\":\"2030-01-01T00:00:00Z\",\"validFrom\":\"2030-01-01T00:00:00Z\",\"validUntil\":\"2030-01-01T00:00:01Z\",\"entries\":[{\"transactionId\":88,\"productId\":42,\"buyerId\":7,\"sellerId\":999,\"price\":200,\"verificationId\":\"s\",\"verifiedBy\":\"synthetic\",\"evidenceRef\":\"synthetic\"}]}");
            gate=PaymentVerificationGate.fromFile(file,new PaymentVerificationGate.Expectations("s","s","s",true,true),clock,nanos::get);
        } finally { java.nio.file.Files.deleteIfExists(file); }
        when(histories.findLatestCostForUpdate(7L)).thenAnswer(call->{now.set(now.get().plusSeconds(1));nanos.set(1_000_000_000);return Optional.of(1000L);});
        conflict(service(gate)); verify(histories,never()).save(any()); verify(histories,never()).flush(); verifyNoInteractions(settlements);
    }
    @Test void conditionalSqlUsesWholeTupleWithoutContextClearOrSeparateTransaction() throws Exception {
        var method=PaymentSettlementRepository.class.getMethod("markPaid",Long.class,Long.class,Long.class,Long.class,Long.class);
        var query=method.getAnnotation(org.springframework.data.jpa.repository.Query.class);
        assertTrue(query.nativeQuery());
        assertEquals("UPDATE transaction_details SET status_id = 2, updated_at = CURRENT_TIMESTAMP(6) WHERE transaction_details_id = :transactionId AND product_id = :productId AND consumer_id = :buyerId AND seller_id = :sellerId AND price = :price AND status_id = 1",query.value());
        var modifying=method.getAnnotation(org.springframework.data.jpa.repository.Modifying.class);
        assertFalse(modifying.clearAutomatically()); assertFalse(modifying.flushAutomatically());
        assertNull(method.getAnnotation(org.springframework.transaction.annotation.Transactional.class));
    }
    static class RecordingTransactions extends AbstractPlatformTransactionManager {
        int commits,rollbacks,isolation; RuntimeException commitFailure;
        protected Object doGetTransaction(){return new Object();}
        protected void doBegin(Object tx,TransactionDefinition definition){isolation=definition.getIsolationLevel();}
        protected void doCommit(DefaultTransactionStatus status){if(commitFailure!=null)throw commitFailure;commits++;}
        protected void doRollback(DefaultTransactionStatus status){rollbacks++;}
    }
}
