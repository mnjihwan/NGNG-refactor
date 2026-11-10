package com.ngng.api.product.cache;

import com.ngng.api.penalty.dto.CreatePenaltyRequestDTO;
import com.ngng.api.penalty.service.PenaltyTran;
import com.ngng.api.product.product.dto.response.ReadProductResponseDTO;
import com.ngng.api.product.product.service.ProductService;
import com.ngng.api.transaction.service.TransactionRequestService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.Advised;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.security.core.context.SecurityContextHolder;
import static com.ngng.api.product.cache.ProductCacheTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

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
    @ParameterizedTest @ValueSource(strings = {"SALE", "REFRESH", "DELETE", "PENALTY", "ACCEPT"})
    void beforeNonPutWriterLeavesWarmStaleValue(String writer) {
        var old = service.read(77L);
        write(writer);
        assertSame(old, state.cache.get(77L).get());
        assertSame(old, service.read(77L));
        assertTrue(((ReadProductResponseDTO) state.cache.get(77L).get()).getForSale());
    }
    @Test void beforePutStoresReturnedDto() {
        service.read(77L);
        var updated = service.update(77L, update());
        assertSame(updated, state.cache.get(77L).get());
    }
    @Test void recordAdvisorOrder() {
        var advisors = ((Advised) service).getAdvisors();
        for (int i = 0; i < advisors.length; i++) System.out.println("RF009 advisor[" + i + "]=" + advisors[i].getAdvice().getClass().getName());
        assertEquals(2, advisors.length);
    }
}
