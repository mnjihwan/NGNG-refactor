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
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
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
class TransactionAcceptanceMySqlConcurrencyTest {
    private static final Hooks HOOKS = new Hooks();
    private static final ThreadLocal<List<ReaderTransaction>> READER_TRANSACTIONS = ThreadLocal.withInitial(ArrayList::new);
    private static final ThreadLocal<List<Mutation>> MUTATIONS = ThreadLocal.withInitial(ArrayList::new);
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
    void stopEveryWorkerBeforeAnyCleanup() throws Exception { HOOKS.clear(); READER_TRANSACTIONS.remove(); MUTATIONS.remove(); stopWorkers(); }

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
        assertThat(requests.update(item.requestA(), true)).isEqualTo(item.requestA());
        conflict(() -> requests.update(item.requestA(), false));
        conflict(() -> requests.update(item.requestB(), true));
        assertThat(fixture.state(item)).isEqualTo(before);
        assertThat(requests.update(Long.MAX_VALUE, true)).isEqualTo(-1L);
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
    void repeatedAcceptanceInsideOneCallerTransactionSeesOwnDetailsAndCommitsOnce() {
        var item = fixture.product(true);
        var before = fixture.state(item);
        tx.executeWithoutResult(status -> {
            assertThat(requests.update(item.requestA(), true)).isEqualTo(item.requestA());
            assertAccepted(item, item.requestA(), item.buyerA(), 80);
            var ownState = fixture.state(item);
            assertThat(requests.update(item.requestA(), true)).isEqualTo(item.requestA());
            assertThat(fixture.state(item)).isEqualTo(ownState);
            assertThat(fixture.committedState(item)).isEqualTo(before);
        });
        assertAccepted(item, item.requestA(), item.buyerA(), 80);
    }

    @Test
    void callerEditingAlreadyManagedProductAfterAcceptanceCannotRestoreStaleSaleState() {
        var item = fixture.product(true);
        tx.executeWithoutResult(status -> {
            Product managed = products.findById(item.product()).orElseThrow();
            assertThat(managed.getForSale()).isTrue();
            requests.update(item.requestA(), true);
            managed.setPurchaseAt(fixture.marker + "_caller_edit");
            EntityManagerFactoryUtils.getTransactionalEntityManager(emf).flush();
        });
        assertThat(fixture.jdbc.queryForObject("SELECT purchase_at FROM product WHERE product_id=?", String.class, item.product()))
                .isEqualTo(fixture.marker + "_caller_edit");
        assertAccepted(item, item.requestA(), item.buyerA(), 80);
    }

    @Test
    void callerEditingAlreadyManagedRequestsAfterAcceptanceCannotRestoreStaleAcceptanceStates() {
        var item = fixture.product(true);
        java.sql.Timestamp firstCreatedAt = java.sql.Timestamp.valueOf("2026-11-05 12:00:00");
        java.sql.Timestamp secondCreatedAt = java.sql.Timestamp.valueOf("2026-11-05 12:01:00");
        tx.executeWithoutResult(status -> {
            var selected = requestRepository.findById(item.requestA()).orElseThrow();
            var sibling = requestRepository.findById(item.requestB()).orElseThrow();
            assertThat(selected.getIsAccepted()).isNull();
            assertThat(sibling.getIsAccepted()).isNull();
            requests.update(item.requestA(), true);
            selected.setCreatedAt(firstCreatedAt);
            sibling.setCreatedAt(secondCreatedAt);
            EntityManagerFactoryUtils.getTransactionalEntityManager(emf).flush();
        });
        assertThat(fixture.jdbc.queryForObject("SELECT created_at FROM transaction_request WHERE transaction_request_id=?",
                java.sql.Timestamp.class, item.requestA())).isEqualTo(firstCreatedAt);
        assertThat(fixture.jdbc.queryForObject("SELECT created_at FROM transaction_request WHERE transaction_request_id=?",
                java.sql.Timestamp.class, item.requestB())).isEqualTo(secondCreatedAt);
        assertAccepted(item, item.requestA(), item.buyerA(), 80);
    }

