package com.ngng.api.product.cache;

import com.ngng.api.global.security.custom.CustomUserDetails;
import com.ngng.api.penalty.entity.PenaltyLevel;
import com.ngng.api.penalty.repository.*;
import com.ngng.api.penalty.service.PenaltyTran;
import com.ngng.api.product.product.dto.request.UpdateProductRequestDTO;
import com.ngng.api.product.product.entity.*;
import com.ngng.api.product.product.repository.ProductRepository;
import com.ngng.api.product.product.service.*;
import com.ngng.api.report.report.entity.Report;
import com.ngng.api.report.report.repository.ReportRepository;
import com.ngng.api.report.report.service.ReportService;
import com.ngng.api.transaction.entity.TransactionRequest;
import com.ngng.api.transaction.repository.TransactionRequestRepository;
import com.ngng.api.transaction.service.*;
import com.ngng.api.user.user.entity.User;
import com.ngng.api.user.user.service.AuthService;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.*;
import java.util.*;
import static org.mockito.Mockito.*;

/** Real cache/transaction advice, fake resources. Does not simulate JPA rollback or Redis. */
final class ProductCacheTestSupport {
    static User user(long id) { return User.builder().userId(id).email("rf009@example.test").nickname("fixture").build(); }
    static Product product() {
        return Product.builder().productId(77L).user(user(1000)).title("original").content("fixture")
                .forSale(true).visible(true).price(300L).isEscrow(false).discountable(false).freeShipping(false)
                .status(Status.builder().statusId(1L).build()).category(Category.builder().categoryId(1L).build())
                .tags(new ArrayList<>()).productImages(new ArrayList<>()).publicChats(new ArrayList<>()).reports(new ArrayList<>()).build();
    }
    static UpdateProductRequestDTO update() {
        return UpdateProductRequestDTO.builder().title("changed").content("new fixture").price(400L)
                .forSale(false).visible(true).isEscrow(false).discountable(false).freeShipping(false)
                .statusId(1L).categoryId(1L).tags(List.of()).build();
    }
    static void admin() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CustomUserDetails(user(1000)), "unused", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }
    static AnnotationConfigApplicationContext context(State state) {
        var context = new AnnotationConfigApplicationContext();
        context.registerBean(State.class, () -> state);
        context.register(Configuration.class);
        context.refresh();
        return context;
    }
    static class State {
        final Product product = product();
        final ProductRepository products = mock(ProductRepository.class);
        final TransactionRequestRepository requests = mock(TransactionRequestRepository.class);
        final TransactionDetailsService details = mock(TransactionDetailsService.class);
        final PenaltyRepository penalties = mock(PenaltyRepository.class);
        final PenaltyLevelRepository levels = mock(PenaltyLevelRepository.class);
        final ReportRepository reports = mock(ReportRepository.class);
        final Cache cache = spy(new ConcurrentMapCache("getProduct"));
        final CacheManager caches = mock(CacheManager.class);
        final Report report = Report.builder().reportId(901L).productId(77L).reporter(user(2000)).user(user(1000)).isReport(false).build();
        final TransactionRequest request = TransactionRequest.builder().transactionRequestId(501L).product(product).buyer(user(2000)).price(300L).build();
        State() {
            when(caches.getCache("getProduct")).thenReturn(cache);
            when(caches.getCacheNames()).thenReturn(List.of("getProduct"));
            when(products.findById(77L)).thenReturn(Optional.of(product));
            when(products.save(any())).thenAnswer(call -> call.getArgument(0));
            when(requests.findProductIdForUpdate(501L)).thenReturn(Optional.of(77L));
            var locked = mock(ProductRepository.TransactionProduct.class);
            when(locked.getSellerId()).thenReturn(1000L);
            when(products.lockTransactionProduct(77L)).thenReturn(Optional.of(locked));
            when(requests.findById(501L)).thenReturn(Optional.of(request));
            when(requests.findAllByProductProductId(77L)).thenReturn(List.of(request));
            when(details.findCurrentDetails(77L)).thenReturn(List.of());
            when(levels.findById(1L)).thenReturn(Optional.of(PenaltyLevel.builder().penaltyLevelId(1L).penaltyLevelDays(1L).build()));
            when(reports.findById(901L)).thenReturn(Optional.of(report));
            when(penalties.save(any())).thenAnswer(call -> call.getArgument(0));
            product.getReports().add(report);
        }
    }
    @org.springframework.context.annotation.Configuration
    @EnableCaching @EnableTransactionManagement
    @Import({ProductService.class, ProductCacheInvalidator.class, PenaltyTran.class, TransactionRequestService.class})
    static class Configuration {
        @Bean(name = "contentCacheManager") CacheManager caches(State s) { return s.caches; }
        @Bean RecordingTransactions transactionManager() { return new RecordingTransactions(); }
        @Bean ProductRepository products(State s) { return s.products; }
        @Bean TagService tags() { return mock(TagService.class); }
        @Bean AuthService auth() {
            var auth = mock(AuthService.class);
            when(auth.readAuthUser()).thenReturn(user(1000));
            return auth;
        }
        @Bean TransactionRequestRepository requests(State s) { return s.requests; }
        @Bean TransactionDetailsService details(State s) { return s.details; }
        @Bean PenaltyRepository penalties(State s) { return s.penalties; }
        @Bean PenaltyLevelRepository levels(State s) { return s.levels; }
        @Bean ReportRepository reports(State s) { return s.reports; }
        @Bean ReportService reportService() { return mock(ReportService.class); }
    }
    static class RecordingTransactions extends AbstractPlatformTransactionManager {
        final ThreadLocal<Tx> current = new ThreadLocal<>();
        int commits, rollbacks;
        boolean failCommit;
        Runnable beforeCommit = () -> {};
        @Override protected Object doGetTransaction() { return current.get() == null ? new Tx() : current.get(); }
        @Override protected boolean isExistingTransaction(Object tx) { return ((Tx) tx).active; }
        @Override protected void doBegin(Object tx, TransactionDefinition definition) { ((Tx) tx).active = true; current.set((Tx) tx); }
        @Override protected void doCommit(DefaultTransactionStatus status) {
            beforeCommit.run();
            if (failCommit) throw new UnexpectedRollbackException("fixture commit failure");
            commits++;
        }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
        @Override protected void doSetRollbackOnly(DefaultTransactionStatus status) { ((Tx) status.getTransaction()).rollbackOnly = true; }
        @Override protected void doCleanupAfterCompletion(Object tx) { current.remove(); }
        static class Tx implements SmartTransactionObject {
            boolean active, rollbackOnly;
            @Override public boolean isRollbackOnly() { return rollbackOnly; }
            @Override public void flush() {}
        }
    }
    private ProductCacheTestSupport() {}
}
