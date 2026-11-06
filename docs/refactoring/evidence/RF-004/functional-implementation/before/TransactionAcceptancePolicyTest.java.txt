package com.ngng.api.transaction.service;

import com.ngng.api.product.product.repository.ProductRepository;
import com.ngng.api.transaction.repository.TransactionDetailsRepository;
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
import java.sql.Timestamp;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** Business decisions and collaborator order only; real locking/rollback requires MySQL. */
class TransactionAcceptancePolicyTest {
    TransactionRequestRepository requests = mock(TransactionRequestRepository.class);
    ProductRepository products = mock(ProductRepository.class);
    TransactionDetailsService creation = mock(TransactionDetailsService.class);
    TransactionRequestService service = new TransactionRequestService(requests, products, creation);
    TransactionRequestRepository.AcceptanceState state = mock(TransactionRequestRepository.AcceptanceState.class);
    ProductRepository.TransactionProduct product = mock(ProductRepository.TransactionProduct.class);
    TransactionDetailsRepository.AcceptanceDetails existing = mock(TransactionDetailsRepository.AcceptanceDetails.class);

    @BeforeEach void setup() {
        when(requests.findProductIdForUpdate(1L)).thenReturn(Optional.of(7L));
        when(products.lockTransactionProduct(7L)).thenReturn(Optional.of(product));
        when(product.getSellerId()).thenReturn(12L);
        when(requests.lockAcceptanceState(1L)).thenReturn(Optional.of(state));
        when(state.getProductId()).thenReturn(7L);
        when(state.getBuyerId()).thenReturn(11L);
        when(state.getPrice()).thenReturn(300L);
        when(state.getAccepted()).thenReturn(null);
        when(creation.findCurrentDetails(7L)).thenReturn(List.of());
        when(requests.findVisibleIdsByProductId(7L)).thenReturn(List.of(1L, 2L));
        when(requests.findCommittedIdsByProductId(7L)).thenReturn(List.of(1L, 3L));
        when(existing.getBuyerId()).thenReturn(11L);
        when(existing.getSellerId()).thenReturn(12L);
        when(existing.getPrice()).thenReturn(300L);
    }

    void noWrites() {
        verify(products, never()).markTransactionSold(anyLong(), any());
        verify(requests, never()).updateAcceptance(anyLong(), anyBoolean(), any());
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
        order.verify(requests).flush();
        order.verify(requests).lockAcceptanceState(1L);
        order.verify(creation).findCurrentDetails(7L);
        order.verify(products).markTransactionSold(eq(7L), any(Timestamp.class));
        order.verify(creation).create(argThat(dto -> dto.getProductId().equals(7L)
                && dto.getBuyerId().equals(11L) && dto.getPrice().equals(300L)));
        order.verify(requests).updateAcceptance(eq(1L), eq(true), any(Timestamp.class));
        order.verify(requests).updateAcceptance(eq(2L), eq(false), any(Timestamp.class));
        order.verify(requests).updateAcceptance(eq(3L), eq(false), any(Timestamp.class));
    }

    @Test void sameAcceptedRequestWithMatchingDetailsIsReadOnlySuccess() {
        when(state.getAccepted()).thenReturn(true);
        when(creation.findCurrentDetails(7L)).thenReturn(List.of(existing));
        assertEquals(1L, service.update(1L, true));
        noWrites();
    }

