package com.ngng.api.product.authorization;

import com.ngng.api.global.security.custom.CustomUserDetails;
import com.ngng.api.product.product.dto.response.ReadProductResponseDTO;
import com.ngng.api.product.product.entity.Product;
import com.ngng.api.product.product.repository.ProductRepository;
import com.ngng.api.product.product.service.*;
import com.ngng.api.user.user.service.AuthService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static com.ngng.api.product.authorization.ProductAuthorizationFixture.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual Spring cache interceptor with an in-memory manager; no Redis or transaction commit claim. */
class ProductAuthorizationCacheTest {
    AnnotationConfigApplicationContext context;
    ProductService service;
    ProductRepository products;
    AuthService auth;
    TagService tags;
    Product target;
    org.springframework.cache.Cache cache;
    ReadProductResponseDTO original;
    @BeforeEach void setup() {
        context = new AnnotationConfigApplicationContext(CacheConfiguration.class);
        service = context.getBean(ProductService.class); assertTrue(AopUtils.isAopProxy(service));
        products = context.getBean(ProductRepository.class); auth = context.getBean(AuthService.class); tags = context.getBean(TagService.class);
        target = product(1000L); when(products.findById(77L)).thenReturn(Optional.of(target));
        when(products.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auth.readAuthUser()).thenReturn(user(1000L));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new CustomUserDetails(user(null)), "unused", List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        cache = context.getBean(CacheManager.class).getCache("getProduct");
        original = ReadProductResponseDTO.builder().id(77L).title("cached-original").build(); cache.put(77L, original);
    }
    @AfterEach void close() { SecurityContextHolder.clearContext(); context.close(); }
    @Test void allowedPutReplacesCachedValueWithReturnedDto() {
        var result = service.update(77L, request());
        assertSame(result, cache.get(77L).get()); assertNotSame(original, result); assertEquals("changed", result.getTitle());
        verify(tags).deleteAllByProductIdAndTagName(77L, List.of("old"));
    }
    @ParameterizedTest @ValueSource(strings = {"PUT", "DELETE"})
    void deniedMutationLeavesExistingCacheAndStateUntouched(String method) {
        when(auth.readAuthUser()).thenReturn(user(2000L));
        assertThrows(ResponseStatusException.class, () -> { if (method.equals("PUT")) service.update(77L, request()); else service.delete(77L); });
        assertSame(original, cache.get(77L).get()); assertEquals("original", target.getTitle()); assertTrue(target.getVisible());
        verifyNoInteractions(tags); verify(products, never()).save(any());
    }
    @Test void allowedDeleteRetainsExistingCacheBehavior() {
        assertEquals(77L, service.delete(77L)); assertSame(original, cache.get(77L).get());
        verify(products).save(target); assertFalse(target.getVisible());
    }
    @Configuration @EnableCaching @Import(ProductService.class)
    static class CacheConfiguration {
        @Bean(name = "contentCacheManager") CacheManager cacheManager() { return new ConcurrentMapCacheManager("getProduct"); }
        @Bean ProductRepository products() { return mock(ProductRepository.class); }
        @Bean TagService tags() { return mock(TagService.class); }
        @Bean AuthService authService() { return mock(AuthService.class); }
    }
}
