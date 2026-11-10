package com.ngng.api.product.product.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Invalidates this writer's product after commit; concurrent cache misses can still repopulate stale data. */
@Component
@Slf4j(topic = "product-log")
public class ProductCacheInvalidator {
    private static final String CACHE_NAME = "getProduct";
    private final CacheManager cacheManager;

    public ProductCacheInvalidator(@Qualifier("contentCacheManager") CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void invalidate(Long productId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { evict(productId); }
            });
        } else {
            evict(productId);
        }
    }

    private void evict(Long productId) {
        try {
            Cache cache = cacheManager.getCache(CACHE_NAME);
            if (cache == null) throw new IllegalStateException("Product cache is not configured");
            cache.evict(productId);
        } catch (RuntimeException failure) {
            // The write may already be committed. Preserve its response and let later callbacks run.
            log.warn("Product cache invalidation failed: cache={} productId={}", CACHE_NAME, productId, failure);
        }
    }
}
