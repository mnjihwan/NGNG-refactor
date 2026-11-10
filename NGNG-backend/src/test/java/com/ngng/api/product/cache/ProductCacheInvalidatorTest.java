package com.ngng.api.product.cache;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ngng.api.product.product.service.ProductCacheInvalidator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.*;
import static com.ngng.api.product.cache.ProductCacheTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductCacheInvalidatorTest {
    State state = new State();
    ProductCacheInvalidator invalidator = new ProductCacheInvalidator(state.caches);
    ListAppender<ILoggingEvent> logs;
    Logger logger;
    @BeforeEach void logs() {
        logger = (Logger) LoggerFactory.getLogger("product-log");
        logs = new ListAppender<>(); logs.start(); logger.addAppender(logs);
        state.cache.put(77L, "old"); state.cache.put(88L, "other");
    }
    @AfterEach void cleanup() {
        logger.detachAppender(logs); logs.stop();
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }
    @ParameterizedTest @ValueSource(strings = {"noTx", "synchronizationOnly", "transactionWithoutSynchronization"})
    void nonTransactionalOrIncompleteBoundaryEvictsImmediately(String boundary) {
        if (boundary.equals("synchronizationOnly")) TransactionSynchronizationManager.initSynchronization();
        if (boundary.equals("transactionWithoutSynchronization")) TransactionSynchronizationManager.setActualTransactionActive(true);
        invalidator.invalidate(77L);
        assertNull(state.cache.get(77L));
        assertEquals("other", state.cache.get(88L).get());
        if (boundary.equals("synchronizationOnly")) assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty());
    }
    @ParameterizedTest @ValueSource(strings = {"lookup", "evict", "missingCache"})
    void failureIsLoggedWithoutChangingCallerResultAndLaterCallbacksContinue(String stage) {
        var tx = new RecordingTransactions();
        new TransactionTemplate(tx).executeWithoutResult(status -> {
            invalidator.invalidate(77L);
            invalidator.invalidate(88L);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { state.cache.put(99L, "later callback"); }
            });
            if (stage.equals("lookup")) when(state.caches.getCache("getProduct"))
                    .thenThrow(new IllegalStateException("fixture lookup failure")).thenReturn(state.cache);
            else if (stage.equals("missingCache")) when(state.caches.getCache("getProduct")).thenReturn(null).thenReturn(state.cache);
            else doThrow(new IllegalStateException("fixture evict failure")).when(state.cache).evict(77L);
        });
        assertEquals(1, tx.commits);
        assertEquals(0, tx.rollbacks);
        assertEquals("old", state.cache.get(77L).get());
        assertNull(state.cache.get(88L));
        assertEquals("later callback", state.cache.get(99L).get());
        assertEquals(1, logs.list.size());
        assertTrue(logs.list.get(0).getFormattedMessage().contains("cache=getProduct productId=77"));
        assertNotNull(logs.list.get(0).getThrowableProxy());
    }
    @ParameterizedTest @ValueSource(strings = {"lookup", "evict"})
    void noTransactionFallbackFailureAlsoLogsAndReturns(String stage) {
        if (stage.equals("lookup")) when(state.caches.getCache("getProduct")).thenThrow(new IllegalStateException("fixture lookup failure"));
        else doThrow(new IllegalStateException("fixture evict failure")).when(state.cache).evict(77L);
        assertDoesNotThrow(() -> invalidator.invalidate(77L));
        assertEquals("old", state.cache.get(77L).get());
        assertEquals(1, logs.list.size());
        assertTrue(logs.list.get(0).getFormattedMessage().contains("cache=getProduct productId=77"));
    }
    @Test void activeTransactionDoesNotEvenLookUpCacheBeforeSuccessfulCommit() {
        clearInvocations(state.caches);
        var tx = new RecordingTransactions();
        new TransactionTemplate(tx).executeWithoutResult(status -> {
            invalidator.invalidate(77L);
            verifyNoInteractions(state.caches);
            assertEquals("old", state.cache.get(77L).get());
        });
        verify(state.caches).getCache("getProduct");
        assertNull(state.cache.get(77L));
    }
}