    @Test
    void pendingCallerChangesBeforeFirstAcceptanceAndLaterEditsAreBothPreserved() {
        var item = fixture.product(true);
        var created = java.sql.Timestamp.valueOf("2026-11-05 13:00:00");
        tx.executeWithoutResult(status -> {
            var em = EntityManagerFactoryUtils.getTransactionalEntityManager(emf);
            Product product = products.findById(item.product()).orElseThrow();
            var selected = requestRepository.findById(item.requestA()).orElseThrow();
            var sibling = requestRepository.findById(item.requestB()).orElseThrow();
            product.setPurchaseAt(fixture.marker + "_pending");
            selected.setCreatedAt(created);
            sibling.setCreatedAt(created);
            requests.update(item.requestA(), true);
            // Flush before a second edit so losing the original pending fields is observable.
            em.flush();
            assertThat(fixture.jdbc.queryForObject("SELECT purchase_at FROM product WHERE product_id=?", String.class, item.product()))
                    .isEqualTo(fixture.marker + "_pending");
            assertThat(fixture.jdbc.queryForList("SELECT created_at FROM transaction_request WHERE product_id=?", java.sql.Timestamp.class, item.product()))
                    .containsExactly(created, created);
            product.setPurchaseAt(fixture.marker + "_later");
            selected.setCreatedAt(java.sql.Timestamp.valueOf("2026-11-05 13:01:00"));
            sibling.setCreatedAt(java.sql.Timestamp.valueOf("2026-11-05 13:02:00"));
            em.flush();
        });
        assertThat(fixture.jdbc.queryForObject("SELECT purchase_at FROM product WHERE product_id=?", String.class, item.product()))
                .isEqualTo(fixture.marker + "_later");
        assertThat(fixture.jdbc.queryForObject("SELECT created_at FROM transaction_request WHERE transaction_request_id=?", java.sql.Timestamp.class, item.requestA()))
                .isEqualTo(java.sql.Timestamp.valueOf("2026-11-05 13:01:00"));
        assertThat(fixture.jdbc.queryForObject("SELECT created_at FROM transaction_request WHERE transaction_request_id=?", java.sql.Timestamp.class, item.requestB()))
                .isEqualTo(java.sql.Timestamp.valueOf("2026-11-05 13:02:00"));
        assertAccepted(item, item.requestA(), item.buyerA(), 80);
    }

    @Test
    void productAffectedZeroStillSynchronizesManagedSaleWithoutAnOrmUpdate() {
        var item = fixture.product(true);
        tx.executeWithoutResult(status -> {
            var em = EntityManagerFactoryUtils.getTransactionalEntityManager(emf);
            Product product = products.findById(item.product()).orElseThrow();
            assertThat(product.getForSale()).isTrue();
            // Only this tracked fixture row changes; the managed object intentionally stays stale.
            assertThat(fixture.jdbc.update("UPDATE product SET for_sale=false WHERE product_id=?", item.product())).isEqualTo(1);
            MUTATIONS.get().clear();
            long updates = entityUpdates();
            requests.update(item.requestA(), true);
            assertThat(MUTATIONS.get()).contains(new Mutation("markTransactionSold", item.product(), 0));
            em.flush();
            assertThat(entityUpdates()).isEqualTo(updates);
            assertThat(product.getForSale()).as("affected=0 must synchronize a stale managed product").isFalse();
            product.setPurchaseAt(fixture.marker + "_zero_later");
            em.flush();
        });
        assertThat(fixture.jdbc.queryForObject("SELECT purchase_at FROM product WHERE product_id=?", String.class, item.product()))
                .isEqualTo(fixture.marker + "_zero_later");
        assertAccepted(item, item.requestA(), item.buyerA(), 80);
    }

    @Test
    void alreadyRejectedRequestSynchronizesStaleManagedStateWithoutNativeOrOrmWrites() {
        var item = fixture.product(true);
        tx.executeWithoutResult(status -> {
            var em = EntityManagerFactoryUtils.getTransactionalEntityManager(emf);
            var selected = requestRepository.findById(item.requestA()).orElseThrow();
            assertThat(selected.getIsAccepted()).isNull();
            assertThat(fixture.jdbc.update("UPDATE transaction_request SET is_accepted=false WHERE transaction_request_id=?", item.requestA())).isEqualTo(1);
            var before = fixture.state(item);
            MUTATIONS.get().clear();
            long updates = entityUpdates();
            requests.update(item.requestA(), false);
            em.flush();
            assertThat(MUTATIONS.get()).isEmpty();
            assertThat(entityUpdates()).isEqualTo(updates);
            assertThat(fixture.state(item)).isEqualTo(before);
            assertThat(selected.getIsAccepted()).as("already-rejected current row must synchronize the managed request").isFalse();
            selected.setCreatedAt(java.sql.Timestamp.valueOf("2026-11-05 13:03:00"));
            em.flush();
        });
        assertThat(fixture.jdbc.queryForObject("SELECT is_accepted FROM transaction_request WHERE transaction_request_id=?", Boolean.class, item.requestA())).isFalse();
        assertThat(fixture.jdbc.queryForObject("SELECT created_at FROM transaction_request WHERE transaction_request_id=?", java.sql.Timestamp.class, item.requestA()))
                .isEqualTo(java.sql.Timestamp.valueOf("2026-11-05 13:03:00"));
    }

