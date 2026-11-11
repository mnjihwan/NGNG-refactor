package com.ngng.api.user.point.payment;

import com.ngng.api.global.security.custom.CustomUserDetails;
import com.ngng.api.product.product.repository.ProductRepository;
import com.ngng.api.transaction.repository.TransactionDetailsRepository;
import com.ngng.api.user.point.dto.PaymentPointRequestDto;
import com.ngng.api.user.point.entity.PointHistory;
import com.ngng.api.user.point.repository.PointHistoryRepository;
import com.ngng.api.user.point.service.PointHistoryService;
import com.ngng.api.user.user.entity.User;
import com.ngng.api.user.user.repository.UserRepository;
import com.ngng.api.user.user.service.AuthService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real AuthService, synthetic scalar repositories; no DB or network. Advice is not DB rollback evidence. */
class PaymentTrustServiceTest {
    final UserRepository users = mock(UserRepository.class);
    final ProductRepository products = mock(ProductRepository.class);
    final TransactionDetailsRepository details = mock(TransactionDetailsRepository.class);
    final PointHistoryRepository histories = mock(PointHistoryRepository.class);
    final PointHistoryService service = new PointHistoryService(histories, new AuthService(users), users, products, details, PaymentFixture.gate(), PaymentFixture.settlements());
    final User buyer = User.builder().userId(7001L).name("Fixture").build();

