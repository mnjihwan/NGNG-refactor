package com.ngng.api.transaction.authorization;

import com.ngng.api.global.security.custom.CustomUserDetails;
import com.ngng.api.product.product.entity.Product;
import com.ngng.api.product.product.repository.ProductRepository;
import com.ngng.api.product.product.service.ProductCacheInvalidator;
import com.ngng.api.transaction.dto.CreateTransactionRequestDTO;
import com.ngng.api.transaction.entity.TransactionRequest;
import com.ngng.api.transaction.repository.TransactionRequestRepository;
import com.ngng.api.transaction.service.*;
import com.ngng.api.user.user.entity.User;
import com.ngng.api.user.user.service.AuthService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.security.authentication.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Advice/order and in-memory state only: no physical transaction or locking claims. */
class TransactionRequestAuthorizationServiceTest {
    final TransactionRequestRepository requests = mock(TransactionRequestRepository.class);
    final ProductRepository products = mock(ProductRepository.class);
    final TransactionDetailsService details = mock(TransactionDetailsService.class);
    final ProductCacheInvalidator cache = mock(ProductCacheInvalidator.class);
    final AuthService auth = mock(AuthService.class);
    final TransactionRequestService service = new TransactionRequestService(requests, products, details, cache, auth);
    final User owner = User.builder().userId(1000L).email("owner@example.test").build();
    final User buyer = User.builder().userId(2000L).email("buyer@example.test").build();
    final Product product = Product.builder().productId(7000L).user(owner).forSale(true).build();
    final TransactionRequest request = TransactionRequest.builder().transactionRequestId(9000L).product(product).buyer(buyer).build();
    final ProductRepository.TransactionProduct locked = mock(ProductRepository.TransactionProduct.class);
    @BeforeEach void setup() {
        caller(buyer);
        when(locked.getSellerId()).thenReturn(1000L);
        when(products.lockTransactionProduct(7000L)).thenReturn(Optional.of(locked));
        when(products.findById(7000L)).thenReturn(Optional.of(product));
        when(requests.findProductIdForUpdate(9000L)).thenReturn(Optional.of(7000L));
        when(requests.findById(9000L)).thenReturn(Optional.of(request));
        when(requests.save(any())).thenAnswer(call -> {
            TransactionRequest saved = call.getArgument(0); saved.setTransactionRequestId(9000L); return saved;
        });
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    void caller(User user) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CustomUserDetails(user), "unused", List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        when(auth.readAuthUser()).thenReturn(user);
    }
    CreateTransactionRequestDTO input(Long buyer, Long seller) { return new CreateTransactionRequestDTO(7000L, seller, buyer, 300L); }
    void refused(int status, Runnable action) {
        assertEquals(status, assertThrows(ResponseStatusException.class, action::run).getStatusCode().value());
        assertTrue(product.getForSale()); assertNull(request.getIsAccepted());
        verify(requests, never()).save(any()); verify(products, never()).save(any());
        verifyNoInteractions(details, cache);
    }
    @ParameterizedTest @ValueSource(strings = {"absent", "anonymous", "unauthenticated", "wrongPrincipal", "nullUser", "nullEmail", "blankEmail"})
    void invalidServiceAuthenticationFailsClosedBeforeAnyLookup(String scenario) {
        var context = SecurityContextHolder.getContext();
        switch (scenario) {
            case "absent" -> context.setAuthentication(null);
            case "anonymous" -> context.setAuthentication(new AnonymousAuthenticationToken("key", "anonymous", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
            case "unauthenticated" -> context.setAuthentication(new UsernamePasswordAuthenticationToken(new CustomUserDetails(buyer), "unused"));
            case "wrongPrincipal" -> context.setAuthentication(new UsernamePasswordAuthenticationToken("caller", "unused", List.of()));
            case "nullUser" -> context.setAuthentication(new UsernamePasswordAuthenticationToken(new CustomUserDetails(null), "unused", List.of()));
            case "nullEmail" -> caller(User.builder().userId(2000L).build());
            case "blankEmail" -> caller(User.builder().userId(2000L).email(" ").build());
        }
        refused(401, () -> service.create(input(2000L,1000L)));
        refused(401, () -> service.update(9000L, true));
        verifyNoInteractions(auth, requests, products);
    }
    @ParameterizedTest @ValueSource(booleans = {true, false})
    void missingOrNullDatabaseIdentityNeverUsesPrincipalId(boolean absent) {
        when(auth.readAuthUser()).thenReturn(absent ? null : User.builder().email("fixture@example.test").build());
        refused(401, () -> service.create(input(2000L,1000L)));
        refused(401, () -> service.update(9000L, true));
        verifyNoInteractions(requests, products);
    }
    @ParameterizedTest @NullSource @ValueSource(longs = {1000, 4000})
    void bodyBuyerMismatchStopsBeforeProductAndDuplicateLookup(Long buyerId) {
        refused(403, () -> service.create(input(buyerId,1000L)));
        verifyNoInteractions(requests, products);
    }
    @ParameterizedTest @NullSource @ValueSource(longs = {2000, 4000})
    void bodySellerMismatchStopsBeforeEntityAndDuplicateLookup(Long sellerId) {
        refused(403, () -> service.create(input(2000L,sellerId)));
        verify(products, never()).findById(any()); verifyNoInteractions(requests);
    }
    @Test void nullLockOwnerDoesNotAuthorizeNullSeller() {
        when(locked.getSellerId()).thenReturn(null);
        refused(403, () -> service.create(input(2000L,null)));
        caller(owner); refused(403, () -> service.update(9000L, false));
        verify(requests).findProductIdForUpdate(9000L);
        verifyNoMoreInteractions(requests);
    }
    @ParameterizedTest @ValueSource(booleans = {true, false})
    void absentEntityOwnerCannotAuthorizeCreateOrUpdate(boolean missingOwner) {
        product.setUser(missingOwner ? null : User.builder().build());
        refused(403, () -> service.create(input(2000L,1000L)));
        caller(owner); refused(403, () -> service.update(9000L,true));
        verify(requests, never()).findByProductProductIdAndBuyerUserId(any(),any());
    }
    @Test void projectionAndEntityOwnerMismatchFailsClosed() {
        product.setUser(User.builder().userId(4000L).build());
        refused(403, () -> service.create(input(2000L,1000L)));
        caller(owner); refused(403, () -> service.update(9000L,true));
    }
    @Test void creationLocksBeforeEntityThenChecksDuplicateAndStoresServerUsers() {
        assertEquals(9000L,service.create(input(2000L,1000L)));
        var order = inOrder(auth,products,requests);
        order.verify(auth).readAuthUser(); order.verify(products).lockTransactionProduct(7000L);
        order.verify(products).findById(7000L);
        order.verify(requests).findByProductProductIdAndBuyerUserId(7000L,2000L);
        order.verify(requests).save(argThat(saved -> saved.getProduct()==product && saved.getBuyer()==buyer
                && saved.getSeller()==owner && saved.getPrice().equals(300L)));
        verifyNoInteractions(details,cache);
    }
    @Test void createRetainsOwnProductAndPricePolicies() {
        caller(owner);
        assertEquals(9000L,service.create(new CreateTransactionRequestDTO(7000L,1000L,1000L,-1L)));
        verify(requests).save(argThat(saved -> saved.getPrice().equals(-1L)));
    }
    @Test void createUsesReadCommittedWriteAdviceBeforeLock() {
        var manager = new RecordingTransactions();
        when(products.lockTransactionProduct(7000L)).thenAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(TransactionDefinition.ISOLATION_READ_COMMITTED, manager.isolation);
            assertFalse(manager.readOnly); return Optional.of(locked);
        });
        assertEquals(9000L,proxy(manager).create(input(2000L,1000L)));
        assertEquals(1,manager.commits); assertEquals(0,manager.rollbacks);
    }
    @Test void creationSaveFailurePropagatesAndTriggersRollbackAdvice() {
        var manager = new RecordingTransactions();
        var failure = new IllegalStateException("fixture save failure");
        doThrow(failure).when(requests).save(any());
        assertSame(failure,assertThrows(IllegalStateException.class, () -> proxy(manager).create(input(2000L,1000L))));
        assertEquals(0,manager.commits); assertEquals(1,manager.rollbacks);
        verifyNoInteractions(details,cache);
    }
    TransactionRequestService proxy(RecordingTransactions manager) {
        var factory = new ProxyFactory(service); factory.setProxyTargetClass(true);
        var advice = new TransactionInterceptor(); advice.setTransactionManager(manager);
        advice.setTransactionAttributeSource(new AnnotationTransactionAttributeSource()); factory.addAdvice(advice);
        return (TransactionRequestService)factory.getProxy();
    }
    static class RecordingTransactions extends AbstractPlatformTransactionManager {
        int commits,rollbacks,isolation; boolean readOnly;
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object tx,TransactionDefinition definition) { isolation=definition.getIsolationLevel(); readOnly=definition.isReadOnly(); }
        @Override protected void doCommit(DefaultTransactionStatus status) { commits++; }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
    }
}