    @Test
    void originallyReadOnlyManagedEntitiesKeepTheirReadOnlyFlagsAndCurrentProtectedValues() {
        var item = fixture.product(true);
        tx.executeWithoutResult(status -> {
            var em = EntityManagerFactoryUtils.getTransactionalEntityManager(emf);
            Session session = em.unwrap(Session.class);
            Product product = products.findById(item.product()).orElseThrow();
            var selected = requestRepository.findById(item.requestA()).orElseThrow();
            var sibling = requestRepository.findById(item.requestB()).orElseThrow();
            session.setReadOnly(product, true);
            session.setReadOnly(selected, true);
            session.setReadOnly(sibling, true);
            requests.update(item.requestA(), true);
            assertThat(session.isReadOnly(product)).isTrue();
            assertThat(session.isReadOnly(selected)).isTrue();
            assertThat(session.isReadOnly(sibling)).isTrue();
            org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(product.getForSale()).isFalse();
                softly.assertThat(selected.getIsAccepted()).isTrue();
                softly.assertThat(sibling.getIsAccepted()).isFalse();
            });
        });
        assertAccepted(item, item.requestA(), item.buyerA(), 80);
    }

    @Test
    void uninitializedProductReferenceStaysManagedAndExposesCurrentSaleAfterAcceptance() {
        var item = fixture.product(true);
        tx.executeWithoutResult(status -> {
            var em = EntityManagerFactoryUtils.getTransactionalEntityManager(emf);
            Product reference = em.getReference(Product.class, item.product());
            assertThat(Hibernate.isInitialized(reference)).isFalse();
            requests.update(item.requestA(), true);
            assertThat(em.contains(reference)).isTrue();
            assertThat(reference.getForSale()).isFalse();
            reference.setPurchaseAt(fixture.marker + "_proxy_later");
            em.flush();
        });
        assertThat(fixture.jdbc.queryForObject("SELECT purchase_at FROM product WHERE product_id=?", String.class, item.product()))
                .isEqualTo(fixture.marker + "_proxy_later");
        assertAccepted(item, item.requestA(), item.buyerA(), 80);
    }

    @Test
    void failureRestoresOriginalReadOnlyFlagsWithoutDetachingCallerEntities() {
        var item = fixture.product(true);
        var before = fixture.state(item);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            var em = EntityManagerFactoryUtils.getTransactionalEntityManager(emf);
            Session session = em.unwrap(Session.class);
            Product product = products.findById(item.product()).orElseThrow();
            var selected = requestRepository.findById(item.requestA()).orElseThrow();
            session.setReadOnly(product, true);
            assertThat(session.isReadOnly(selected)).isFalse();
            try {
                withHook(HOOKS.afterSold, () -> { throw new InjectedFailure(); }, () -> requests.update(item.requestA(), true));
                fail("Injected failure must propagate");
            } catch (InjectedFailure expected) {
                assertThat(em.contains(product)).isTrue();
                assertThat(em.contains(selected)).isTrue();
                assertThat(session.isReadOnly(product)).isTrue();
                assertThat(session.isReadOnly(selected)).isFalse();
                throw expected;
            }
        })).isInstanceOf(InjectedFailure.class);
        assertThat(fixture.state(item)).isEqualTo(before);
    }

    @Test
    void repeatAfterExternalCommitWithStaleManagedEntitiesDoesNotIssueAnyWrite() throws Exception {
        assertStaleManagedRepeat(false);
    }

    @Test
    void pendingCallerChangesAfterExternalCommitCannotUndoAcceptedStateDuringAutoFlush() throws Exception {
        assertStaleManagedRepeat(true);
    }

    private long entityUpdates() { return emf.unwrap(SessionFactory.class).getStatistics().getEntityUpdateCount(); }

    private void assertStaleManagedRepeat(boolean pendingChanges) throws Exception {
        var item = fixture.product(true);
        CountDownLatch snapshot = latch(1), committed = latch(1);
        ExecutorService workers = executor();
        Future<?> caller = workers.submit(() -> tx.executeWithoutResult(status -> {
            var em = EntityManagerFactoryUtils.getTransactionalEntityManager(emf);
            Product product = products.findById(item.product()).orElseThrow();
            var selected = requestRepository.findById(item.requestA()).orElseThrow();
            var sibling = requestRepository.findById(item.requestB()).orElseThrow();
            assertThat(product.getForSale()).isTrue();
            assertThat(selected.getIsAccepted()).isNull();
            assertThat(sibling.getIsAccepted()).isNull();
            snapshot.countDown(); await(committed);
            if (pendingChanges) {
                product.setPurchaseAt(fixture.marker + "_stale_pending");
                selected.setCreatedAt(java.sql.Timestamp.valueOf("2026-11-05 13:04:00"));
                sibling.setCreatedAt(java.sql.Timestamp.valueOf("2026-11-05 13:05:00"));
            }
            MUTATIONS.get().clear();
            long updates = entityUpdates();
            assertThat(requests.update(item.requestA(), true)).isEqualTo(item.requestA());
            em.flush();
            if (!pendingChanges) {
                assertThat(MUTATIONS.get()).as("repeat must not call native writers").isEmpty();
                assertThat(entityUpdates()).as("snapshot synchronization must not create an ORM update").isEqualTo(updates);
            }
            org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(product.getForSale()).isFalse();
                softly.assertThat(selected.getIsAccepted()).isTrue();
                softly.assertThat(sibling.getIsAccepted()).isFalse();
            });
        }));
        await(snapshot);
        try {
            requests.update(item.requestA(), true);
            var before = fixture.state(item);
            committed.countDown();
            caller.get(20, TimeUnit.SECONDS);
            if (pendingChanges) {
                assertThat(fixture.jdbc.queryForObject("SELECT purchase_at FROM product WHERE product_id=?", String.class, item.product()))
                        .isEqualTo(fixture.marker + "_stale_pending");
                assertThat(fixture.jdbc.queryForObject("SELECT created_at FROM transaction_request WHERE transaction_request_id=?", java.sql.Timestamp.class, item.requestA()))
                        .isEqualTo(java.sql.Timestamp.valueOf("2026-11-05 13:04:00"));
                assertThat(fixture.jdbc.queryForObject("SELECT created_at FROM transaction_request WHERE transaction_request_id=?", java.sql.Timestamp.class, item.requestB()))
                        .isEqualTo(java.sql.Timestamp.valueOf("2026-11-05 13:05:00"));
            } else assertThat(fixture.state(item)).isEqualTo(before);
            assertAccepted(item, item.requestA(), item.buyerA(), 80);
        } finally { committed.countDown(); }
    }

    @Test
    void repeatedDirectCreateInsideOneCallerTransactionConflictsAndRollsBackTheFirstInsert() {
        var item = fixture.product(true);
        var before = fixture.state(item);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            details.create(direct(item));
            var ownState = fixture.state(item);
            conflict(() -> details.create(direct(item)));
            assertThat(fixture.state(item)).isEqualTo(ownState);
            assertThat(fixture.committedState(item)).isEqualTo(before);
        })).isInstanceOf(UnexpectedRollbackException.class);
        assertThat(fixture.state(item)).isEqualTo(before);
        details.create(direct(item));
        assertDetails(item, item.buyerA(), 80);
    }

    @Test
    void directCreateThenAcceptanceInOneCallerTransactionSeesOwnDetailsAndRollsBackOnConflict() {
        var item = fixture.product(true);
        var before = fixture.state(item);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            details.create(direct(item));
            var ownState = fixture.state(item);
            conflict(() -> requests.update(item.requestA(), true));
            assertThat(fixture.state(item)).isEqualTo(ownState);
            assertThat(fixture.committedState(item)).isEqualTo(before);
        })).isInstanceOf(UnexpectedRollbackException.class);
        assertThat(fixture.state(item)).isEqualTo(before);
        requests.update(item.requestA(), true);
        assertAccepted(item, item.requestA(), item.buyerA(), 80);
    }

    @Test
    void callerRollbackKeepsAllServiceWritesInCallerTransactionAndNextAcceptanceCanProceed() {
        var item = fixture.product(true);
        var before = fixture.state(item);
        tx.executeWithoutResult(status -> {
            requests.update(item.requestA(), true);
            assertAccepted(item, item.requestA(), item.buyerA(), 80);
            assertThat(fixture.committedState(item)).isEqualTo(before);
            status.setRollbackOnly();
        });
        assertThat(fixture.state(item)).isEqualTo(before);
        requests.update(item.requestB(), true);
        assertAccepted(item, item.requestB(), item.buyerB(), 90);
    }

    @Test
    void fixtureRequestsCreatedInsideCallerTransactionAreIncludedInAcceptanceAndSiblingRejection() {
        var item = tx.execute(status -> {
            var own = fixture.product(true);
            assertThat(fixture.committedState(own)).isEmpty();
            requests.update(own.requestA(), true);
            assertAccepted(own, own.requestA(), own.buyerA(), 80);
            var firstState = fixture.state(own);
            requests.update(own.requestA(), true);
            assertThat(fixture.state(own)).isEqualTo(firstState);
            assertThat(fixture.committedState(own)).isEmpty();
            return own;
        });
        assertAccepted(Objects.requireNonNull(item), item.requestA(), item.buyerA(), 80);
    }

    @Test
    void callerRollbackAlsoRemovesFixtureRowsCreatedBeforeAcceptanceInThatTransaction() {
        var item = tx.execute(status -> {
            var own = fixture.product(true);
            requests.update(own.requestA(), true);
            assertAccepted(own, own.requestA(), own.buyerA(), 80);
            assertThat(fixture.committedState(own)).isEmpty();
            status.setRollbackOnly();
            return own;
        });
        assertThat(fixture.state(Objects.requireNonNull(item))).isEmpty();
    }

    @Test
    void committedReaderUsesDifferentReadOnlyRcConnectionAndResumesUncommittedCallerConnection() {
        var item = fixture.product(true);
        var before = fixture.state(item);
        tx.executeWithoutResult(status -> {
            long callerConnection = fixture.jdbc.queryForObject("SELECT CONNECTION_ID()", Long.class);
            ReadTransactionDetailsDTO own = details.create(direct(item));
            assertThat(own.getId()).isPositive();
            READER_TRANSACTIONS.get().clear();
            // Historical reader/outer-transaction expectation retained; the current
            // implementation deliberately has no independent committed-ID reader.
            assertThat(context.getBean(TransactionDetailsRepository.class).findAllByProductProductId(item.product())).isEmpty();
            assertThat(READER_TRANSACTIONS.get()).singleElement().satisfies(reader -> {
                assertThat(reader.error()).isNull();
                assertThat(reader.connectionId()).isNotEqualTo(callerConnection);
                assertThat(reader.isolation()).isEqualTo("READ-COMMITTED");
                assertThat(reader.readOnly()).isTrue();
                assertThat(reader.autoCommit()).isFalse();
            });
            assertThat(fixture.jdbc.queryForObject("SELECT CONNECTION_ID()", Long.class)).isEqualTo(callerConnection);
            assertThat(fixture.committedState(item)).isEqualTo(before);
            assertDetails(item, item.buyerA(), 80);
            status.setRollbackOnly();
        });
        assertThat(fixture.state(item)).isEqualTo(before);
        System.out.println("RF004 committed reader: different-connection=true read-only=true isolation=READ-COMMITTED"
                + " caller-connection-resumed=true caller-write-uncommitted=true caller-rollback=true");
    }

    @Test
    void repeatAcceptanceUsesCurrentStateAfterEarlierRrSnapshotAndManagedEntities() throws Exception {
        assertSnapshotCase(true, false);
    }

    @Test
    void competingAcceptanceUsesCurrentStateAfterEarlierRrSnapshotAndManagedEntities() throws Exception {
        assertSnapshotCase(false, false);
    }

    @Test
    void directCreationUsesCurrentStateAfterEarlierRrSnapshotAndManagedEntities() throws Exception {
        assertSnapshotCase(false, true);
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

    private void assertSnapshotCase(boolean sameRequest, boolean directCreation) throws Exception {
        var item = fixture.product(true);
        CountDownLatch snapshot = latch(1), committed = latch(1);
        ExecutorService workers = executor();
        Future<Attempt> stale = workers.submit(() -> attempt(() -> tx.execute(status -> {
            Product loaded = products.findById(item.product()).orElseThrow();
            assertThat(loaded.getForSale()).isTrue();
            assertThat(requestRepository.findById(item.requestA()).orElseThrow().getIsAccepted()).isNull();
            assertThat(EntityManagerFactoryUtils.getTransactionalEntityManager(emf)
                    .createQuery("SELECT d FROM TransactionDetails d WHERE d.product.productId=:id")
                    .setParameter("id", item.product()).getResultList()).isEmpty();
            snapshot.countDown(); await(committed);
            if (directCreation) return details.create(direct(item));
            return requests.update(sameRequest ? item.requestA() : item.requestB(), true);
        })));
        await(snapshot);
        if (directCreation) details.create(direct(item));
        else requests.update(item.requestA(), true);
        var before = fixture.state(item);
        committed.countDown();
        Attempt outcome = stale.get(20, TimeUnit.SECONDS);
        if (sameRequest) { assertThat(outcome.failure()).isNull(); assertThat(outcome.result()).isEqualTo(item.requestA()); }
        else assertConflict(outcome.failure());
        assertThat(fixture.state(item)).isEqualTo(before);
        assertDetails(item, item.buyerA(), 80);
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
    private record ReaderTransaction(long connectionId, String isolation, boolean readOnly, boolean autoCommit, String error) { }
    private record Mutation(String method, long id, int affected) { }
    private static final class InjectedFailure extends RuntimeException { }
    private static final class Hooks {
        final ThreadLocal<Runnable> beforeLock = ThreadLocal.withInitial(() -> () -> {});
        final ThreadLocal<Runnable> afterLock = ThreadLocal.withInitial(() -> () -> {});
        final ThreadLocal<Runnable> afterSold = ThreadLocal.withInitial(() -> () -> {});
        final ThreadLocal<Runnable> afterSaved = ThreadLocal.withInitial(() -> () -> {});
        final ThreadLocal<Runnable> afterOthers = ThreadLocal.withInitial(() -> () -> {});
        void clear() { beforeLock.remove(); afterLock.remove(); afterSold.remove(); afterSaved.remove(); afterOthers.remove(); }
    }

    @SuppressWarnings("unchecked")
    private static <T> T observe(Class<T> type, T delegate, EntityManagerFactory emf) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, arguments) -> {
            if (method.getName().equals("lockTransactionProduct")) HOOKS.beforeLock.get().run();
            try {
                Object result = method.invoke(delegate, arguments);
                if (method.getName().equals("markTransactionSold") || method.getName().equals("updateAcceptance")) {
                    MUTATIONS.get().add(new Mutation(method.getName(), ((Number) arguments[0]).longValue(), (Integer) result));
                }
                if (method.getName().equals("lockTransactionProduct")) HOOKS.afterLock.get().run();
                if (method.getName().equals("markTransactionSold")) HOOKS.afterSold.get().run();
                if (type == TransactionDetailsRepository.class && method.getName().equals("save")) {
                    TransactionDetails saved = (TransactionDetails) result;
                    fixture.trackDetails(saved.getId(), saved.getProduct().getProductId(), saved.getConsumer().getUserId(),
                            saved.getSeller().getUserId(), saved.getPrice(), saved.getStatus().getId());
                    EntityManagerFactoryUtils.getTransactionalEntityManager(emf).flush();
                    HOOKS.afterSaved.get().run();
                }
                if (method.getName().equals("updateAcceptance") && Boolean.FALSE.equals(arguments[1])) {
                    EntityManagerFactoryUtils.getTransactionalEntityManager(emf).flush();
                    HOOKS.afterOthers.get().run();
                }
                return result;
            } catch (InvocationTargetException failure) { throw failure.getCause(); }
        });
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
                    "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory) {
                @Override protected void doBegin(Object transaction, TransactionDefinition definition) {
                    super.doBegin(transaction, definition);
                    if (!definition.isReadOnly() || definition.getIsolationLevel() != TransactionDefinition.ISOLATION_READ_COMMITTED) return;
                    DataSource source = Objects.requireNonNull(getDataSource());
                    Connection connection = null;
                    try {
                        connection = DataSourceUtils.getConnection(source);
                        try (var statement = connection.createStatement(); var result = statement.executeQuery(
                                "SELECT CONNECTION_ID(),@@transaction_isolation,@@transaction_read_only")) {
                            result.next();
                            READER_TRANSACTIONS.get().add(new ReaderTransaction(result.getLong(1), result.getString(2),
                                    result.getBoolean(3), connection.getAutoCommit(), null));
                        }
                    } catch (Exception failure) {
                        // Observation never changes transaction handling or broadens writes.
                        READER_TRANSACTIONS.get().add(new ReaderTransaction(-1L, null, false, false, failure.getClass().getName()));
                    } finally { if (connection != null) DataSourceUtils.releaseConnection(connection, source); }
                }
            };
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
