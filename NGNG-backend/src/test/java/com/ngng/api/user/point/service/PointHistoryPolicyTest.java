package com.ngng.api.user.point.service;

import com.ngng.api.product.product.repository.ProductRepository;
import com.ngng.api.transaction.repository.TransactionDetailsRepository;
import com.ngng.api.user.point.payment.PaymentFixture;


import com.ngng.api.user.point.dto.CreateAddPointRequestDTO;
import com.ngng.api.user.point.dto.PaymentPointRequestDto;
import com.ngng.api.user.point.entity.PointHistory;
import com.ngng.api.user.point.repository.PointHistoryRepository;
import com.ngng.api.user.user.entity.User;
import com.ngng.api.user.user.repository.UserRepository;
import com.ngng.api.user.user.service.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Mocks verify arithmetic/call order and Spring transaction advice, not DB locks or rollback. */
class PointHistoryPolicyTest {
    @org.junit.jupiter.api.AfterEach void clearAuthentication() { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    private final PointHistoryRepository histories = mock(PointHistoryRepository.class);
    private final AuthService auth = mock(AuthService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final TransactionDetailsRepository details = mock(TransactionDetailsRepository.class);
    private final User user = User.builder().userId(7L).name("Alice").build();
    private PointHistoryService service = new PointHistoryService(histories, auth, users, products, details, PaymentFixture.gate(), PaymentFixture.settlements());

    private void balance(long cost) {
        PaymentFixture.authenticate();
        PaymentFixture.allowPayment(products, details, 7L, 200L);
        when(auth.readAuthUser()).thenReturn(user);
        when(users.lockUserIdForPointUpdate(7L)).thenReturn(Optional.of(7L));
        when(histories.findLatestCostForUpdate(7L)).thenReturn(Optional.of(cost));
        when(histories.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    void invalidChargeIsRejectedBeforeReadingOrWriting(Long amount) {
        assertBadRequest(() -> service.updateCost(new CreateAddPointRequestDTO(amount, "충전")));
        verifyNoInteractions(auth, users, histories);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    void invalidPaymentIsRejectedBeforeReadingOrWriting(Long amount) {
        assertBadRequest(() -> new PointHistoryService(histories, auth, users, products, details, PaymentFixture.gate(), PaymentFixture.settlements()).payment(new PaymentPointRequestDto(amount, 42L)));
        verifyNoInteractions(auth, users, histories);
    }

    @Test
    void chargeLocksUserThenReadsCurrentBalanceThenAppends() {
        balance(1000L);
        service.updateCost(new CreateAddPointRequestDTO(200L, "충전"));
        assertWriteOrder(false);
    }

    @Test
    void paymentLocksUserThenReadsCurrentBalanceThenAppends() {
        balance(1000L);
        new PointHistoryService(histories, auth, users, products, details, PaymentFixture.gate(), PaymentFixture.settlements()).payment(new PaymentPointRequestDto(200L, 42L));
        assertWriteOrder(true);
    }

    @Test
    void paymentOfEntireBalanceSucceedsWithZeroRemaining() {
        balance(Long.MAX_VALUE);
        PaymentFixture.allowPayment(products, details, 7L, Long.MAX_VALUE);
        PointHistory result = new PointHistoryService(histories, auth, users, products, details, PaymentFixture.gate(), PaymentFixture.settlements()).payment(new PaymentPointRequestDto(Long.MAX_VALUE, 42L));
        assertThat(result.getBeforeCost()).isEqualTo(Long.MAX_VALUE);
        assertThat(result.getAddCost()).isEqualTo(Long.MAX_VALUE);
        assertThat(result.getCost()).isZero();
    }

    @Test
    void insufficientBalanceIsRejectedWithoutAppendingEvenAfterSuccessfulCheck() {
        balance(20L);
        PaymentFixture.allowPayment(products, details, 7L, 80L);
        when(histories.findLastByUserId(7L)).thenReturn(Optional.of(
                PointHistory.builder().user(user).cost(100L).build()));
        assertThat(service.isPaymnet(80L)).isTrue();
        assertBadRequest(() -> new PointHistoryService(histories, auth, users, products, details, PaymentFixture.gate(), PaymentFixture.settlements()).payment(new PaymentPointRequestDto(80L, 42L)));
        verify(users).lockUserIdForPointUpdate(7L);
        verify(histories).findLatestCostForUpdate(7L);
        verify(histories, never()).save(any());
    }

    @Test
    void overflowingChargeIsRejectedWithoutAppending() {
        balance(Long.MAX_VALUE);
        assertBadRequest(() -> service.updateCost(new CreateAddPointRequestDTO(1L, "충전")));
        verify(histories, never()).save(any());
    }

    @Test
    void chargeCanReachMaximumSupportedBalanceExactly() {
        balance(Long.MAX_VALUE - 1);
        assertThat(service.updateCost(new CreateAddPointRequestDTO(1L, "충전")).getCost())
                .isEqualTo(Long.MAX_VALUE);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    void checkReturnsFalseForNonpositiveAmountWithoutAppending(Long amount) {
        assertThat(service.isPaymnet(amount)).isFalse();
        verifyNoInteractions(auth, users, histories);
    }

    @Test
    void checkRejectsNullAmount() {
        assertBadRequest(() -> service.isPaymnet(null));
        verifyNoInteractions(auth, users, histories);
    }

    @ParameterizedTest
    @ValueSource(longs = {99, 100, 101})
    void checkAllowsEqualBalanceButDoesNotReserveIt(Long amount) {
        when(auth.readAuthUser()).thenReturn(user);
        when(histories.findLastByUserId(7L)).thenReturn(Optional.of(
                PointHistory.builder().user(user).cost(100L).build()));
        assertThat(service.isPaymnet(amount)).isEqualTo(amount <= 100);
        verifyNoInteractions(users);
        verify(histories, never()).save(any());
        verify(histories, never()).findLatestCostForUpdate(any());
    }

    @Test
    void successfulPaymentCannotBeDebitedAgainAfterStatusChanges() {
        balance(1000L);
        service = new PointHistoryService(histories, auth, users, products, details, PaymentFixture.gate(), PaymentFixture.settlements());
        assertThat(service.payment(new PaymentPointRequestDto(200L, 42L)).getCost()).isEqualTo(800L);
        when(details.lockPaymentTransactions(42L)).thenReturn(java.util.List.of(new PaymentFixture.TransactionRow(88L, 7L, 200L, 42L, 999L, 2L)));
        assertThatThrownBy(() -> service.payment(new PaymentPointRequestDto(200L, 42L)))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatusCode().value()).isEqualTo(409));
        verify(histories).save(any());
    }

    @Test
    void missingHistoryIsNotReplacedWithAnInitialBalance() {
        balance(100L);
        PaymentFixture.allowPayment(products, details, 7L, 1L);
        when(histories.findLatestCostForUpdate(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> new PointHistoryService(histories, auth, users, products, details, PaymentFixture.gate(), PaymentFixture.settlements()).payment(new PaymentPointRequestDto(1L, 42L)))
                .isInstanceOf(NoSuchElementException.class);
        verify(histories, never()).save(any());
    }

    @Test
    void missingUserLockPreventsHistoryAccess() {
        when(auth.readAuthUser()).thenReturn(user);
        when(users.lockUserIdForPointUpdate(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateCost(new CreateAddPointRequestDTO(1L, "충전")))
                .isInstanceOf(NoSuchElementException.class);
        verifyNoInteractions(histories);
    }

    @Test
    void springAdviceKeepsBothWriteMethodsInsideATransaction() {
        balance(1000L);
        PaymentFixture.allowPayment(products, details, 7L, 1L);
        RecordingTransactions transactions = new RecordingTransactions();
        PointHistoryService proxy = proxy(transactions);
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return Optional.of(7L);
        }).when(users).lockUserIdForPointUpdate(7L);
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return Optional.of(1000L);
        }).when(histories).findLatestCostForUpdate(7L);
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return invocation.getArgument(0);
        }).when(histories).save(any());

        proxy.updateCost(new CreateAddPointRequestDTO(1L, "충전"));
        proxy.payment(new PaymentPointRequestDto(1L, 42L));
        assertThat(transactions.commits).isEqualTo(2);
        assertThat(transactions.rollbacks).isZero();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    @Test
    void saveFailureEscapesProxyAndInvokesRollbackAdvice() {
        balance(1000L);
        PaymentFixture.allowPayment(products, details, 7L, 1L);
        RuntimeException failure = new RuntimeException("save failed");
        when(histories.save(any())).thenThrow(failure);
        RecordingTransactions transactions = new RecordingTransactions();
        assertThatThrownBy(() -> proxy(transactions).payment(new PaymentPointRequestDto(1L, 42L)))
                .isSameAs(failure);
        assertThat(transactions.commits).isZero();
        assertThat(transactions.rollbacks).isEqualTo(1);
    }

    @Test
    void commitFailureIsNotReturnedAsSuccessfulPayment() {
        balance(1000L);
        PaymentFixture.allowPayment(products, details, 7L, 1L);
        RecordingTransactions transactions = new RecordingTransactions();
        transactions.failCommit = true;
        assertThatThrownBy(() -> proxy(transactions).payment(new PaymentPointRequestDto(1L, 42L)))
                .isInstanceOf(UnexpectedRollbackException.class);
    }

    private void assertWriteOrder(boolean payment) {
        InOrder order = inOrder(auth, users, histories);
        order.verify(auth).readAuthUser();
        order.verify(users).lockUserIdForPointUpdate(7L);
        order.verify(histories).findLatestCostForUpdate(7L);
        order.verify(histories).save(any());
        if (payment) order.verify(histories).flush();
        order.verifyNoMoreInteractions();
    }

    private void assertBadRequest(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private PointHistoryService proxy(RecordingTransactions transactions) {
        ProxyFactory factory = new ProxyFactory(new PointHistoryService(histories, auth, users, products, details, PaymentFixture.gate(), PaymentFixture.settlements()));
        factory.setProxyTargetClass(true);
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactions);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        factory.addAdvice(interceptor);
        return (PointHistoryService) factory.getProxy();
    }

    private static class RecordingTransactions extends AbstractPlatformTransactionManager {
        int commits;
        int rollbacks;
        boolean failCommit;

        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) {
            if (failCommit) throw new UnexpectedRollbackException("test commit failed");
            commits++;
        }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
    }
}
