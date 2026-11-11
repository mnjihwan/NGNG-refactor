package com.ngng.api.transaction.service;

import com.ngng.api.product.product.repository.ProductRepository;
import com.ngng.api.product.product.entity.Product;
import com.ngng.api.transaction.entity.TransactionRequest;
import com.ngng.api.transaction.entity.TransactionDetails;
import com.ngng.api.user.user.entity.User;
import com.ngng.api.transaction.repository.TransactionRequestRepository;
import com.ngng.api.transaction.controller.TransactionController;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** Business decisions and collaborator order only; real locking/rollback requires MySQL. */
class TransactionAcceptancePolicyTest {
    TransactionRequestRepository requests = mock(TransactionRequestRepository.class);
    ProductRepository products = mock(ProductRepository.class);
    TransactionDetailsService creation = mock(TransactionDetailsService.class);
    com.ngng.api.user.user.service.AuthService auth = mock(com.ngng.api.user.user.service.AuthService.class);
    TransactionRequestService service = new TransactionRequestService(requests, products, creation, mock(com.ngng.api.product.product.service.ProductCacheInvalidator.class), auth);
    Product product = Product.builder().productId(7L).forSale(true).user(User.builder().userId(12L).build()).build();
    TransactionRequest state = TransactionRequest.builder().transactionRequestId(1L).product(product)
            .buyer(User.builder().userId(11L).build()).price(300L).build();
    TransactionRequest sibling = TransactionRequest.builder().transactionRequestId(2L).product(product).build();
    TransactionRequest rejectedSibling = TransactionRequest.builder().transactionRequestId(3L).product(product).isAccepted(false).build();
    TransactionDetails existing = TransactionDetails.builder().consumer(User.builder().userId(11L).build())
            .seller(product.getUser()).price(300L).build();