    @BeforeEach void setup() {
        PaymentFixture.authenticate();
        when(users.findUserByEmail("rf035-service@example.invalid")).thenReturn(Optional.of(buyer));
        PaymentFixture.allowPayment(products, details, 7001L, 8000L);
        when(users.lockUserIdForPointUpdate(7001L)).thenReturn(Optional.of(7001L));
        when(histories.findLatestCostForUpdate(7001L)).thenReturn(Optional.of(10000L));
        when(histories.save(any())).thenAnswer(call -> call.getArgument(0));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    PaymentPointRequestDto request(Long amount) { return new PaymentPointRequestDto(amount, 42L); }
    void failure(int status, PaymentPointRequestDto request) {
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> new PointHistoryService(histories, new AuthService(users), users, products, details, PaymentFixture.gate(), PaymentFixture.settlements()).payment(request));
        assertEquals(status, error.getStatusCode().value());
        assertFalse(error.getReason().contains("7001"));
        assertFalse(error.getReason().contains("8000"));
        verify(histories, never()).save(any());
    }
    void noBalanceAccess() {
        verify(users, never()).lockUserIdForPointUpdate(any());
        verifyNoInteractions(histories);
    }
    @ParameterizedTest @NullSource @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    void invalidProductIs400BeforeAuthenticationOrRepositories(Long id) {
        SecurityContextHolder.clearContext();
        failure(400, new PaymentPointRequestDto(8000L, id));
        verifyNoInteractions(users, products, details, histories);
    }
    @ParameterizedTest @NullSource @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    void invalidAmountIs400BeforeAuthenticationOrRepositories(Long amount) {
        SecurityContextHolder.clearContext();
        failure(400, request(amount));
        verifyNoInteractions(users, products, details, histories);
    }
    @ParameterizedTest @ValueSource(strings = {"missing", "anonymous", "unauthenticated", "nullUser", "nullEmail", "blankEmail"})
    void invalidDirectInvocationIdentityIs401BeforeDbReads(String kind) {
        SecurityContextHolder.clearContext();
        if (!kind.equals("missing")) {
            Object principal = switch (kind) {
                case "anonymous" -> "anonymousUser";
                case "nullUser" -> new CustomUserDetails(null);
                case "nullEmail" -> new CustomUserDetails(User.builder().build());
                case "blankEmail" -> new CustomUserDetails(User.builder().email(" ").build());
                default -> new CustomUserDetails(User.builder().email("fixture@example.invalid").build());
            };
            var authentication = kind.equals("unauthenticated")
                    ? new UsernamePasswordAuthenticationToken(principal, null)
                    : new UsernamePasswordAuthenticationToken(principal, null, List.of());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        failure(401, request(8000L));
        verifyNoInteractions(users, products, details, histories);
    }
    @ParameterizedTest @ValueSource(strings = {"absent", "nullId"})
    void missingDatabaseIdentityIs401BeforeProductRead(String kind) {
        when(users.findUserByEmail(any())).thenReturn(kind.equals("absent") ? Optional.empty() : Optional.of(new User()));
        failure(401, request(8000L));
        verifyNoInteractions(products, details, histories);
        noBalanceAccess();
    }
    @Test void userRepositoryFailureIsNotConvertedToAuthenticationFailure() {
        var error = new DataAccessResourceFailureException("synthetic-unavailable");
        when(users.findUserByEmail(any())).thenThrow(error);
        assertSame(error, assertThrows(RuntimeException.class, () -> new PointHistoryService(histories, new AuthService(users), users, products, details, PaymentFixture.gate(), PaymentFixture.settlements()).payment(request(8000L))));
        verifyNoInteractions(products, details, histories);
    }
    @Test void missingProductIs404BeforeTransactionOrPointRead() {
        when(products.lockTransactionProduct(42L)).thenReturn(Optional.empty());
        failure(404, request(8000L));
        verifyNoInteractions(details); noBalanceAccess();
    }
    @Test void missingTransactionIs404() {
        when(details.lockPaymentTransactions(42L)).thenReturn(List.of());
        failure(404, request(8000L)); noBalanceAccess();
    }
    @Test void multipleTransactionsAre409WithoutPickingFirstEvenForWrongBuyer() {
        when(details.lockPaymentTransactions(42L)).thenReturn(List.of(
                new PaymentFixture.TransactionRow(88L, 999L, 1L), new PaymentFixture.TransactionRow(89L, 7001L, 8000L)));
        failure(409, request(8000L)); noBalanceAccess();
    }
    @ParameterizedTest @ValueSource(strings = {"id", "consumer", "both"})
    void incompleteScalarIdentityIs409BeforeBuyerOrPriceCheck(String missing) {
        when(details.lockPaymentTransactions(42L)).thenReturn(List.of(new PaymentFixture.TransactionRow(
                missing.equals("consumer") ? 88L : null, missing.equals("id") ? 999L : null, null)));
        failure(409, request(8000L)); noBalanceAccess();
    }
    @ParameterizedTest @ValueSource(longs = {999, 7002, Long.MAX_VALUE})
    void otherBuyerIs403BeforeReadingPrice(Long consumer) {
        var row = mock(TransactionDetailsRepository.PaymentTransaction.class);
        when(row.getTransactionId()).thenReturn(88L); when(row.getConsumerId()).thenReturn(consumer);
        when(row.getProductId()).thenReturn(42L); when(row.getSellerId()).thenReturn(999L); when(row.getStatusId()).thenReturn(1L);
        doThrow(new AssertionError("unauthorized price read")).when(row).getPrice();
        when(details.lockPaymentTransactions(42L)).thenReturn(List.of(row));
        failure(403, request(8000L)); verify(row, never()).getPrice(); noBalanceAccess();
    }
    @ParameterizedTest @NullSource @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    void invalidStoredPriceIs409WithoutReadingBalance(Long price) {
        PaymentFixture.allowPayment(products, details, 7001L, price);
        failure(409, request(8000L)); noBalanceAccess();
    }
    @ParameterizedTest @ValueSource(longs = {1, 7999, 8001, 10000, Long.MAX_VALUE})
    void tamperedBodyIs409WithoutAutomaticServerPriceDebit(long amount) {
        failure(409, request(amount)); noBalanceAccess();
    }
    @Test void matchingNegotiatedPriceIsDebitedWithProductThenTransactionThenBalanceLockOrder() {
        PointHistory saved = new PointHistoryService(histories, new AuthService(users), users, products, details, PaymentFixture.gate(), PaymentFixture.settlements()).payment(request(8000L));
        assertEquals(10000L, saved.getBeforeCost()); assertEquals(8000L, saved.getAddCost());
        assertEquals(2000L, saved.getCost()); assertSame(buyer, saved.getUser());
        assertEquals("결제", saved.getType());
        assertEquals("Fixture 유저가 8000 금액 만큼 42상품을 구매 하셨습니다.", saved.getTypeDetail());
        var order = inOrder(users, products, details, histories);
        order.verify(users).findUserByEmail("rf035-service@example.invalid");
        order.verify(products).lockTransactionProduct(42L);
        order.verify(details).lockPaymentTransactions(42L);
        order.verify(users).lockUserIdForPointUpdate(7001L);
        order.verify(histories).findLatestCostForUpdate(7001L);
        order.verify(histories).save(saved); order.verify(histories).flush(); order.verifyNoMoreInteractions();
    }
    @ParameterizedTest @ValueSource(longs = {7, 7001, Long.MAX_VALUE})
    void buyerIdsUseValueEqualityIncludingSeparateBoxedReferences(long id) {
        buyer.setUserId(Long.valueOf(Long.toString(id)));
        Long consumer = Long.valueOf(Long.toString(id));
        PaymentFixture.allowPayment(products, details, consumer, 8000L);
        when(users.lockUserIdForPointUpdate(id)).thenReturn(Optional.of(id));
        when(histories.findLatestCostForUpdate(id)).thenReturn(Optional.of(10000L));
        assertEquals(2000L, new PointHistoryService(histories, new AuthService(users), users, products, details, PaymentFixture.gate(), PaymentFixture.settlements()).payment(request(8000L)).getCost());
    }
    @Test void equalBalanceSucceedsWithZeroAndInsufficientBalanceStill400() {
        when(histories.findLatestCostForUpdate(7001L)).thenReturn(Optional.of(7999L));
        failure(400, request(8000L));
        when(histories.findLatestCostForUpdate(7001L)).thenReturn(Optional.of(8000L));
        assertEquals(0L, new PointHistoryService(histories, new AuthService(users), users, products, details, PaymentFixture.gate(), PaymentFixture.settlements()).payment(request(8000L)).getCost());
    }
    @Test void transactionReadFailureStopsBeforePointLock() {
        var error = new DataAccessResourceFailureException("synthetic-unavailable");
        when(details.lockPaymentTransactions(42L)).thenThrow(error);
        assertSame(error, assertThrows(RuntimeException.class, () -> new PointHistoryService(histories, new AuthService(users), users, products, details, PaymentFixture.gate(), PaymentFixture.settlements()).payment(request(8000L))));
        noBalanceAccess();
    }
    @Test void allLocksAndAppendShareTheSameSpringWriteAdvice() {
        var manager = new RecordingTransactions();
        var factory = new ProxyFactory(new PointHistoryService(histories, new AuthService(users), users, products, details, PaymentFixture.gate(), PaymentFixture.settlements())); factory.setProxyTargetClass(true);
        var advice = new TransactionInterceptor(); advice.setTransactionManager(manager);
        advice.setTransactionAttributeSource(new AnnotationTransactionAttributeSource()); factory.addAdvice(advice);
        var proxy = (PointHistoryService) factory.getProxy();
        when(products.lockTransactionProduct(42L)).thenAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            return Optional.of(new PaymentFixture.ProductRow(42L, null));
        });
        when(details.lockPaymentTransactions(42L)).thenAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            return List.of(new PaymentFixture.TransactionRow(88L, 7001L, 8000L));
        });
        when(users.lockUserIdForPointUpdate(7001L)).thenAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive()); return Optional.of(7001L);
        });
        when(histories.findLatestCostForUpdate(7001L)).thenAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive()); return Optional.of(10000L);
        });
        when(histories.save(any())).thenAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive()); return call.getArgument(0);
        });
        assertEquals(2000L, proxy.payment(request(8000L)).getCost()); assertEquals(1, manager.commits);
        var saveError = new IllegalStateException("synthetic-save-failure");
        doThrow(saveError).when(histories).save(any());
        assertSame(saveError, assertThrows(IllegalStateException.class, () -> proxy.payment(request(8000L))));
        assertEquals(1, manager.rollbacks); assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }
    static class RecordingTransactions extends AbstractPlatformTransactionManager {
        int commits, rollbacks;
        protected Object doGetTransaction() { return new Object(); }
        protected void doBegin(Object transaction, TransactionDefinition definition) { }
        protected void doCommit(DefaultTransactionStatus status) { commits++; }
        protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
    }
}
