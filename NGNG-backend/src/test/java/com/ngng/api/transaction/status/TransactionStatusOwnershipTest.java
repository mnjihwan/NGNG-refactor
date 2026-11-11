package com.ngng.api.transaction.status;

import com.ngng.api.global.security.custom.CustomUserDetails;
import com.ngng.api.product.product.repository.ProductRepository;
import com.ngng.api.transaction.dto.UpdateTransactionDetailsRequestDTO;
import com.ngng.api.transaction.entity.*;
import com.ngng.api.transaction.repository.*;
import com.ngng.api.transaction.service.TransactionDetailsService;
import com.ngng.api.user.user.entity.User;
import com.ngng.api.user.user.service.AuthService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** RF038 policy, advice and collaborator order only. No physical locks/flush/rollback. */
class TransactionStatusOwnershipTest {
    final TransactionDetailsRepository details = mock(TransactionDetailsRepository.class);
    final TransactionStatusRepository statuses = mock(TransactionStatusRepository.class);
    final ProductRepository products = mock(ProductRepository.class);
    final AuthService auth = mock(AuthService.class);
    final TransactionDetailsService service = new TransactionDetailsService(details, statuses, products, auth);
    TransactionDetails transaction;
    TransactionDetailsRepository.StatusTransaction current;
    @BeforeEach void setup() { fixture(2L); caller(1000L); }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    void fixture(Long status) {
        transaction = spy(Rf038Fixture.graph(status));
        current = mock(TransactionDetailsRepository.StatusTransaction.class);
        when(current.getTransactionId()).thenReturn(88L);
        when(current.getProductId()).thenReturn(7L);
        when(current.getConsumerId()).thenReturn(2000L);
        when(current.getSellerId()).thenReturn(1000L);
        when(current.getStatusId()).thenReturn(status);
        when(current.getPrice()).thenReturn(300L);
        when(current.getAddress()).thenReturn("synthetic-address");
        when(details.findStatusProductId(88L)).thenReturn(Optional.of(7L));
        when(details.lockStatusTransaction(88L)).thenReturn(Optional.of(current));
        when(details.findById(88L)).thenReturn(Optional.of(transaction));
        var product = mock(ProductRepository.TransactionProduct.class);
        when(product.getProductId()).thenReturn(7L);
        when(products.lockTransactionProduct(7L)).thenReturn(Optional.of(product));
        when(statuses.findById(any())).thenAnswer(call -> {
            Long id = call.getArgument(0);
            return id == null || id == 99L ? Optional.empty() : Optional.of(new TransactionStatus(id, "synthetic-status-" + id));
        });
    }
    void caller(Long id) {
        User user = Rf038Fixture.user(id);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CustomUserDetails(user), "unused", List.of()));
        when(auth.readAuthUser()).thenReturn(user);
    }
    void refused(int status, Long target) {
        assertEquals(status, assertThrows(ResponseStatusException.class,
                () -> service.updateTransactionStatus(88L, new UpdateTransactionDetailsRequestDTO(target))).getStatusCode().value());
        verify(transaction, never()).setStatus(any());
        verify(details, never()).save(any());
        verify(details, never()).updateTransactionStatus(any(), any());
    }
    static Stream<Arguments> matrix() {
        return Stream.of(1L,2L,3L,4L,5L,6L,7L,8L,100L).flatMap(from ->
                Stream.of(1L,2L,3L,4L,5L,6L,7L,8L).flatMap(to ->
                        Stream.of(1000L,2000L).map(actor -> Arguments.of(from,to,actor))));
    }
    @ParameterizedTest @MethodSource("matrix")
    void completeNormalExceptionalAndUnknownStateMatrix(Long from, Long to, Long actor) {
        fixture(from); caller(actor);
        boolean same = from.equals(to) && to <= 7;
        boolean exceptional = to == 6 || to == 7;
        boolean sellerAction = (from == 2 && to == 3) || (from == 3 && to == 4);
        boolean buyerAction = from == 4 && to == 5;
        int expected = same || exceptional ? 200 : sellerAction ? (actor == 1000 ? 200 : 403)
                : buyerAction ? (actor == 2000 ? 200 : 403) : 409;
        if (expected != 200) { refused(expected, to); verify(details, never()).findById(any()); return; }
        var beforeUpdatedAt = transaction.getUpdatedAt();
        var response = service.updateTransactionStatus(88L, new UpdateTransactionDetailsRequestDTO(to));
        assertEquals(to, response.getStatus().getId()); assertEquals(88L, response.getId());
        assertEquals(beforeUpdatedAt, transaction.getUpdatedAt());
        verify(transaction, times(same ? 0 : 1)).setStatus(any());
        verify(details, never()).save(any());
        verify(details, never()).updateTransactionStatus(any(), any());
    }
    @ParameterizedTest @ValueSource(longs = {3000,4000})
    void currentProductOwnerAndThirdPartyAreNotRecordedParties(long actor) {
        caller(actor); refused(401, 3L); verifyNoInteractions(statuses);
    }
    @ParameterizedTest @ValueSource(strings = {"seller", "consumer", "status", "transactionId", "productId"})
    void incompleteCurrentScalarsFailClosedBeforeEntityOrStatusLookup(String field) {
        switch(field) {
            case "seller" -> when(current.getSellerId()).thenReturn(null);
            case "consumer" -> when(current.getConsumerId()).thenReturn(null);
            case "status" -> when(current.getStatusId()).thenReturn(null);
            case "transactionId" -> when(current.getTransactionId()).thenReturn(null);
            case "productId" -> when(current.getProductId()).thenReturn(null);
        }
        refused(409, 6L); verify(details, never()).findById(any()); verifyNoInteractions(statuses);
    }
    @ParameterizedTest @ValueSource(strings = {"status", "seller", "consumer", "product", "id", "price", "address", "nullSeller", "nullConsumer", "nullProduct", "nullStatus"})
    void staleOrIncompleteManagedEntityCannotOverrideCurrentScalarDecisionOrReturnStaleDto(String field) {
        switch(field) {
            case "status" -> transaction.setStatus(new TransactionStatus(1L));
            case "seller" -> transaction.setSeller(Rf038Fixture.user(3000L));
            case "consumer" -> transaction.setConsumer(Rf038Fixture.user(4000L));
            case "product" -> transaction.getProduct().setProductId(9L);
            case "id" -> transaction.setId(89L);
            case "price" -> transaction.setPrice(1L);
            case "address" -> transaction.setAddress("stale-address");
            case "nullSeller" -> transaction.setSeller(null);
            case "nullConsumer" -> transaction.setConsumer(null);
            case "nullProduct" -> transaction.setProduct(null);
            case "nullStatus" -> transaction.setStatus(null);
        }
        clearInvocations(transaction);
        refused(409, 3L);
    }
    @Test void changedProductBetweenIdentificationAndCurrentLockIsRejected() {
        when(current.getProductId()).thenReturn(9L); refused(409, 6L);
    }
    @Test void productLockDoesNotMatchRequestedProductIsRejected() {
        var product = mock(ProductRepository.TransactionProduct.class);
        when(product.getProductId()).thenReturn(9L);
        when(products.lockTransactionProduct(7L)).thenReturn(Optional.of(product));
        refused(409, 6L); verify(details, never()).lockStatusTransaction(any());
    }
    @Test void locksAndCurrentValidationPrecedeFirstEntityReadAndSetter() {
        service.updateTransactionStatus(88L, new UpdateTransactionDetailsRequestDTO(3L));
        var order = inOrder(auth, details, products, statuses, transaction);
        order.verify(auth).readAuthUser(); order.verify(details).findStatusProductId(88L);
        order.verify(products).lockTransactionProduct(7L); order.verify(details).lockStatusTransaction(88L);
        order.verify(statuses).findById(3L); order.verify(details).findById(88L); order.verify(transaction).setStatus(any());
    }
    @ParameterizedTest @ValueSource(strings = {"absent", "unauthenticated", "wrongPrincipal", "nullUser", "nullEmail", "blankEmail"})
    void invalidServiceAuthenticationFailsBeforeRepositoryAccess(String scenario) {
        switch (scenario) {
            case "absent" -> SecurityContextHolder.clearContext();
            case "unauthenticated" -> SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new CustomUserDetails(Rf038Fixture.user(1000L)), "unused"));
            case "wrongPrincipal" -> SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("wrong", "unused", List.of()));
            case "nullUser" -> SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new CustomUserDetails(null), "unused", List.of()));
            case "nullEmail" -> caller(1000L);
            case "blankEmail" -> caller(1000L);
        }
        if (scenario.equals("nullEmail") || scenario.equals("blankEmail")) {
            var user = ((CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getUser();
            user.setEmail(scenario.equals("nullEmail") ? null : " ");
        }
        refused(401, 3L); verifyNoInteractions(details, products, statuses, auth);
    }
    @ParameterizedTest @ValueSource(booleans = {true,false})
    void missingDatabaseUserOrIdIsUnauthorized(boolean absent) {
        when(auth.readAuthUser()).thenReturn(absent ? null : Rf038Fixture.user(null));
        refused(401, 3L); verifyNoInteractions(details, products, statuses);
    }
    @Test void missingTransactionRetainsNoSuchElementBoundary() {
        when(details.findStatusProductId(88L)).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> service.updateTransactionStatus(88L, new UpdateTransactionDetailsRequestDTO(3L)));
        verifyNoInteractions(products, statuses);
    }
    @Test void removedTransactionAfterProductLockRetainsNoSuchElementBoundary() {
        when(details.lockStatusTransaction(88L)).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> service.updateTransactionStatus(88L, new UpdateTransactionDetailsRequestDTO(3L)));
        verify(details, never()).findById(any());
    }
    @ParameterizedTest @NullSource @ValueSource(longs = {99})
    void missingTargetRetainsNoSuchElementBoundaryWithoutMutation(Long id) {
        assertThrows(NoSuchElementException.class, () -> service.updateTransactionStatus(88L, new UpdateTransactionDetailsRequestDTO(id)));
        verify(transaction, never()).setStatus(any());
    }
    @Test void lockFailureIsPropagatedAndNoEntityIsLoaded() {
        var failure = new IllegalStateException("synthetic lock failure");
        when(products.lockTransactionProduct(7L)).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> service.updateTransactionStatus(88L, new UpdateTransactionDetailsRequestDTO(3L))));
        verify(details, never()).findById(any());
    }
    @SuppressWarnings("removal") @ParameterizedTest
    @CsvSource({"true,7", "false,7", "true,1000", "false,1000", "true,9223372036854775807", "false,9223372036854775807"})
    void recordedPartyIdsUseValueEqualityAcrossDistinctLongInstances(boolean seller, long id) {
        fixture(seller ? 2L : 4L);
        Long stored = new Long(id), entityId = new Long(id), callerId = new Long(id);
        assertNotSame(stored,callerId); assertNotSame(entityId,callerId); assertEquals(stored,callerId);
        if (seller) { when(current.getSellerId()).thenReturn(stored); transaction.setSeller(Rf038Fixture.user(entityId)); }
        else { when(current.getConsumerId()).thenReturn(stored); transaction.setConsumer(Rf038Fixture.user(entityId)); }
        caller(callerId);
        Long target = seller ? 3L : 5L;
        var response = service.updateTransactionStatus(88L, new UpdateTransactionDetailsRequestDTO(target));
        assertEquals(target,response.getStatus().getId());
    }
    @Test void cachedSmallPartyIdRetainsValueComparisonAndNoOwnerDependency() {
        Long id = Long.valueOf(7); assertSame(id,Long.valueOf(7));
        when(current.getSellerId()).thenReturn(id); transaction.setSeller(Rf038Fixture.user(id)); caller(id);
        assertEquals(3L,service.updateTransactionStatus(88L,new UpdateTransactionDetailsRequestDTO(3L)).getStatus().getId());
    }
    @Test void proxyStartsReadCommittedWriteTransactionBeforeFirstLockAndCommits() {
        var manager = new RecordingTransactions();
        when(products.lockTransactionProduct(7L)).thenAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            var locked = mock(ProductRepository.TransactionProduct.class); when(locked.getProductId()).thenReturn(7L);
            return Optional.of(locked);
        });
        proxy(manager).updateTransactionStatus(88L, new UpdateTransactionDetailsRequestDTO(3L));
        assertEquals(TransactionDefinition.ISOLATION_READ_COMMITTED, manager.isolation);
        assertFalse(manager.readOnly); assertEquals(1, manager.commits); assertEquals(0, manager.rollbacks);
    }
    @Test void setterFailureInvokesRollbackAdviceAndNeverReturnsSuccess() {
        var manager = new RecordingTransactions(); var failure = new IllegalStateException("synthetic setter failure");
        doThrow(failure).when(transaction).setStatus(any());
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> proxy(manager).updateTransactionStatus(88L, new UpdateTransactionDetailsRequestDTO(3L))));
        assertEquals(0, manager.commits); assertEquals(1, manager.rollbacks);
    }
    @Test void commitFailureIsPropagatedInsteadOfReturningDto() {
        var manager = new RecordingTransactions(); manager.failCommit = true;
        assertThrows(UnexpectedRollbackException.class,
                () -> proxy(manager).updateTransactionStatus(88L, new UpdateTransactionDetailsRequestDTO(3L)));
    }
    @ParameterizedTest @CsvSource({"2,false", "4,false", "2,true", "4,true"})
    void adviceOnlyRequiredJoinsOuterIsolationAndStaleFixtureIsRejectedWithoutClaimingRealFlushSafety(int isolation, boolean stale) {
        var manager = new JoiningTransactions();
        var outer = new TransactionTemplate(manager); outer.setIsolationLevel(isolation);
        Runnable operation = () -> outer.executeWithoutResult(ignored -> {
            assertEquals(isolation, TransactionSynchronizationManager.getCurrentTransactionIsolationLevel());
            if (stale) { transaction.setPrice(1L); clearInvocations(transaction); }
            proxy(manager).updateTransactionStatus(88L, new UpdateTransactionDetailsRequestDTO(3L));
            assertEquals(isolation, TransactionSynchronizationManager.getCurrentTransactionIsolationLevel());
        });
        if (stale) {
            assertEquals(409, assertThrows(ResponseStatusException.class, operation::run).getStatusCode().value());
            verify(transaction,never()).setStatus(any());
            assertEquals(1, manager.rollbacks);
        } else { operation.run(); assertEquals(1, manager.commits); }
        assertEquals(1, manager.begins);
    }
    TransactionDetailsService proxy(RecordingTransactions manager) {
        var factory = new ProxyFactory(service); factory.setProxyTargetClass(true);
        var advice = new TransactionInterceptor(); advice.setTransactionManager(manager);
        advice.setTransactionAttributeSource(new AnnotationTransactionAttributeSource()); factory.addAdvice(advice);
        return (TransactionDetailsService) factory.getProxy();
    }
    static class RecordingTransactions extends AbstractPlatformTransactionManager {
        int isolation, commits, rollbacks; boolean readOnly, failCommit;
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object tx, TransactionDefinition definition) { isolation = definition.getIsolationLevel(); readOnly = definition.isReadOnly(); }
        @Override protected void doCommit(DefaultTransactionStatus status) { if (failCommit) throw new UnexpectedRollbackException("synthetic commit failure"); commits++; }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
    }
    static class JoiningTransactions extends RecordingTransactions {
        final Tx tx = new Tx(); int begins;
        static class Tx { boolean active; }
        @Override protected Object doGetTransaction() { return tx; }
        @Override protected boolean isExistingTransaction(Object transaction) { return ((Tx) transaction).active; }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { super.doBegin(transaction,definition); tx.active = true; begins++; }
        @Override protected void doCleanupAfterCompletion(Object transaction) { tx.active = false; }
        @Override protected void doSetRollbackOnly(DefaultTransactionStatus status) { }
    }
}
