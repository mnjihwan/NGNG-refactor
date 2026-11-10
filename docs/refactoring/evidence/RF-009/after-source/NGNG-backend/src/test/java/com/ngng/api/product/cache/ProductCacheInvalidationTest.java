package com.ngng.api.product.cache;

import com.ngng.api.penalty.dto.CreatePenaltyRequestDTO;
import com.ngng.api.penalty.service.PenaltyTran;
import com.ngng.api.product.product.dto.response.ReadProductResponseDTO;
import com.ngng.api.product.product.service.ProductService;
import com.ngng.api.product.product.service.ProductCacheInvalidator;
import com.ngng.api.transaction.entity.TransactionDetails;
import com.ngng.api.transaction.entity.TransactionStatus;
import com.ngng.api.transaction.service.TransactionRequestService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.Advised;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.util.AopTestUtils;
import java.util.*;
import static com.ngng.api.product.cache.ProductCacheTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductCacheInvalidationTest {
    State state;
    AnnotationConfigApplicationContext context;
    ProductService service;
    @BeforeEach void setup() { state = new State(); context = context(state); service = context.getBean(ProductService.class); admin(); }
    @AfterEach void close() { SecurityContextHolder.clearContext(); context.close(); }
    void write(String writer) {
        switch (writer) {
            case "PUT" -> service.update(77L, update());
            case "SALE" -> service.updateForSale(77L, false);
            case "REFRESH" -> service.updateRefresh(77L);
            case "DELETE" -> service.delete(77L);
            case "PENALTY" -> context.getBean(PenaltyTran.class).penaltySaveAndReportUpdate(CreatePenaltyRequestDTO.builder()
                    .penaltyLevelId(1L).reportId(901L).userId(1000L).reporterId(2000L).reason("fixture").build());
            case "ACCEPT" -> context.getBean(TransactionRequestService.class).update(501L, true);
            default -> throw new IllegalArgumentException(writer);
        }
    }
    @ParameterizedTest @ValueSource(strings = {"PUT", "SALE", "REFRESH", "DELETE", "PENALTY", "ACCEPT"})
    void successfulWriterEvictsOnlyProductAfterCommitAndNextReadIsFresh(String writer) {
        var old = service.read(77L);
        state.cache.put(88L, "other fixture");
        var tx = context.getBean(RecordingTransactions.class);
        tx.beforeCommit = () -> assertSame(old, state.cache.get(77L).get(), "cache must survive until commit");
        write(writer);
        assertNull(state.cache.get(77L));
        assertEquals("other fixture", state.cache.get(88L).get());
        tx.beforeCommit = () -> {};
        var fresh = service.read(77L);
        assertNotSame(old, fresh);
        if (writer.equals("REFRESH")) assertNotNull(fresh.getRefreshedAt());
        else assertFalse(fresh.getForSale());
        if (writer.equals("PUT")) assertEquals("changed", fresh.getTitle());
        if (writer.equals("DELETE")) assertFalse(fresh.getVisible());
        if (writer.equals("PENALTY")) assertTrue(fresh.getReports().get(0).getIsReport());
    }
    @ParameterizedTest @ValueSource(strings = {"PUT", "SALE", "REFRESH", "DELETE", "PENALTY", "ACCEPT"})
    void outerTransactionDefersEvictionAndRollbackOnlyDiscardsIt(String writer) {
        var old = service.read(77L);
        var manager = context.getBean(RecordingTransactions.class);
        int beforeRollbacks = manager.rollbacks;
        new TransactionTemplate(manager).executeWithoutResult(tx -> {
            write(writer);
            assertSame(old, state.cache.get(77L).get());
            tx.setRollbackOnly();
        });
        assertSame(old, state.cache.get(77L).get());
        assertEquals(beforeRollbacks + 1, manager.rollbacks);
        verify(state.cache, never()).evict(any());
        // Entity mocks are not a database and are intentionally not asserted as rolled back.
    }
    @ParameterizedTest @ValueSource(strings = {"PUT", "SALE", "REFRESH", "DELETE", "PENALTY", "ACCEPT"})
    void commitFailureDoesNotEvict(String writer) {
        var old = service.read(77L);
        context.getBean(RecordingTransactions.class).failCommit = true;
        assertThrows(UnexpectedRollbackException.class, () -> write(writer));
        assertSame(old, state.cache.get(77L).get());
        verify(state.cache, never()).evict(any());
    }
    @ParameterizedTest @ValueSource(strings = {"PUT", "SALE", "REFRESH", "DELETE", "PENALTY", "ACCEPT"})
    void cacheEvictionFailurePreservesSuccessfulWriteResponse(String writer) {
        var old = service.read(77L);
        var manager = context.getBean(RecordingTransactions.class);
        int commits = manager.commits;
        doAnswer(call -> {
            assertEquals(commits + 1, manager.commits, "write has committed before Redis-like failure");
            throw new IllegalStateException("fixture eviction failure");
        }).when(state.cache).evict(77L);
        assertDoesNotThrow(() -> write(writer));
        assertSame(old, state.cache.get(77L).get(), "failed eviction leaves stale data; no recovery claim");
        assertEquals(commits + 1, manager.commits);
    }
    @Test void successfulOuterCommitDefersMultipleCallbacksAndAllowsDuplicateEviction() {
        var old = service.read(77L);
        var manager = context.getBean(RecordingTransactions.class);
        new TransactionTemplate(manager).executeWithoutResult(tx -> {
            service.updateForSale(77L, false);
            service.updateRefresh(77L);
            assertSame(old, state.cache.get(77L).get());
            verify(state.cache, never()).evict(any());
        });
        verify(state.cache, times(2)).evict(77L);
        assertNull(state.cache.get(77L));
    }
    @Test void runtimeFailureAfterSuccessfulInnerWriteRollsBackWithoutEviction() {
        var old = service.read(77L);
        var manager = context.getBean(RecordingTransactions.class);
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(manager).executeWithoutResult(tx -> {
            service.updateForSale(77L, false);
            throw new IllegalStateException("fixture outer failure");
        }));
        assertSame(old, state.cache.get(77L).get());
        verify(state.cache, never()).evict(any());
    }
    @Test void caughtParticipantFailureStillMarksOuterTransactionRollbackOnlyWithoutEviction() {
        var old = service.read(77L);
        when(state.levels.findById(1L)).thenThrow(new IllegalStateException("fixture participant failure"));
        var manager = context.getBean(RecordingTransactions.class);
        assertThrows(UnexpectedRollbackException.class, () -> new TransactionTemplate(manager).executeWithoutResult(tx -> {
            service.updateForSale(77L, false);
            assertThrows(IllegalStateException.class, () -> write("PENALTY"));
        }));
        assertSame(old, state.cache.get(77L).get());
        verify(state.cache, never()).evict(any());
    }
    @ParameterizedTest @ValueSource(strings = {"PUT", "SALE", "REFRESH", "DELETE", "PENALTY"})
    void directNonTransactionalWriterUsesImmediateFallback(String writer) {
        service.read(77L);
        ProductService direct = AopTestUtils.getTargetObject(service);
        switch (writer) {
            case "PUT" -> assertEquals("changed", direct.update(77L, update()).getTitle());
            case "SALE" -> assertEquals(77L, direct.updateForSale(77L, false));
            case "REFRESH" -> assertEquals(77L, direct.updateRefresh(77L));
            case "DELETE" -> assertEquals(77L, direct.delete(77L));
            case "PENALTY" -> {
                PenaltyTran penalty = AopTestUtils.getTargetObject(context.getBean(PenaltyTran.class));
                assertEquals(901L, penalty.penaltySaveAndReportUpdate(CreatePenaltyRequestDTO.builder()
                        .penaltyLevelId(1L).reportId(901L).userId(1000L).reporterId(2000L).reason("fixture").build()).getReportId());
            }
        }
        assertNull(state.cache.get(77L));
    }
    @Test void directAcceptanceRetainsMandatoryTransactionCollaboratorBoundaryWithoutEviction() {
        var old = service.read(77L);
        TransactionRequestService direct = AopTestUtils.getTargetObject(context.getBean(TransactionRequestService.class));
        assertThrows(org.springframework.transaction.IllegalTransactionStateException.class, () -> direct.update(501L, true));
        assertSame(old, state.cache.get(77L).get());
        verify(state.cache, never()).evict(any());
    }
    @ParameterizedTest @ValueSource(strings = {"level", "report", "product"})
    void missingPenaltyEntityRollsBackWithoutEviction(String stage) {
        var old = service.read(77L);
        if (stage.equals("level")) when(state.levels.findById(1L)).thenReturn(Optional.empty());
        if (stage.equals("report")) when(state.reports.findById(901L)).thenReturn(Optional.empty());
        if (stage.equals("product")) when(state.products.findById(77L)).thenReturn(Optional.empty());
        assertThrows(jakarta.persistence.EntityNotFoundException.class, () -> write("PENALTY"));
        assertSame(old, state.cache.get(77L).get());
        verify(state.cache, never()).evict(any());
    }
    @ParameterizedTest @ValueSource(strings = {"PUT", "DELETE"})
    void authorizationDenialDoesNotEvict(String writer) {
        var old = service.read(77L);
        SecurityContextHolder.clearContext();
        assertThrows(ResponseStatusException.class, () -> write(writer));
        assertSame(old, state.cache.get(77L).get());
        verify(state.cache, never()).evict(any());
        verify(state.products, never()).save(any());
    }
    @Test void businessDeleteRefusalDoesNotEvict() {
        var old = service.read(77L);
        state.product.setTransactionDetails(TransactionDetails.builder().status(new TransactionStatus(1L, "active")).build());
        assertEquals(-1L, service.delete(77L));
        assertSame(old, state.cache.get(77L).get());
        verify(state.cache, never()).evict(any());
    }
    @ParameterizedTest @ValueSource(strings = {"PUT", "SALE", "REFRESH", "DELETE"})
    void missingProductRetainsFailureWithoutEviction(String writer) {
        var old = service.read(77L);
        when(state.products.findById(77L)).thenReturn(Optional.empty());
        if (writer.equals("PUT") || writer.equals("DELETE")) assertThrows(NoSuchElementException.class, () -> write(writer));
        else assertThrows(NullPointerException.class, () -> write(writer));
        assertSame(old, state.cache.get(77L).get());
        verify(state.cache, never()).evict(any());
    }
    @Test void penaltyStillInvalidatesWhenAlreadyNotForSaleBecauseReportChanged() {
        state.product.setForSale(false);
        var old = service.read(77L);
        assertFalse(old.getReports().get(0).getIsReport());
        write("PENALTY");
        assertNull(state.cache.get(77L));
        assertTrue(service.read(77L).getReports().get(0).getIsReport());
    }
    @ParameterizedTest @ValueSource(strings = {"PUT", "PENALTY", "ACCEPT"})
    void businessCollaboratorFailureDoesNotEvict(String writer) {
        var old = service.read(77L);
        var failure = new IllegalStateException("fixture write failure");
        switch (writer) {
            case "PUT" -> doThrow(failure).when(context.getBean(com.ngng.api.product.product.service.TagService.class)).deleteAllByProductIdAndTagName(any(), any());
            case "PENALTY" -> when(state.products.save(any())).thenThrow(failure);
            case "ACCEPT" -> when(state.details.create(any())).thenThrow(failure);
        }
        assertSame(failure, assertThrows(IllegalStateException.class, () -> write(writer)));
        assertSame(old, state.cache.get(77L).get());
        verify(state.cache, never()).evict(any());
    }
    @ParameterizedTest @ValueSource(strings = {"repeat", "reject", "repeatReject", "conflict", "missing", "missingLock", "removed"})
    void acceptanceWithoutProductChangeDoesNotEvict(String scenario) {
        var old = service.read(77L);
        boolean accept = true;
        switch (scenario) {
            case "repeat" -> {
                state.request.setIsAccepted(true);
                when(state.details.findCurrentDetails(77L)).thenReturn(List.of(TransactionDetails.builder()
                        .consumer(user(2000)).seller(user(1000)).price(300L).build()));
            }
            case "reject" -> accept = false;
            case "repeatReject" -> { accept = false; state.request.setIsAccepted(false); }
            case "conflict" -> when(state.details.findCurrentDetails(77L)).thenReturn(List.of(TransactionDetails.builder().build()));
            case "missing" -> when(state.requests.findProductIdForUpdate(501L)).thenReturn(Optional.empty());
            case "missingLock" -> when(state.products.lockTransactionProduct(77L)).thenReturn(Optional.empty());
            case "removed" -> when(state.requests.findById(501L)).thenReturn(Optional.empty());
        }
        boolean decision = accept;
        if (scenario.equals("conflict")) assertThrows(ResponseStatusException.class, () -> context.getBean(TransactionRequestService.class).update(501L, decision));
        else context.getBean(TransactionRequestService.class).update(501L, decision);
        assertSame(old, state.cache.get(77L).get());
        verify(state.cache, never()).evict(any());
        verify(state.products, never()).save(any());
    }
    @Test void readCacheGetFailureStillPropagates() {
        doThrow(new IllegalStateException("fixture get failure")).when(state.cache).get(77L);
        assertThrows(IllegalStateException.class, () -> service.read(77L));
        verify(state.products, never()).findById(any());
    }
    @Test void readCachePutFailureStillPropagates() {
        doThrow(new IllegalStateException("fixture put failure")).when(state.cache).put(eq(77L), any());
        assertThrows(IllegalStateException.class, () -> service.read(77L));
        verify(state.products).findById(77L);
    }
    @Test void recordAdvisorOrder() {
        var advisors = ((Advised) service).getAdvisors();
        for (int i = 0; i < advisors.length; i++) System.out.println("RF009 advisor[" + i + "]=" + advisors[i].getAdvice().getClass().getName());
        assertEquals(2, advisors.length);
        assertEquals("CacheInterceptor", advisors[0].getAdvice().getClass().getSimpleName());
        assertEquals("TransactionInterceptor", advisors[1].getAdvice().getClass().getSimpleName());
        assertEquals(1, ((Advised) context.getBean(PenaltyTran.class)).getAdvisors().length);
        assertEquals(1, ((Advised) context.getBean(TransactionRequestService.class)).getAdvisors().length);
    }
}