    @Test void acceptedRequestWithoutDetailsConflicts() { when(state.getAccepted()).thenReturn(true); conflict(true); }
    @Test void acceptedRequestWithDifferentBuyerConflicts() {
        when(state.getAccepted()).thenReturn(true);
        when(creation.findCurrentDetails(7L)).thenReturn(List.of(existing));
        when(existing.getBuyerId()).thenReturn(99L);
        conflict(true);
    }
    @Test void acceptedRequestWithDifferentPriceConflicts() {
        when(state.getAccepted()).thenReturn(true);
        when(creation.findCurrentDetails(7L)).thenReturn(List.of(existing));
        when(existing.getPrice()).thenReturn(999L);
        conflict(true);
    }
    @Test void acceptedRequestWithDifferentSellerConflicts() {
        when(state.getAccepted()).thenReturn(true);
        when(creation.findCurrentDetails(7L)).thenReturn(List.of(existing));
        when(existing.getSellerId()).thenReturn(99L);
        conflict(true);
    }
    @Test void multipleExistingDetailsConflictInsteadOfRepair() {
        when(state.getAccepted()).thenReturn(true);
        when(creation.findCurrentDetails(7L)).thenReturn(List.of(existing, existing));
        conflict(true);
    }
    @Test void anotherRequestOrDirectTransactionPreventsAcceptance() {
        when(creation.findCurrentDetails(7L)).thenReturn(List.of(existing));
        conflict(true);
    }
    @Test void rejectionAfterAcceptanceConflicts() { when(state.getAccepted()).thenReturn(true); conflict(false); }
    @Test void firstRejectionOnlyChangesSelectedRequest() {
        assertEquals(1L, service.update(1L, false));
        verify(requests).updateAcceptance(eq(1L), eq(false), any(Timestamp.class));
        verify(products, never()).markTransactionSold(anyLong(), any());
        verifyNoInteractions(creation);
    }
    @Test void repeatedRejectionDoesNotWriteEvenIfAnotherRequestHasTransaction() {
        when(state.getAccepted()).thenReturn(false);
        assertEquals(1L, service.update(1L, false));
        noWrites();
    }
    @Test void rejectedRequestCanStillBeAcceptedWithoutTransaction() {
        when(state.getAccepted()).thenReturn(false);
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
        when(requests.lockAcceptanceState(1L)).thenReturn(Optional.empty());
        assertEquals(-1L, service.update(1L, true));
        noWrites();
    }
    @Test void changedProductDoesNotWriteUnderWrongLock() { when(state.getProductId()).thenReturn(9L); conflict(true); }
    @Test void creationFailureIsPropagatedAndLaterRequestWritesDoNotRun() {
        RuntimeException failure = new RuntimeException("insert failed");
        when(creation.create(any())).thenThrow(failure);
        assertSame(failure, assertThrows(RuntimeException.class, () -> service.update(1L, true)));
        verify(requests, never()).updateAcceptance(anyLong(), anyBoolean(), any());
    }
    @Test void lockFailureIsNotConvertedToConflictOrSuccess() {
        RuntimeException failure = new RuntimeException("lock failed");
        when(products.lockTransactionProduct(7L)).thenThrow(failure);
        assertSame(failure, assertThrows(RuntimeException.class, () -> service.update(1L, true)));
        noWrites();
    }
    @Test void independentReaderFailureIsNotConvertedToConflictOrSuccess() {
        RuntimeException failure = new RuntimeException("reader connection unavailable");
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
            return Optional.of(product);
        });
        assertEquals(1L, proxy(manager).update(1L, true));
        assertEquals(1, manager.commits);
        assertEquals(0, manager.rollbacks);
    }

    @Test void failureAfterRejectingOthersInvokesRollbackAdvice() {
        RecordingTransactions manager = new RecordingTransactions();
        RuntimeException failure = new RuntimeException("reject update failed");
        doThrow(failure).when(requests).updateAcceptance(eq(2L), eq(false), any(Timestamp.class));
        assertSame(failure, assertThrows(RuntimeException.class, () -> proxy(manager).update(1L, true)));
        assertEquals(0, manager.commits);
        assertEquals(1, manager.rollbacks);
    }

    @Test void acceptanceCommitFailureIsNotReturnedAsSuccess() {
        RecordingTransactions manager = new RecordingTransactions();
        manager.failCommit = true;
        assertThrows(UnexpectedRollbackException.class, () -> proxy(manager).update(1L, true));
    }

    @Test void updatesBindVmTimestampsAtExistingMySqlMicrosecondPrecision() {
        Instant before = Instant.now().minusSeconds(1);
        service.update(1L, true);
        Instant after = Instant.now().plusSeconds(1);
        var timestamp = org.mockito.ArgumentCaptor.forClass(Timestamp.class);
        verify(products).markTransactionSold(eq(7L), timestamp.capture());
        verify(requests).updateAcceptance(eq(1L), eq(true), timestamp.capture());
        verify(requests).updateAcceptance(eq(2L), eq(false), timestamp.capture());
        verify(requests).updateAcceptance(eq(3L), eq(false), timestamp.capture());
        for (Timestamp value : timestamp.getAllValues()) {
            assertFalse(value.toInstant().isBefore(before));
            assertFalse(value.toInstant().isAfter(after));
            assertEquals(0, value.getNanos() % 1_000);
        }
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
        boolean failCommit;
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object tx, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) {
            if (failCommit) throw new UnexpectedRollbackException("test commit failed");
            commits++;
        }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
    }
}
