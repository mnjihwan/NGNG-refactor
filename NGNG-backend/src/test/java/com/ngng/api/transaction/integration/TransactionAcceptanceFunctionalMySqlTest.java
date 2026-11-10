package com.ngng.api.transaction.integration;

import com.ngng.api.product.product.entity.Product;
import com.ngng.api.product.product.repository.ProductRepository;
import com.ngng.api.transaction.dto.CreateTransactionDetailsRequestDTO;
import com.ngng.api.transaction.dto.ReadTransactionDetailsDTO;
import com.ngng.api.transaction.entity.TransactionDetails;
import com.ngng.api.transaction.repository.TransactionDetailsRepository;
import com.ngng.api.transaction.repository.TransactionRequestRepository;
import com.ngng.api.transaction.repository.TransactionStatusRepository;
import com.ngng.api.transaction.service.TransactionDetailsService;
import com.ngng.api.transaction.service.TransactionRequestService;
import com.ngng.api.user.user.repository.UserRepository;
import com.ngng.api.user.user.service.AuthService;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;

/**
 * Minimal JPA plus real production Spring service proxies. No Boot/YAML, HTTP, Redis, ES,
 * S3, SMTP, SMS, SQL initializer or DDL. Requires separate safety review before opt-in.
 * Latches coordinate transactions; Future states are not direct DB lock-wait observations.
 */