    @org.junit.jupiter.api.AfterEach void clearAuthentication() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }
    ProductRepository.TransactionProduct locked() {
        var locked = mock(ProductRepository.TransactionProduct.class);
        when(locked.getSellerId()).thenReturn(12L);
        return locked;
    }
    @BeforeEach void setup() {
        var caller = User.builder().userId(12L).email("rf021-owner@example.test").build();
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        new com.ngng.api.global.security.custom.CustomUserDetails(caller), "unused", java.util.List.of()));
        when(auth.readAuthUser()).thenReturn(caller);
        when(requests.findProductIdForUpdate(1L)).thenReturn(Optional.of(7L));
        var locked = locked();
        when(products.lockTransactionProduct(7L)).thenReturn(Optional.of(locked));
        when(requests.findById(1L)).thenReturn(Optional.of(state));
        when(creation.findCurrentDetails(7L)).thenReturn(List.of());
        when(requests.findAllByProductProductId(7L)).thenReturn(List.of(state, sibling, rejectedSibling));
    }

    void noWrites() {
        verify(products, never()).save(any());
        verify(requests, never()).save(any());
        verify(creation, never()).create(any());
    }

    void conflict(boolean accept) {
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.update(1L, accept)).getStatusCode().value());
        noWrites();
    }

    @Test void firstAcceptancePreservesFieldsAndOrder() {
        assertEquals(1L, service.update(1L, true));
        var order = inOrder(requests, products, creation);
        order.verify(requests).findProductIdForUpdate(1L);
        order.verify(products).lockTransactionProduct(7L);
        order.verify(requests).findById(1L);
        order.verify(creation).findCurrentDetails(7L);
        order.verify(products).save(product);
        order.verify(creation).create(argThat(dto -> dto.getProductId().equals(7L)
                && dto.getBuyerId().equals(11L) && dto.getPrice().equals(300L)));
        order.verify(requests).save(state);
        order.verify(requests).findAllByProductProductId(7L);
        order.verify(requests).save(sibling);
        verify(requests, never()).save(rejectedSibling);
        assertFalse(product.getForSale());
        assertTrue(state.getIsAccepted());
        assertFalse(sibling.getIsAccepted());
    }

    @Test void sameAcceptedRequestWithMatchingDetailsIsReadOnlySuccess() {
        state.setIsAccepted(true);
        when(creation.findCurrentDetails(7L)).thenReturn(List.of(existing));
        assertEquals(1L, service.update(1L, true));
        noWrites();
    }

    @Test void acceptedRequestWithoutDetailsConflicts() { state.setIsAccepted(true); conflict(true); }
    @Test void acceptedRequestWithDifferentBuyerConflicts() {
        state.setIsAccepted(true);
        when(creation.findCurrentDetails(7L)).thenReturn(List.of(existing));
        existing.setConsumer(User.builder().userId(99L).build());
        conflict(true);
    }
    @Test void acceptedRequestWithDifferentPriceConflicts() {
        state.setIsAccepted(true);
        when(creation.findCurrentDetails(7L)).thenReturn(List.of(existing));
        existing.setPrice(999L);
        conflict(true);
    }
    @Test void acceptedRequestWithDifferentSellerConflicts() {
        state.setIsAccepted(true);
        when(creation.findCurrentDetails(7L)).thenReturn(List.of(existing));
        existing.setSeller(User.builder().userId(99L).build());
        conflict(true);
    }
    @Test void acceptedRequestWithMissingBuyerConflicts() {
        state.setIsAccepted(true);
        when(creation.findCurrentDetails(7L)).thenReturn(List.of(existing));
        existing.setConsumer(null);
        conflict(true);
    }
    @Test void acceptedRequestWithMissingSellerConflicts() {
        state.setIsAccepted(true);
        when(creation.findCurrentDetails(7L)).thenReturn(List.of(existing));
        existing.setSeller(null);
        conflict(true);
    }
    @Test void multipleExistingDetailsConflictInsteadOfRepair() {
        state.setIsAccepted(true);
        when(creation.findCurrentDetails(7L)).thenReturn(List.of(existing, existing));
        conflict(true);
    }
    @Test void anotherRequestOrDirectTransactionPreventsAcceptance() {
        when(creation.findCurrentDetails(7L)).thenReturn(List.of(existing));
        conflict(true);
    }
    @Test void rejectionAfterAcceptanceConflicts() { state.setIsAccepted(true); conflict(false); }
    @Test void firstRejectionOnlyChangesSelectedRequest() {
        assertEquals(1L, service.update(1L, false));
        verify(requests).save(state);
        assertFalse(state.getIsAccepted());
        verify(products, never()).save(any());
        verifyNoInteractions(creation);
    }
    @Test void repeatedRejectionDoesNotWriteEvenIfAnotherRequestHasTransaction() {
        state.setIsAccepted(false);
        assertEquals(1L, service.update(1L, false));
        noWrites();
    }
    @Test void rejectedRequestCanStillBeAcceptedWithoutTransaction() {
        state.setIsAccepted(false);
        assertEquals(1L, service.update(1L, true));
        verify(creation).create(any());
    }
    @Test void missingRequestReturnsNegativeOneBeforeLock() {
        when(requests.findProductIdForUpdate(1L)).thenReturn(Optional.empty());
        assertEquals(-1L, service.update(1L, true));
        verifyNoInteractions(products, creation);
    }
    @Test void missingProductReturnsNegativeOne() {
        when(products.lockTransactionProduct(7L)).thenReturn(Optional.empty());
        assertEquals(-1L, service.update(1L, true));
        noWrites();
    }
    @Test void requestRemovedAfterLookupReturnsNegativeOne() {
        when(requests.findById(1L)).thenReturn(Optional.empty());
        assertEquals(-1L, service.update(1L, true));
        noWrites();
    }
    @Test void changedProductDoesNotWriteUnderWrongLock() { state.setProduct(Product.builder().productId(9L).user(product.getUser()).build()); conflict(true); }
    @Test void creationFailureIsPropagatedAndLaterRequestWritesDoNotRun() {
        RuntimeException failure = new RuntimeException("insert failed");
        when(creation.create(any())).thenThrow(failure);
        assertSame(failure, assertThrows(RuntimeException.class, () -> service.update(1L, true)));
        verify(requests, never()).save(any());
    }
    @Test void lockFailureIsNotConvertedToConflictOrSuccess() {
        RuntimeException failure = new RuntimeException("lock failed");
        when(products.lockTransactionProduct(7L)).thenThrow(failure);
        assertSame(failure, assertThrows(RuntimeException.class, () -> service.update(1L, true)));
        noWrites();
    }
    @Test void detailsReadFailureIsNotConvertedToConflictOrSuccess() {
        RuntimeException failure = new RuntimeException("details read failed");
        when(creation.findCurrentDetails(7L)).thenThrow(failure);
        assertSame(failure, assertThrows(RuntimeException.class, () -> service.update(1L, true)));
        noWrites();
    }
    @Test void nullAcceptanceRetainsUnboxingFailureWithoutWriting() {
        assertThrows(NullPointerException.class, () -> service.update(1L, null));
        noWrites();
    }

    @Test void nullRequestIdRetainsExistingIllegalArgumentBoundaryBeforeRepositoryAccess() {
        assertThrows(IllegalArgumentException.class, () -> service.update(null, true));
        verifyNoInteractions(requests, products, creation);
    }

    @Test void mvcMissingRequestIdRetainsUnresolvedIllegalArgumentFailure() {
        var mvc = MockMvcBuilders.standaloneSetup(new TransactionController(creation, service)).build();
        ServletException failure = assertThrows(ServletException.class, () -> mvc.perform(put("/transaction/request")
                .contentType("application/json").content("{\"isAccepted\":true}")));
        assertInstanceOf(IllegalArgumentException.class, failure.getCause());
        verifyNoInteractions(requests, products, creation);
    }

    @Test void acceptanceProxyHasTransactionBeforeFirstLockAndCommits() {
        RecordingTransactions manager = new RecordingTransactions();
        when(products.lockTransactionProduct(7L)).thenAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            return Optional.of(locked());
        });
        assertEquals(1L, proxy(manager).update(1L, true));
        assertEquals(1, manager.commits);
        assertEquals(0, manager.rollbacks);
    }

    @Test void failureAfterRejectingOthersInvokesRollbackAdvice() {
        RecordingTransactions manager = new RecordingTransactions();
        RuntimeException failure = new RuntimeException("reject update failed");
        doThrow(failure).when(requests).save(sibling);
        assertSame(failure, assertThrows(RuntimeException.class, () -> proxy(manager).update(1L, true)));
        assertEquals(0, manager.commits);
        assertEquals(1, manager.rollbacks);
    }

    @Test void acceptanceCommitFailureIsNotReturnedAsSuccess() {
        RecordingTransactions manager = new RecordingTransactions();
        manager.failCommit = true;
        assertThrows(UnexpectedRollbackException.class, () -> proxy(manager).update(1L, true));
    }

    @Test void acceptanceStartsReadCommittedWriteTransaction() {
        RecordingTransactions manager = new RecordingTransactions();
        proxy(manager).update(1L, true);
        assertEquals(TransactionDefinition.ISOLATION_READ_COMMITTED, manager.isolation);
        assertFalse(manager.readOnly);
    }

    private TransactionRequestService proxy(RecordingTransactions manager) {
        ProxyFactory factory = new ProxyFactory(service);
        factory.setProxyTargetClass(true);
        TransactionInterceptor advice = new TransactionInterceptor();
        advice.setTransactionManager(manager);
        advice.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        factory.addAdvice(advice);
        return (TransactionRequestService) factory.getProxy();
    }

    private static class RecordingTransactions extends AbstractPlatformTransactionManager {
        int commits, rollbacks;
        boolean failCommit, readOnly;
        int isolation;
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object tx, TransactionDefinition definition) { isolation = definition.getIsolationLevel(); readOnly = definition.isReadOnly(); }
        @Override protected void doCommit(DefaultTransactionStatus status) {
            if (failCommit) throw new UnexpectedRollbackException("test commit failed");
            commits++;
        }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
    }
}
