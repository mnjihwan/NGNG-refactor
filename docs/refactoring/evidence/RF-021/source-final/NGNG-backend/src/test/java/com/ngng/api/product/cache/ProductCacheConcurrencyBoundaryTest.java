package com.ngng.api.product.cache;

import com.ngng.api.product.product.entity.Product;
import com.ngng.api.product.product.service.ProductService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static com.ngng.api.product.cache.ProductCacheTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Two independent Spring contexts, shared mock repository/cache; no Redis/process or DB-isolation claim. */
class ProductCacheConcurrencyBoundaryTest {
    static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(10, TimeUnit.SECONDS), "fixture ordering timed out"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }
    @ParameterizedTest @ValueSource(ints = {1, 2})
    void delayedMissCanRepopulateStaleDataAfterOtherContextCommitAndEviction(int readerCount) throws Exception {
        var state = new State();
        var loaded = new CountDownLatch(readerCount);
        var release = new CountDownLatch(1);
        when(state.products.findById(77L)).thenAnswer(call -> {
            if (Thread.currentThread().getName().startsWith("rf009-reader")) {
                Product snapshot = product(); // Snapshot captured before writer changes the backing fixture.
                loaded.countDown(); await(release);
                return Optional.of(snapshot);
            }
            return Optional.of(state.product);
        });
        ExecutorService readers = Executors.newFixedThreadPool(readerCount, runnable -> new Thread(runnable, "rf009-reader"));
        try (var first = context(state); var second = context(state)) {
            var reader = first.getBean(ProductService.class);
            var writer = second.getBean(ProductService.class);
            List<Future<?>> results = new ArrayList<>();
            for (int i = 0; i < readerCount; i++) results.add(readers.submit(() -> reader.read(77L)));
            await(loaded);
            writer.updateForSale(77L, false);
            assertFalse(state.product.getForSale());
            assertNull(state.cache.get(77L), "successful writer did evict");
            release.countDown();
            for (Future<?> result : results) result.get(10, TimeUnit.SECONDS);
            assertTrue(reader.read(77L).getForSale(), "late miss put reproducibly leaves stale data");
            assertFalse(state.product.getForSale(), "cache diverges from backing fixture");
            System.out.println("RF009 residual reproduced: readers=" + readerCount + " old snapshot -> other context commit/evict -> late stale put");
        } finally {
            release.countDown(); readers.shutdownNow(); assertTrue(readers.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
    @Test void reverseWriterCommitOrderEvictsIntermediateCachedReadInsteadOfPuttingReturnedDto() throws Exception {
        var state = new State();
        var firstSnapshot = product();
        var secondSnapshot = product();
        var committed = new AtomicReference<>(state.product);
        var staged = new ThreadLocal<Product>();
        var firstReady = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        when(state.products.findById(77L)).thenAnswer(call -> Optional.of(
                Thread.currentThread().getName().equals("rf009-writer-first") ? firstSnapshot
                        : staged.get() != null ? staged.get() : committed.get()));
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "rf009-writer-first"));
        try (var first = context(state); var second = context(state)) {
            ProductService firstService = first.getBean(ProductService.class);
            ProductService secondService = second.getBean(ProductService.class);
            first.getBean(RecordingTransactions.class).beforeCommit = () -> {
                firstReady.countDown(); await(releaseFirst); committed.set(firstSnapshot);
            };
            // The mock publishes each transaction's snapshot only at the fake commit point.
            second.getBean(RecordingTransactions.class).beforeCommit = () -> committed.set(secondSnapshot);
            state.cache.put(77L, "warm fixture");
            Future<?> firstResult = executor.submit(() -> {
                admin();
                try { return firstService.update(77L, update()); }
                finally { SecurityContextHolder.clearContext(); }
            });
            await(firstReady);
            assertEquals("warm fixture", state.cache.get(77L).get(), "first writer has not committed");
            // A distinct snapshot represents a second independent writer transaction.
            secondSnapshot.setTitle("second-committed");
            staged.set(secondSnapshot);
            secondService.updateForSale(77L, false);
            staged.remove();
            assertNull(state.cache.get(77L));
            second.getBean(RecordingTransactions.class).beforeCommit = () -> {};
            assertEquals("second-committed", secondService.read(77L).getTitle());
            releaseFirst.countDown(); firstResult.get(10, TimeUnit.SECONDS);
            assertNull(state.cache.get(77L), "later commit evicts intermediate reader's entry");
            assertEquals("changed", secondService.read(77L).getTitle());
            verify(state.cache, times(2)).evict(77L);
            System.out.println("RF009 reverse commit fixture: writer A starts -> B commit/evict -> reader caches B -> A commit/evict -> read A");
        } finally {
            releaseFirst.countDown(); executor.shutdownNow(); assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}