@EnabledIfEnvironmentVariable(named = "RF004_MYSQL_TEST", matches = Rf004MySqlFixture.OPT_IN)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TransactionAcceptanceFunctionalMySqlTest {
    private static final Runnable NO_HOOK = () -> {};
    private static final Hooks HOOKS = new Hooks();
    private static final ThreadLocal<List<String>> SQL = ThreadLocal.withInitial(ArrayList::new);
    private static final ThreadLocal<List<Long>> CONNECTIONS = ThreadLocal.withInitial(ArrayList::new);
    private static Rf004MySqlFixture fixture;
    private AnnotationConfigApplicationContext context;
    private TransactionTemplate tx;
    private TransactionRequestService requests;
    private TransactionDetailsService details;
    private TransactionRequestRepository requestRepository;
    private ProductRepository products;
    private EntityManagerFactory emf;
    private final List<ExecutorService> executors = new ArrayList<>();
    private final List<CountDownLatch> latches = new ArrayList<>();

    @BeforeAll
    void verifyReadOnlyTargetThenStartOnlyOwnedFixtures() throws Exception {
        fixture = new Rf004MySqlFixture();
        context = new AnnotationConfigApplicationContext(IsolatedJpa.class);
        tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        requests = context.getBean(TransactionRequestService.class);
        details = context.getBean(TransactionDetailsService.class);
        requestRepository = context.getBean(TransactionRequestRepository.class);
        products = context.getBean(ProductRepository.class);
        emf = context.getBean(EntityManagerFactory.class);
        assertThat(AopUtils.isAopProxy(requests)).isTrue();
        assertThat(AopUtils.isAopProxy(details)).isTrue();
        fixture.start(tx);
    }

    @AfterEach
    void stopEveryWorkerBeforeAnyCleanup() throws Exception { HOOKS.clear(); SQL.remove(); CONNECTIONS.remove(); stopWorkers(); }

    @AfterAll
    void removeOnlyTrackedFixtureRowsAfterAllWorkersStop() throws Exception {
        try { stopWorkers(); if (fixture != null) fixture.cleanup(); }
        catch (Exception | AssertionError failure) {
            if (fixture != null) fixture.reportTrackedIdentitiesForManualReadOnlyRecovery();
            throw failure;
        }
        finally { if (context != null) context.close(); fixture = null; HOOKS.clear(); }
    }

    @Test
    void firstAcceptanceAndIdenticalRepeatKeepExactlyOneUnchangedTransaction() {
        var item = fixture.product(true);
        assertThat(requests.update(item.requestA(), true)).isEqualTo(item.requestA());
        assertAccepted(item, item.requestA(), item.buyerA(), 80);
        var before = fixture.state(item);
        SQL.get().clear();
        assertThat(requests.update(item.requestA(), true)).isEqualTo(item.requestA());
        assertThat(SQL.get()).noneMatch(sql -> sql.startsWith("update ") || sql.startsWith("insert ") || sql.startsWith("delete "));
        conflict(() -> requests.update(item.requestA(), false));
        conflict(() -> requests.update(item.requestB(), true));
        assertThat(fixture.state(item)).isEqualTo(before);
        assertThat(requests.update(Long.MAX_VALUE, true)).isEqualTo(-1L);
    }

    @Test
    void entryUsesOneReadCommittedConnectionAndLoadsEntitiesAfterProductLock() {
        var item = fixture.product(true);
        SQL.get().clear(); CONNECTIONS.get().clear();
        requests.update(item.requestA(), true);
        assertThat(new HashSet<>(CONNECTIONS.get())).hasSize(1);
        var statements = SQL.get();
        int lock = -1;
        for (int index = 0; index < statements.size(); index++) {
            String sql = statements.get(index);
            if (sql.contains("from product where product_id") && sql.contains("for update")) { lock = index; break; }
        }
        assertThat(lock).isPositive();
        assertThat(statements.subList(0, lock)).hasSize(1);
        assertThat(statements.get(0)).contains("transaction_request", "product_id").doesNotContain("is_accepted");
        assertThat(statements).filteredOn(sql -> sql.contains("for update")).allSatisfy(sql ->
                assertThat(sql).contains("from product where product_id").doesNotContain(" join "));
        statements.forEach(sql -> System.out.println("RF004 entry SQL: " + sql));
        System.out.println("RF004 function entry: same-connection=true READ-COMMITTED/write=true scalar-product-lock-before-entity=true");
    }

    @Test
    void alreadySoldProductAndAlreadyRejectedSiblingAreNotUpdatedAgain() {
        var item = fixture.product(true);
        fixture.jdbc.update("UPDATE product SET for_sale=false WHERE product_id=?", item.product());
        fixture.jdbc.update("UPDATE transaction_request SET is_accepted=false WHERE transaction_request_id=?", item.requestB());
        SQL.get().clear();
        requests.update(item.requestA(), true);
        assertAccepted(item, item.requestA(), item.buyerA(), 80);
        assertThat(SQL.get()).noneMatch(sql -> sql.startsWith("update product "));
        assertThat(SQL.get()).filteredOn(sql -> sql.startsWith("update transaction_request ")).hasSize(1);
    }

    @Test
    void preboundEmptyOsivEntityManagerWorksWithoutPreloadingTargets() {
        var item = fixture.product(true);
        var entityManager = emf.createEntityManager();
        TransactionSynchronizationManager.bindResource(emf, new org.springframework.orm.jpa.EntityManagerHolder(entityManager));
        try {
            requests.update(item.requestA(), true);
            var before = fixture.state(item);
            SQL.get().clear();
            requests.update(item.requestA(), true);
            assertThat(SQL.get()).noneMatch(sql -> sql.startsWith("update ") || sql.startsWith("insert "));
            assertThat(fixture.state(item)).isEqualTo(before);
        } finally {
            TransactionSynchronizationManager.unbindResource(emf);
            entityManager.close();
        }
        assertAccepted(item, item.requestA(), item.buyerA(), 80);
    }

    @Test
    void rejectionThatLocksFirstStillAllowsLaterAcceptance() throws Exception {
        var item = fixture.product(true);
        ordered(() -> requests.update(item.requestA(), false), () -> requests.update(item.requestA(), true), false);
        assertAccepted(item, item.requestA(), item.buyerA(), 80);
    }

    @Test
    void acceptanceThatLocksFirstMakesWaitingDirectCreateConflict() throws Exception {
        var item = fixture.product(true);
        ordered(() -> requests.update(item.requestA(), true), () -> details.create(direct(item)), true);
        assertAccepted(item, item.requestA(), item.buyerA(), 80);
    }

    @Test
    void directCreateThatLocksFirstMakesWaitingAcceptanceConflict() throws Exception {
        var item = fixture.product(true);
        ordered(() -> details.create(direct(item)), () -> requests.update(item.requestA(), true), true);
        assertDetails(item, item.buyerA(), 80);
        assertThat(fixture.jdbc.queryForObject("SELECT for_sale FROM product WHERE product_id=?", Boolean.class, item.product())).isTrue();
        assertThat(fixture.jdbc.queryForObject("SELECT is_accepted FROM transaction_request WHERE transaction_request_id=?", Boolean.class, item.requestA())).isNull();
    }

    @Test
    void rolledBackWinnerAllowsWaitingAcceptanceToCommit() throws Exception {
        var item = fixture.product(true);
        CountDownLatch inserted = latch(1), release = latch(1), waiting = latch(1);
        ExecutorService workers = executor();
        Future<Attempt> first = workers.submit(() -> withHook(HOOKS.afterSaved, () -> {
            inserted.countDown(); await(release); throw new InjectedFailure();
        }, () -> attempt(() -> requests.update(item.requestA(), true))));
        await(inserted);
        Future<Long> second = workers.submit(() -> withHook(HOOKS.beforeLock, waiting::countDown,
                () -> requests.update(item.requestB(), true)));
        await(waiting); release.countDown();
        assertThat(first.get(20, TimeUnit.SECONDS).failure()).isInstanceOf(InjectedFailure.class);
        assertThat(second.get(20, TimeUnit.SECONDS)).isEqualTo(item.requestB());
        assertAccepted(item, item.requestB(), item.buyerB(), 90);
    }

    private void ordered(Supplier<?> first, Supplier<?> second, boolean secondConflicts) throws Exception {
        CountDownLatch locked = latch(1), release = latch(1), waiting = latch(1);
        ExecutorService workers = executor();
        Future<Attempt> leader = workers.submit(() -> withHook(HOOKS.afterLock, once(() -> {
            locked.countDown(); await(release);
        }), () -> attempt(first)));
        await(locked);
        Future<Attempt> follower = workers.submit(() -> withHook(HOOKS.beforeLock, once(waiting::countDown), () -> attempt(second)));
        await(waiting); release.countDown();
        assertThat(leader.get(20, TimeUnit.SECONDS).failure()).isNull();
        Attempt outcome = follower.get(20, TimeUnit.SECONDS);
        if (secondConflicts) assertConflict(outcome.failure());
        else assertThat(outcome.failure()).isNull();
    }

    @Test
    void rejectingPendingOrRejectedRequestRemainsSuccessfulAndLaterAcceptanceIsAllowed() {
        var item = fixture.product(true);
        assertThat(requests.update(item.requestA(), false)).isEqualTo(item.requestA());
        var before = fixture.state(item);
        assertThat(requests.update(item.requestA(), false)).isEqualTo(item.requestA());
        assertThat(fixture.state(item)).isEqualTo(before);
        assertThat(requests.update(item.requestA(), true)).isEqualTo(item.requestA());
        assertAccepted(item, item.requestA(), item.buyerA(), 80);
    }

    @Test
    void simultaneousAcceptanceOfSameRequestReturnsTwoSuccessesAndOneTransaction() throws Exception {
        var item = fixture.product(true);
        var outcomes = pair(() -> requests.update(item.requestA(), true), () -> requests.update(item.requestA(), true));
        assertThat(outcomes).allSatisfy(value -> { assertThat(value.failure()).isNull(); assertThat(value.result()).isEqualTo(item.requestA()); });
        assertAccepted(item, item.requestA(), item.buyerA(), 80);
    }

    @Test
    void simultaneousDifferentRequestsAcceptExactlyOneAndRejectTheOther() throws Exception {
        var item = fixture.product(true);
        var outcomes = pair(() -> requests.update(item.requestA(), true), () -> requests.update(item.requestB(), true));
        assertOneSuccessOneConflict(outcomes);
        long winner = ((Number) outcomes.stream().filter(value -> value.failure() == null).findFirst().orElseThrow().result()).longValue();
        assertAccepted(item, winner, winner == item.requestA() ? item.buyerA() : item.buyerB(), winner == item.requestA() ? 80 : 90);
    }

    @Test
    void acceptanceThatOwnsProductLockCompletesBeforeConcurrentRejectionConflicts() throws Exception {
        var item = fixture.product(true);
        CountDownLatch locked = latch(1), release = latch(1), rejectStarted = latch(1);
        ExecutorService workers = executor();
        Future<Long> accepted = workers.submit(() -> withHook(HOOKS.afterLock, () -> { locked.countDown(); await(release); },
                () -> requests.update(item.requestA(), true)));
        await(locked);
        Future<Attempt> rejected = workers.submit(() -> withHook(HOOKS.beforeLock, rejectStarted::countDown,
                () -> attempt(() -> requests.update(item.requestA(), false))));
        await(rejectStarted);
        release.countDown();
        assertThat(accepted.get(20, TimeUnit.SECONDS)).isEqualTo(item.requestA());
        assertConflict(rejected.get(20, TimeUnit.SECONDS).failure());
        assertAccepted(item, item.requestA(), item.buyerA(), 80);
    }

    @Test
    void directCreatePreservesSaleAndRequestsAndDuplicateConflictsWithoutWrites() {
        var item = fixture.product(true);
        ReadTransactionDetailsDTO created = details.create(direct(item));
        assertThat(created.getId()).isPositive();
        assertThat(created.getPrice()).isEqualTo(80L);
        assertThat(created.getStatus().getId()).isEqualTo(1L);
        assertThat(fixture.jdbc.queryForObject("SELECT for_sale FROM product WHERE product_id=?", Boolean.class, item.product())).isTrue();
        assertThat(fixture.jdbc.queryForList("SELECT is_accepted FROM transaction_request WHERE product_id=?", Boolean.class, item.product()))
                .containsExactly(null, null);
        var before = fixture.state(item);
        conflict(() -> details.create(direct(item)));
        conflict(() -> requests.update(item.requestA(), true));
        assertThat(fixture.state(item)).isEqualTo(before);
    }

    @Test
    void simultaneousDirectCreatesUseSameProductGuard() throws Exception {
        var item = fixture.product(true);
        assertOneSuccessOneConflict(pair(() -> details.create(direct(item)), () -> details.create(direct(item))));
        assertDetails(item, item.buyerA(), 80);
    }

    @Test
    void acceptanceAndDirectCreationRaceProduceExactlyOneCommittedTransaction() throws Exception {
        var item = fixture.product(true);
        assertOneSuccessOneConflict(pair(() -> requests.update(item.requestA(), true), () -> details.create(direct(item))));
        assertDetails(item, item.buyerA(), 80);
        Boolean accepted = fixture.jdbc.queryForObject("SELECT is_accepted FROM transaction_request WHERE transaction_request_id=?",
                Boolean.class, item.requestA());
        if (Boolean.TRUE.equals(accepted)) assertAccepted(item, item.requestA(), item.buyerA(), 80);
        else {
            assertThat(accepted).isNull();
            assertThat(fixture.jdbc.queryForObject("SELECT for_sale FROM product WHERE product_id=?", Boolean.class, item.product())).isTrue();
        }
    }

    @Test
    void failureAfterProductSaleChangeRollsBackAllWritesAndReleasesLock() {
        assertRollbackAt(HOOKS.afterSold);
    }

    @Test
    void failureAfterRealTransactionInsertFlushRollsBackAllWritesAndReleasesLock() {
        assertRollbackAt(HOOKS.afterSaved);
    }

    @Test
    void failureAfterOtherRequestsAreChangedRollsBackAllWritesAndReleasesLock() {
        assertRollbackAt(HOOKS.afterOthers);
    }

    @Test
    void mappingFailureAfterInsertRollsBackAcceptanceAndDirectCreation() {
        var accepted = fixture.product(false);
        var before = fixture.state(accepted);
        assertThatThrownBy(() -> requests.update(accepted.requestA(), true)).isInstanceOf(NullPointerException.class);
        assertThat(fixture.state(accepted)).isEqualTo(before);
        var direct = fixture.product(false);
        before = fixture.state(direct);
        assertThatThrownBy(() -> details.create(direct(direct))).isInstanceOf(NullPointerException.class);
        assertThat(fixture.state(direct)).isEqualTo(before);
    }

    @Test
    void rollbackOnlyAndBeforeCommitFailurePropagateInsteadOfLeavingPartialState() {
        var item = fixture.product(true);
        var before = fixture.state(item);
        assertThatThrownBy(() -> withHook(HOOKS.afterSaved,
                () -> EntityManagerFactoryUtils.getTransactionalEntityManager(emf).getTransaction().setRollbackOnly(),
                () -> requests.update(item.requestA(), true))).isInstanceOf(UnexpectedRollbackException.class);
        assertThat(fixture.state(item)).isEqualTo(before);
        assertThatThrownBy(() -> withHook(HOOKS.afterSaved,
                () -> TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void beforeCommit(boolean readOnly) { throw new InjectedFailure(); }
                }), () -> requests.update(item.requestA(), true))).isInstanceOf(InjectedFailure.class);
        assertThat(fixture.state(item)).isEqualTo(before);
        assertThat(requests.update(item.requestA(), true)).isEqualTo(item.requestA());
        assertAccepted(item, item.requestA(), item.buyerA(), 80);
    }

    @Test
    void differentProductsSharingSellerCategoryAndStatusCommitIndependently() throws Exception {
        long seller = fixture.user();
        var first = fixture.product(seller, true);
        var second = fixture.product(seller, true);
        CountDownLatch firstFlushed = latch(1), release = latch(1);
        ExecutorService workers = executor();
        Future<Long> held = workers.submit(() -> tx.execute(status -> {
            Long result = requests.update(first.requestA(), true);
            EntityManagerFactoryUtils.getTransactionalEntityManager(emf).flush();
            firstFlushed.countDown(); await(release);
            return result;
        }));
        await(firstFlushed);
        Future<Long> independent = workers.submit(() -> requests.update(second.requestA(), true));
        assertThat(independent.get(10, TimeUnit.SECONDS)).isEqualTo(second.requestA());
        assertThat(held.isDone()).isFalse();
        assertDetails(second, second.buyerA(), 80);
        assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM transaction_details WHERE product_id=?", Long.class, first.product())).isZero();
        release.countDown();
        assertThat(held.get(20, TimeUnit.SECONDS)).isEqualTo(first.requestA());
        assertAccepted(first, first.requestA(), first.buyerA(), 80);
        assertAccepted(second, second.requestA(), second.buyerA(), 80);
        System.out.println("RF004 shared-seller/category/status: B-committed-before-A-release=true; not direct DB wait observation");
    }

    @Test
    void anotherProductCanFinishWhileFirstIsHeldAfterAbsentDetailsReadBeforeInsert() throws Exception {
        long seller = fixture.user();
        var first = fixture.product(seller, true);
        var second = fixture.product(seller, true);
        CountDownLatch firstAbsentRead = latch(1), release = latch(1);
        ExecutorService workers = executor();
        Future<Long> held = workers.submit(() -> withHook(HOOKS.afterSold, () -> {
            firstAbsentRead.countDown(); await(release);
        }, () -> requests.update(first.requestA(), true)));
        await(firstAbsentRead);
        Future<Long> independent = workers.submit(() -> requests.update(second.requestA(), true));
        assertThat(independent.get(10, TimeUnit.SECONDS)).isEqualTo(second.requestA());
        assertThat(held.isDone()).isFalse();
        assertDetails(second, second.buyerA(), 80);
        release.countDown();
        assertThat(held.get(20, TimeUnit.SECONDS)).isEqualTo(first.requestA());
        assertAccepted(first, first.requestA(), first.buyerA(), 80);
        assertAccepted(second, second.requestA(), second.buyerA(), 80);
        System.out.println("RF004 before-insert isolation: B-committed-after-A-absent-read-before-A-release=true");
    }

    @Test
    void differentProductsBothPassingAbsentDetailsReadCanBothInsertAndCommit() throws Exception {
        long seller = fixture.user();
        var first = fixture.product(seller, true);
        var second = fixture.product(seller, true);
        CountDownLatch absentReads = latch(2), allowInserts = latch(1);
        Runnable afterAbsentRead = () -> { absentReads.countDown(); await(allowInserts); };
        ExecutorService workers = executor();
        Future<Attempt> a = workers.submit(() -> withHook(HOOKS.afterSold, afterAbsentRead,
                () -> attempt(() -> requests.update(first.requestA(), true))));
        Future<Attempt> b = workers.submit(() -> withHook(HOOKS.afterSold, afterAbsentRead,
                () -> attempt(() -> requests.update(second.requestA(), true))));
        await(absentReads); allowInserts.countDown();
        assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)))
                .allSatisfy(outcome -> assertThat(outcome.failure()).isNull());
        assertAccepted(first, first.requestA(), first.buyerA(), 80);
        assertAccepted(second, second.requestA(), second.buyerA(), 80);
        System.out.println("RF004 dual absent-read before-insert barrier: different-products-both-committed=true");
    }

    @Test
    void realLockTimeoutIsFailureWithoutWritesAndFollowingAcceptanceCanAcquireLock() throws Exception {
        var item = fixture.product(true);
        var before = fixture.state(item);
        CountDownLatch locked = latch(1), release = latch(1);
        ExecutorService workers = executor();
        Future<?> holder = workers.submit(() -> tx.executeWithoutResult(status -> {
            products.lockTransactionProduct(item.product()).orElseThrow();
            locked.countDown(); await(release);
        }));
        await(locked);
        Throwable failure = catchThrowable(() -> requests.update(item.requestA(), true));
        assertThat(failure).isNotNull().isNotInstanceOf(ResponseStatusException.class);
        assertThat(sqlError(failure)).isEqualTo(1205);
        assertThat(fixture.state(item)).isEqualTo(before);
        release.countDown(); holder.get(20, TimeUnit.SECONDS);
        assertThat(requests.update(item.requestA(), true)).isEqualTo(item.requestA());
        assertAccepted(item, item.requestA(), item.buyerA(), 80);
        System.out.println("RF004 intentional lock timeout: mysql-error=1205 no-waiter-writes=true");
    }

    private void assertRollbackAt(ThreadLocal<Runnable> location) {
        var item = fixture.product(true);
        var before = fixture.state(item);
        assertThatThrownBy(() -> withHook(location, () -> { throw new InjectedFailure(); },
                () -> requests.update(item.requestA(), true))).isInstanceOf(InjectedFailure.class);
        assertThat(fixture.state(item)).isEqualTo(before);
        assertThat(requests.update(item.requestA(), true)).isEqualTo(item.requestA());
        assertAccepted(item, item.requestA(), item.buyerA(), 80);
    }

    private void assertAccepted(Rf004MySqlFixture.ProductCase item, long winner, long buyer, long price) {
        assertDetails(item, buyer, price);
        assertThat(fixture.jdbc.queryForObject("SELECT for_sale FROM product WHERE product_id=?", Boolean.class, item.product())).isFalse();
        var states = fixture.jdbc.queryForList("SELECT transaction_request_id,is_accepted FROM transaction_request WHERE product_id=?", item.product());
        assertThat(states).hasSize(2);
        org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
            for (var row : states) {
                long id = ((Number) row.get("transaction_request_id")).longValue();
                softly.assertThat(row.get("is_accepted")).as("request %s acceptance", id).isEqualTo(id == winner);
            }
        });
    }

    private void assertDetails(Rf004MySqlFixture.ProductCase item, long buyer, long price) {
        assertThat(fixture.jdbc.queryForList("SELECT consumer_id,seller_id,price,status_id FROM transaction_details WHERE product_id=?", item.product()))
                .singleElement().satisfies(row -> {
                    assertThat(((Number) row.get("consumer_id")).longValue()).isEqualTo(buyer);
                    assertThat(((Number) row.get("seller_id")).longValue()).isEqualTo(item.seller());
                    assertThat(((Number) row.get("price")).longValue()).isEqualTo(price);
                    assertThat(((Number) row.get("status_id")).longValue()).isEqualTo(1L);
                });
    }

    private List<Attempt> pair(Supplier<?> first, Supplier<?> second) throws Exception {
        CountDownLatch ready = latch(2), go = latch(1);
        ExecutorService workers = executor();
        Runnable waitForBoth = () -> { ready.countDown(); await(go); };
        Future<Attempt> a = workers.submit(() -> withHook(HOOKS.beforeLock, once(waitForBoth), () -> attempt(first)));
        Future<Attempt> b = workers.submit(() -> withHook(HOOKS.beforeLock, once(waitForBoth), () -> attempt(second)));
        await(ready); go.countDown();
        return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
    }

    private static Runnable once(Runnable action) {
        return new Runnable() { private boolean done; public void run() { if (!done) { done = true; action.run(); } } };
    }
    private static Attempt attempt(Supplier<?> action) { try { return new Attempt(action.get(), null); } catch (RuntimeException failure) { return new Attempt(null, failure); } }
    private static <T> T withHook(ThreadLocal<Runnable> hook, Runnable action, Supplier<T> operation) { hook.set(action); try { return operation.get(); } finally { HOOKS.clear(); } }
    private static CreateTransactionDetailsRequestDTO direct(Rf004MySqlFixture.ProductCase item) { return new CreateTransactionDetailsRequestDTO(item.product(), item.buyerA(), 80L); }
    private static void conflict(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) { assertConflict(catchThrowable(action)); }
    private static void assertConflict(Throwable failure) { assertThat(failure).isInstanceOfSatisfying(ResponseStatusException.class, value -> assertThat(value.getStatusCode().value()).isEqualTo(409)); }
    private static void assertOneSuccessOneConflict(List<Attempt> outcomes) { assertThat(outcomes.stream().filter(value -> value.failure() == null).count()).isEqualTo(1); outcomes.stream().filter(value -> value.failure() != null).forEach(value -> assertConflict(value.failure())); }
    private ExecutorService executor() { ExecutorService value = Executors.newFixedThreadPool(2); executors.add(value); return value; }
    private CountDownLatch latch(int size) { CountDownLatch value = new CountDownLatch(size); latches.add(value); return value; }
    private void stopWorkers() throws Exception {
        for (CountDownLatch value : latches) while (value.getCount() > 0) value.countDown();
        for (ExecutorService value : executors) value.shutdownNow();
        for (ExecutorService value : executors) assertThat(value.awaitTermination(20, TimeUnit.SECONDS)).as("all workers stopped before cleanup").isTrue();
        latches.clear(); executors.clear();
    }
    private static void await(CountDownLatch latch) { try { assertThat(latch.await(15, TimeUnit.SECONDS)).as("bounded synchronization").isTrue(); } catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); } }
    private static int sqlError(Throwable failure) { for (Throwable cause = failure; cause != null; cause = cause.getCause()) if (cause instanceof SQLException sql) return sql.getErrorCode(); return 0; }
    private record Attempt(Object result, Throwable failure) { }
    private static final class InjectedFailure extends RuntimeException { }
    private static final class Hooks {
        final ThreadLocal<Runnable> beforeLock = ThreadLocal.withInitial(() -> NO_HOOK);
        final ThreadLocal<Runnable> afterLock = ThreadLocal.withInitial(() -> NO_HOOK);
        final ThreadLocal<Runnable> afterSold = ThreadLocal.withInitial(() -> NO_HOOK);
        final ThreadLocal<Runnable> afterSaved = ThreadLocal.withInitial(() -> NO_HOOK);
        final ThreadLocal<Runnable> afterOthers = ThreadLocal.withInitial(() -> NO_HOOK);
        void clear() { beforeLock.remove(); afterLock.remove(); afterSold.remove(); afterSaved.remove(); afterOthers.remove(); }
    }

    @SuppressWarnings("unchecked")
    private static <T> T observe(Class<T> type, T delegate, EntityManagerFactory emf) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, arguments) -> {
            if (method.getName().equals("lockTransactionProduct")) HOOKS.beforeLock.get().run();
            try {
                Object result = method.invoke(delegate, arguments);
                if (method.getName().equals("lockTransactionProduct")) {
                    var connection = DataSourceUtils.getConnection(fixture.source);
                    try (var statement = connection.createStatement(); var row = statement.executeQuery(
                            "SELECT CONNECTION_ID(),@@transaction_isolation,@@transaction_read_only")) {
                        row.next();
                        assertThat(row.getString(2)).isEqualTo("READ-COMMITTED");
                        assertThat(row.getBoolean(3)).isFalse();
                        assertThat(connection.getAutoCommit()).isFalse();
                        CONNECTIONS.get().add(row.getLong(1));
                    } finally { DataSourceUtils.releaseConnection(connection, fixture.source); }
                    HOOKS.afterLock.get().run();
                }
                if (type == ProductRepository.class && method.getName().equals("save")) {
                    flushForInjectedHook(HOOKS.afterSold, emf);
                }
                if (type == TransactionDetailsRepository.class && method.getName().equals("save")) {
                    TransactionDetails saved = (TransactionDetails) result;
                    fixture.trackDetails(saved.getId(), saved.getProduct().getProductId(), saved.getConsumer().getUserId(),
                            saved.getSeller().getUserId(), saved.getPrice(), saved.getStatus().getId());
                    flushForInjectedHook(HOOKS.afterSaved, emf);
                }
                if (type == TransactionRequestRepository.class && method.getName().equals("save")
                        && Boolean.FALSE.equals(((com.ngng.api.transaction.entity.TransactionRequest) arguments[0]).getIsAccepted())) {
                    flushForInjectedHook(HOOKS.afterOthers, emf);
                }
                return result;
            } catch (InvocationTargetException failure) { throw failure.getCause(); }
        });
    }

    // Normal functional calls retain production flush timing. Only fault/barrier
    // tests flush explicitly to establish the injection point they assert.
    private static void flushForInjectedHook(ThreadLocal<Runnable> hook, EntityManagerFactory emf) {
        Runnable action = hook.get();
        if (action != NO_HOOK) {
            EntityManagerFactoryUtils.getTransactionalEntityManager(emf).flush();
            action.run();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = {TransactionRequestRepository.class, ProductRepository.class, UserRepository.class})
    static class IsolatedJpa {
        @Bean DataSource dataSource() { return Objects.requireNonNull(fixture).source; }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(source);
            factory.setPackagesToScan("com.ngng.api");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate", "hibernate.show_sql", "false",
                    "hibernate.generate_statistics", "true",
                    "hibernate.session_factory.statement_inspector", (org.hibernate.resource.jdbc.spi.StatementInspector) sql -> { SQL.get().add(sql.toLowerCase(java.util.Locale.ROOT)); return sql; },
                    "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
        @Bean AuthService authService(UserRepository users) { return new AuthService(users); }
        @Bean TransactionDetailsService transactionDetailsService(TransactionDetailsRepository details, TransactionStatusRepository statuses,
                                                                 ProductRepository products, AuthService auth, EntityManagerFactory emf) {
            return new TransactionDetailsService(observe(TransactionDetailsRepository.class, details, emf), statuses,
                    observe(ProductRepository.class, products, emf), auth);
        }
        @Bean TransactionRequestService transactionRequestService(TransactionRequestRepository requests, ProductRepository products,
                                                                 TransactionDetailsService details, EntityManagerFactory emf, AuthService auth) {
            return new TransactionRequestService(observe(TransactionRequestRepository.class, requests, emf),
                    observe(ProductRepository.class, products, emf), details, org.mockito.Mockito.mock(com.ngng.api.product.product.service.ProductCacheInvalidator.class), auth);
        }
    }
}
