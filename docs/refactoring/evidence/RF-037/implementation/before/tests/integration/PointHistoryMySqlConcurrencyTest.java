package com.ngng.api.user.point.integration;

import com.ngng.api.product.product.repository.ProductRepository;
import com.ngng.api.transaction.repository.TransactionDetailsRepository;
import com.ngng.api.user.point.payment.PaymentFixture;


import com.ngng.api.global.security.custom.CustomUserDetails;
import com.ngng.api.user.point.dto.CreateAddPointRequestDTO;
import com.ngng.api.user.point.dto.PaymentPointRequestDto;
import com.ngng.api.user.point.entity.PointHistory;
import com.ngng.api.user.point.repository.PointHistoryRepository;
import com.ngng.api.user.point.service.PointHistoryService;
import com.ngng.api.user.user.entity.Role;
import com.ngng.api.user.user.entity.User;
import com.ngng.api.user.user.repository.UserRepository;
import com.ngng.api.user.user.service.AuthService;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;

/**
 * Explicit opt-in only. No Boot application/configuration, external integrations or DDL.
 * Requires separately verified local process/datadir and approval for one unique role and user/point fixtures.
 */
@EnabledIfEnvironmentVariable(named = "RF003_MYSQL_TEST", matches = "approved-local-shared-role-fixture-only")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PointHistoryMySqlConcurrencyTest {
    private static final String URL = "jdbc:mysql://127.0.0.1:13306/ngng_local?serverTimezone=Asia/Seoul"
            + "&characterEncoding=UTF-8&connectTimeout=3000&socketTimeout=15000";
    private static final Hooks HOOKS = new Hooks();
    private static DataSource verifiedDataSource;
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private PointHistoryService service;
    private UserRepository users;
    private PointHistoryRepository histories;
    private EntityManagerFactory emf;
    private List<Map<String, Object>> originalRoles;
    private Long sharedRoleId;
    private final String runMarker = "rf003_" + UUID.randomUUID().toString().replace("-", "");
    private final String roleMarker = runMarker + "_role";
    private final Map<Long, User> fixtures = new LinkedHashMap<>();
    private final List<ExecutorService> executors = new ArrayList<>();
    private final List<CountDownLatch> latches = new ArrayList<>();
    private int fixtureSequence;
    private int cleanedUsers;
    private int cleanedHistories;

    @BeforeAll
    void verifyTargetBeforeCreatingJpaContextOrWritingAnything() {
        String expectedDataDir = Objects.requireNonNull(System.getenv("RF003_EXPECTED_DATADIR"),
                "A separately verified local datadir is required");
        String password = Objects.requireNonNull(System.getenv("RF003_DB_PASSWORD"), "Local credential required");
        DriverManagerDataSource source = new DriverManagerDataSource(URL, "ngng", password) {
            @Override public Connection getConnection() throws SQLException {
                Connection connection = super.getConnection();
                try (var statement = connection.createStatement()) {
                    // Session-local failure bound; no global setting or schema change.
                    statement.execute("SET SESSION innodb_lock_wait_timeout=5");
                    return connection;
                } catch (SQLException failure) {
                    connection.close();
                    throw failure;
                }
            }
        };
        source.setDriverClassName("com.mysql.cj.jdbc.Driver");
        jdbc = new JdbcTemplate(source);
        Map<String, Object> metadata = jdbc.queryForMap("SELECT DATABASE() db, CURRENT_USER() account, "
                + "VERSION() version, @@port port, @@datadir datadir, @@transaction_isolation isolation_level");
        assertThat(metadata.get("db")).isEqualTo("ngng_local");
        assertThat(metadata.get("account")).isEqualTo("ngng@127.0.0.1");
        assertThat(((Number) metadata.get("port")).intValue()).isEqualTo(13306);
        assertThat(normalize(metadata.get("datadir").toString())).isEqualTo(normalize(expectedDataDir));
        assertThat(metadata.get("isolation_level")).isEqualTo("REPEATABLE-READ");
        assertThat(jdbc.queryForList("SELECT engine FROM information_schema.tables WHERE table_schema=DATABASE() "
                + "AND table_name IN ('user','point_history','user_role')", String.class))
                .hasSize(3).containsOnly("InnoDB");
        // The verified target is currently empty. Refuse to reuse these tests against populated user data.
        assertThat(count("user")).isZero();
        assertThat(count("point_history")).isZero();
        originalRoles = jdbc.queryForList("SELECT role_id, role_type FROM user_role ORDER BY role_id");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_role WHERE role_type=?", Long.class,
                roleMarker)).isZero();
        verifiedDataSource = source;
        context = new AnnotationConfigApplicationContext();
        context.register(IsolatedJpa.class);
        context.refresh();
        tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        service = context.getBean(PointHistoryService.class);
        users = context.getBean(UserRepository.class);
        histories = context.getBean(PointHistoryRepository.class);
        emf = context.getBean(EntityManagerFactory.class);
        tx.executeWithoutResult(status -> {
            GeneratedKeyHolder key = new GeneratedKeyHolder();
            assertThat(jdbc.update(connection -> {
                var statement = connection.prepareStatement("INSERT INTO user_role (role_type) VALUES (?)",
                        Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, roleMarker);
                return statement;
            }, key)).isEqualTo(1);
            // Track the generated identity before commit so a later setup failure cannot lose it.
            sharedRoleId = Objects.requireNonNull(key.getKey()).longValue();
            assertThat(sharedRoleId).isPositive();
            assertOwnedRole();
        });
        System.out.println("RF003 verified: db=ngng_local port=13306 account=ngng@127.0.0.1 version="
                + metadata.get("version") + " isolation=REPEATABLE-READ InnoDB datadir-match=true shared-role="
                + (sharedRoleId != null) + " role-id=" + sharedRoleId + " role-marker=" + roleMarker
                + " original-role-rows=" + originalRoles.size() + " run=" + runMarker);
    }

    @AfterEach
    void stopWorkersBeforeDeletingOnlyVerifiedFixtureRows() throws Exception {
        stopWorkers();
        if (tx == null) return;
        tx.executeWithoutResult(status -> {
            for (User fixture : fixtures.values()) {
                List<Map<String, Object>> found = jdbc.queryForList(
                        "SELECT email, name, role_id FROM user WHERE user_id=?", fixture.getUserId());
                assertThat(found).singleElement().satisfies(row -> {
                    assertThat(row.get("email")).isEqualTo(fixture.getEmail());
                    assertThat(row.get("name")).isEqualTo(fixture.getName());
                    assertThat(((Number) row.get("role_id")).longValue()).isEqualTo(sharedRoleId);
                });
                assertThat(fixture.getEmail()).startsWith(runMarker + "_");
                assertThat(fixture.getName()).startsWith(runMarker + "_");
                for (Map<String, Object> row : jdbc.queryForList(
                        "SELECT point_id, type_detail FROM point_history WHERE user_id=?", fixture.getUserId())) {
                    String detail = (String) row.get("type_detail");
                    assertThat(detail).startsWith(runMarker + "_");
                    assertThat(jdbc.update("DELETE FROM point_history WHERE point_id=? AND user_id=? AND type_detail=?",
                            row.get("point_id"), fixture.getUserId(), detail)).isEqualTo(1);
                    cleanedHistories++;
                }
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM point_history WHERE user_id=?", Long.class,
                        fixture.getUserId())).isZero();
                assertThat(jdbc.update("DELETE FROM user WHERE user_id=? AND email=? AND name=? AND role_id=?",
                        fixture.getUserId(), fixture.getEmail(), fixture.getName(), sharedRoleId)).isEqualTo(1);
                cleanedUsers++;
            }
        });
        fixtures.clear();
    }

    private void stopWorkers() throws InterruptedException {
        for (CountDownLatch latch : latches) while (latch.getCount() > 0) latch.countDown();
        for (ExecutorService executor : executors) executor.shutdownNow();
        for (ExecutorService executor : executors) {
            assertThat(executor.awaitTermination(20, TimeUnit.SECONDS))
                    .as("workers must stop before fixture cleanup").isTrue();
        }
        executors.clear();
        latches.clear();
    }

    @AfterAll
    void confirmCleanupAndUnchangedRoleRows() throws Exception {
        try {
            stopWorkers();
            if (tx != null && sharedRoleId != null) {
                // A setup/fixture cleanup failure must not broaden deletion beyond the owned role.
                assertThat(fixtures).isEmpty();
                tx.executeWithoutResult(status -> {
                    assertThat(count("user")).isZero();
                    assertThat(count("point_history")).isZero();
                    assertOwnedRole();
                    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE role_id=?", Long.class,
                            sharedRoleId)).isZero();
                    assertThat(jdbc.update("DELETE FROM user_role WHERE role_id=? AND role_type=?",
                            sharedRoleId, roleMarker)).isEqualTo(1);
                });
                assertThat(count("user")).isZero();
                assertThat(count("point_history")).isZero();
                assertThat(jdbc.queryForList("SELECT role_id, role_type FROM user_role ORDER BY role_id"))
                        .isEqualTo(originalRoles);
                System.out.println("RF003 cleanup verified: users=" + cleanedUsers + " histories=" + cleanedHistories
                        + " roles=1 role-id=" + sharedRoleId + " role-marker=" + roleMarker
                        + " remaining-users=0 remaining-histories=0 role-rows-unchanged=true run=" + runMarker);
            }
        } finally {
            if (context != null) context.close();
            verifiedDataSource = null;
        }
    }

    @Test
    void formerReadComputeAppendAlgorithmReproducesLostChargeWithBothReadsCompleted() throws Exception {
        User user = fixture(1000);
        CountDownLatch bothRead = latch(2), allowSave = latch(1);
        ExecutorService executor = executor();
        Future<PointHistory> first = executor.submit(() -> as(user, () -> oldCharge(user, 100, bothRead, allowSave)));
        Future<PointHistory> second = executor.submit(() -> as(user, () -> oldCharge(user, 200, bothRead, allowSave)));
        await(bothRead);
        allowSave.countDown();
        assertThat(first.get(20, TimeUnit.SECONDS).getBeforeCost()).isEqualTo(1000);
        assertThat(second.get(20, TimeUnit.SECONDS).getBeforeCost()).isEqualTo(1000);
        List<Row> rows = rows(user);
        assertThat(rows).hasSize(3);
        assertThat(rows.get(2).cost()).isIn(1100L, 1200L).isNotEqualTo(1300L);
        System.out.println("RF003 legacy reproduction: both-before=1000 latest=" + rows.get(2).cost() + " expected=1300");
    }

    @Test
    void concurrentChargesKeepCompleteBalanceChain() throws Exception {
        User user = fixture(1000);
        assertSuccesses(pair(user, () -> charge(100), () -> charge(200)), 2);
        assertChain(user, 1300, 3);
    }

    @Test
    void concurrentPaymentsWithEnoughBalanceKeepCompleteChain() throws Exception {
        User user = fixture(1000);
        assertSuccesses(pair(user, () -> pay(100), () -> pay(200)), 2);
        assertChain(user, 700, 3);
    }

    @Test
    void concurrentInsufficientPaymentsAllowOnlyOneEvenWhenBothChecksPassed() throws Exception {
        User user = fixture(100);
        assertThat(as(user, () -> service.isPaymnet(80L))).isTrue();
        assertThat(as(user, () -> service.isPaymnet(80L))).isTrue();
        List<Attempt> outcomes = pair(user, () -> pay(80), () -> pay(80));
        assertSuccesses(outcomes, 1);
        assertThat(outcomes.stream().filter(value -> value.rejection() != null).toList()).singleElement()
                .satisfies(value -> assertThat(value.rejection().getStatusCode().value()).isEqualTo(400));
        assertChain(user, 20, 2);
    }

    @Test
    void concurrentChargeAndPaymentKeepCompleteChain() throws Exception {
        User user = fixture(1000);
        assertSuccesses(pair(user, () -> charge(100), () -> pay(200)), 2);
        assertChain(user, 900, 3);
    }

    @Test
    void mixedInsufficientBoundaryMatchesActualSerialOutcome() throws Exception {
        User user = fixture(100);
        List<Attempt> outcomes = pair(user, () -> charge(100), () -> pay(150));
        assertThat(outcomes.get(0).rejection()).isNull();
        if (outcomes.get(1).rejection() == null) assertChain(user, 50, 3);
        else {
            assertThat(outcomes.get(1).rejection().getStatusCode().value()).isEqualTo(400);
            assertChain(user, 200, 2);
        }
    }

    @Test
    void waitingWriterUsesCurrentBalanceDespiteEarlierRrSnapshotAndManagedHistory() throws Exception {
        User user = fixture(1000);
        CountDownLatch bothAuthenticated = latch(2), allowA = latch(1), allowB = latch(1);
        CountDownLatch aFlushed = latch(1), allowACommit = latch(1), bAttemptsLock = latch(1);
        ExecutorService executor = executor();
        Future<PointHistory> first = executor.submit(() -> as(user, () -> charge(100), () -> {
            bothAuthenticated.countDown(); await(allowA);
        }, () -> { aFlushed.countDown(); await(allowACommit); }, () -> {}));
        Future<PointHistory> second = executor.submit(() -> as(user, () -> charge(200), () -> {
            // Same EntityManager and RR transaction: deliberately cache the old entity and snapshot.
            assertThat(service.readCostByUser(user).getCost()).isEqualTo(1000);
            bothAuthenticated.countDown(); await(allowB);
        }, () -> {}, bAttemptsLock::countDown));
        await(bothAuthenticated);
        allowA.countDown(); await(aFlushed);
        allowB.countDown(); await(bAttemptsLock);
        assertThat(second.isDone()).isFalse();
        allowACommit.countDown();
        assertThat(first.get(20, TimeUnit.SECONDS).getCost()).isEqualTo(1100);
        assertThat(second.get(20, TimeUnit.SECONDS).getBeforeCost()).isEqualTo(1100);
        assertChain(user, 1300, 3);
    }

    @Test
    void sameRoleUserCompletesWhileFirstUserHoldsItsLockAndUncommittedAppend() throws Exception {
        User firstUser = fixture(1000), secondUser = fixture(1000);
        assertThat(firstUser.getUserId()).isNotEqualTo(secondUser.getUserId());
        assertSharedRole(firstUser);
        assertSharedRole(secondUser);
        CountDownLatch firstFlushed = latch(1), allowFirstCommit = latch(1);
        ExecutorService executor = executor();
        Future<PointHistory> first = executor.submit(() -> as(firstUser, () -> charge(100), () -> {}, () -> {
            firstFlushed.countDown(); await(allowFirstCommit);
        }, () -> {}));
        await(firstFlushed);
        Future<PointHistory> second = executor.submit(() -> as(secondUser, () -> charge(200)));
        assertThat(second.get(20, TimeUnit.SECONDS).getCost()).isEqualTo(1200);
        assertThat(first.isDone()).isFalse();
        assertChain(firstUser, 1000, 1);
        assertChain(secondUser, 1200, 2);
        System.out.println("RF003 shared-role independence: role-id=" + sharedRoleId + " role-marker=" + roleMarker
                + " first-user=" + firstUser.getUserId() + " second-user=" + secondUser.getUserId()
                + " both-authenticated-role-match=true B-committed=1200 A-uncommitted=true run=" + runMarker);
        allowFirstCommit.countDown();
        first.get(20, TimeUnit.SECONDS);
        assertChain(firstUser, 1100, 2);
        assertChain(secondUser, 1200, 2);
    }

    @Test
    void flushedAppendRollsBackOnFailureAndNextRequestAcquiresLock() {
        User user = fixture(1000);
        RuntimeException failure = new RuntimeException("RF003 intentional failure after flush");
        assertThatThrownBy(() -> as(user, () -> pay(100), () -> {}, () -> { throw failure; }, () -> {}))
                .isSameAs(failure);
        assertChain(user, 1000, 1);
        as(user, () -> charge(100));
        assertChain(user, 1100, 2);
    }

    @Test
    void joinedTransactionRollbackOnlyDiscardsAppendAndReleasesLock() {
        User user = fixture(1000);
        as(user, () -> tx.execute(status -> {
            PointHistory result = pay(100);
            EntityManagerFactoryUtils.getTransactionalEntityManager(emf).flush();
            status.setRollbackOnly();
            return result;
        }));
        assertChain(user, 1000, 1);
        as(user, () -> pay(100));
        assertChain(user, 900, 2);
    }

    @Test
    void lockTimeoutRejectsWaitingPaymentWithoutAppendAndAllowsNextRequest() throws Exception {
        User user = fixture(1000);
        CountDownLatch flushed = latch(1), release = latch(1);
        ExecutorService executor = executor();
        Future<PointHistory> holder = executor.submit(() -> as(user, () -> charge(100), () -> {}, () -> {
            flushed.countDown(); await(release);
        }, () -> {}));
        await(flushed);
        Future<Throwable> waiter = executor.submit(() -> {
            try { as(user, () -> pay(100)); return null; }
            catch (RuntimeException failure) { return failure; }
        });
        Throwable failure = waiter.get(20, TimeUnit.SECONDS);
        assertThat(failure).isNotNull();
        assertThat(sqlError(failure)).isEqualTo(1205);
        release.countDown(); holder.get(20, TimeUnit.SECONDS);
        assertChain(user, 1100, 2);
        as(user, () -> pay(100));
        assertChain(user, 1000, 3);
        System.out.println("RF003 intentional lock timeout: mysql-error=1205 no-waiter-append=true");
    }

    @Test
    void realDatabasePoliciesRejectWithoutWritesAndAllowExactMaximumBalancePayment() {
        User user = fixture(Long.MAX_VALUE);
        for (Long invalid : Arrays.asList(null, 0L, -1L, Long.MIN_VALUE)) {
            assertBadRequest(() -> as(user, () -> service.updateCost(new CreateAddPointRequestDTO(invalid, "충전"))));
            assertBadRequest(() -> as(user, () -> service.payment(new PaymentPointRequestDto(invalid, 42L))));
        }
        assertBadRequest(() -> as(user, () -> charge(1)));
        assertChain(user, Long.MAX_VALUE, 1);
        assertThat(as(user, () -> service.isPaymnet(Long.MAX_VALUE))).isTrue();
        assertThat(as(user, () -> service.isPaymnet(0L))).isFalse();
        assertThat(as(user, () -> pay(Long.MAX_VALUE)).getCost()).isZero();
        assertBadRequest(() -> as(user, () -> pay(1)));
        assertChain(user, 0, 2);
    }

    @Test
    void repeatedRequestsRemainIndependentAndExistingSingleAndBatchReadsAgree() {
        User first = fixture(1000), second = fixture(2000);
        as(first, () -> pay(200));
        as(first, () -> pay(200));
        as(second, () -> charge(100));
        assertChain(first, 600, 3);
        assertChain(second, 2100, 2);
        tx.executeWithoutResult(status -> {
            List<PointHistory> batch = service.readCostsByUserIds(List.of(second.getUserId(), first.getUserId()));
            assertThat(batch).hasSize(2);
            for (PointHistory history : batch) {
                PointHistory single = service.readCostByUser(history.getUser());
                assertThat(history.getId()).isEqualTo(single.getId());
                assertThat(history.getCost()).isEqualTo(single.getCost());
            }
        });
    }

    private User fixture(long initialBalance) {
        assertOwnedRole();
        String name = runMarker + "_" + (++fixtureSequence);
        User user = tx.execute(status -> {
            User created = User.builder().name(name).email(name + "@example.invalid").visible(true).build();
            created.setRole(EntityManagerFactoryUtils.getTransactionalEntityManager(emf)
                    .getReference(Role.class, sharedRoleId));
            users.saveAndFlush(created);
            histories.saveAndFlush(PointHistory.builder().user(created).beforeCost(0L).addCost(initialBalance)
                    .cost(initialBalance).type("RF003 fixture").typeDetail(name + " initial balance").build());
            return created;
        });
        fixtures.put(user.getUserId(), user);
        return user;
    }

    private void assertOwnedRole() {
        assertThat(sharedRoleId).isNotNull();
        assertThat(jdbc.queryForList("SELECT role_type FROM user_role WHERE role_id=?", String.class, sharedRoleId))
                .containsExactly(roleMarker);
    }

    private void assertSharedRole(User user) {
        assertThat(jdbc.queryForObject("SELECT role_id FROM user WHERE user_id=?", Long.class, user.getUserId()))
                .isEqualTo(sharedRoleId);
        as(user, () -> {
            User authenticated = context.getBean(AuthService.class).readAuthUser();
            assertThat(authenticated.getRole()).isNotNull();
            assertThat(authenticated.getRole().getRoleId()).isEqualTo(sharedRoleId);
            assertThat(authenticated.getRole().getRoleType()).isEqualTo(roleMarker);
            return null;
        });
    }

    private PointHistory charge(long amount) { return service.updateCost(new CreateAddPointRequestDTO(amount, "충전")); }
    private PointHistory pay(long amount) { return service.payment(new PaymentPointRequestDto(amount, 42L)); }

    private PointHistory oldCharge(User user, long amount, CountDownLatch read, CountDownLatch save) {
        return tx.execute(status -> {
            User authenticated = context.getBean(AuthService.class).readAuthUser();
            PointHistory last = service.readCostByUser(authenticated);
            read.countDown(); await(save);
            return histories.saveAndFlush(PointHistory.builder().user(authenticated).beforeCost(last.getCost())
                    .addCost(amount).cost(last.getCost() + amount).type("충전")
                    .typeDetail(user.getName() + " legacy charge").build());
        });
    }

    private List<Attempt> pair(User user, Supplier<PointHistory> first, Supplier<PointHistory> second) throws Exception {
        CountDownLatch authenticated = latch(2), go = latch(1);
        Runnable authHook = () -> { authenticated.countDown(); await(go); };
        ExecutorService executor = executor();
        Future<Attempt> a = executor.submit(() -> attempt(user, first, authHook));
        Future<Attempt> b = executor.submit(() -> attempt(user, second, authHook));
        await(authenticated); go.countDown();
        return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
    }

    private Attempt attempt(User user, Supplier<PointHistory> action, Runnable authHook) {
        try { return new Attempt(as(user, action, authHook, () -> {}, () -> {}), null); }
        catch (ResponseStatusException rejection) { return new Attempt(null, rejection); }
    }

    private <T> T as(User user, Supplier<T> action) { return as(user, action, () -> {}, () -> {}, () -> {}); }
    private <T> T as(User user, Supplier<T> action, Runnable afterAuth, Runnable afterSave, Runnable beforeLock) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CustomUserDetails(User.builder().email(user.getEmail()).build()), null, List.of()));
        HOOKS.afterAuth.set(afterAuth); HOOKS.afterSave.set(afterSave); HOOKS.beforeLock.set(beforeLock);
        try { return action.get(); }
        finally { SecurityContextHolder.clearContext(); HOOKS.clear(); }
    }

    private List<Row> rows(User user) {
        return jdbc.query("SELECT point_id,before_cost,add_cost,cost,type FROM point_history WHERE user_id=? ORDER BY point_id",
                (rs, row) -> new Row(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getString(5)), user.getUserId());
    }

    private void assertChain(User user, long expected, int size) {
        List<Row> rows = rows(user);
        assertThat(rows).hasSize(size);
        for (int index = 1; index < rows.size(); index++) {
            Row row = rows.get(index);
            assertThat(row.before()).isEqualTo(rows.get(index - 1).cost());
            assertThat(row.cost()).isEqualTo(row.type().equals("결제") ? row.before() - row.add() : row.before() + row.add());
        }
        assertThat(rows.get(rows.size() - 1).cost()).isEqualTo(expected);
    }

    private void assertSuccesses(List<Attempt> attempts, long expected) {
        assertThat(attempts.stream().filter(value -> value.rejection() == null).count()).isEqualTo(expected);
    }
    private void assertBadRequest(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ResponseStatusException.class,
                failure -> assertThat(failure.getStatusCode().value()).isEqualTo(400));
    }
    private long count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM `" + table + "`", Long.class); }
    private CountDownLatch latch(int size) { CountDownLatch latch = new CountDownLatch(size); latches.add(latch); return latch; }
    private ExecutorService executor() { ExecutorService executor = Executors.newFixedThreadPool(2); executors.add(executor); return executor; }
    private static void await(CountDownLatch latch) {
        try { assertThat(latch.await(15, TimeUnit.SECONDS)).as("bounded synchronization").isTrue(); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
    }
    private static String normalize(String path) { return path.replace('\\', '/').replaceAll("/+$", "").toLowerCase(Locale.ROOT); }
    private static int sqlError(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause())
            if (cause instanceof SQLException sql) return sql.getErrorCode();
        return 0;
    }
    private record Row(long id, long before, long add, long cost, String type) { }
    private record Attempt(PointHistory value, ResponseStatusException rejection) { }

    private static class Hooks {
        final ThreadLocal<Runnable> afterAuth = ThreadLocal.withInitial(() -> () -> {});
        final ThreadLocal<Runnable> afterSave = ThreadLocal.withInitial(() -> () -> {});
        final ThreadLocal<Runnable> beforeLock = ThreadLocal.withInitial(() -> () -> {});
        void clear() { afterAuth.remove(); afterSave.remove(); beforeLock.remove(); }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = {PointHistoryRepository.class, UserRepository.class})
    static class IsolatedJpa {
        @Bean DataSource dataSource() { return Objects.requireNonNull(verifiedDataSource); }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
            LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(source);
            factory.setPackagesToScan("com.ngng.api.user.point.entity", "com.ngng.api.user.user.entity");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate",
                    "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy",
                    "hibernate.show_sql", "false"));
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) { return new JpaTransactionManager(factory); }
        @Bean AuthService authService(UserRepository users) {
            return new AuthService(users) {
                @Override public User readAuthUser() {
                    User user = super.readAuthUser();
                    HOOKS.afterAuth.get().run();
                    return user;
                }
            };
        }
        @Bean PointHistoryService pointHistoryService(PointHistoryRepository histories, UserRepository users,
                                                     AuthService auth, EntityManagerFactory factory) {
            PointHistoryRepository observedHistories = (PointHistoryRepository) Proxy.newProxyInstance(
                    PointHistoryRepository.class.getClassLoader(), new Class<?>[]{PointHistoryRepository.class},
                    (proxy, method, arguments) -> {
                        try {
                            Object result = method.invoke(histories, arguments);
                            if (method.getName().equals("save")) {
                                EntityManagerFactoryUtils.getTransactionalEntityManager(factory).flush();
                                HOOKS.afterSave.get().run();
                            }
                            return result;
                        } catch (InvocationTargetException failure) { throw failure.getCause(); }
                    });
            UserRepository observedUsers = (UserRepository) Proxy.newProxyInstance(UserRepository.class.getClassLoader(),
                    new Class<?>[]{UserRepository.class}, (proxy, method, arguments) -> {
                        if (method.getName().equals("lockUserIdForPointUpdate")) HOOKS.beforeLock.get().run();
                        try { return method.invoke(users, arguments); }
                        catch (InvocationTargetException failure) { throw failure.getCause(); }
                    });
            // RF035: compile compatibility only. This opt-in fixture has no product/transaction rows.
            // Before re-running, provision approved owned fixtures and use real locking repositories.
            return new PointHistoryService(observedHistories, auth, observedUsers,
                    org.mockito.Mockito.mock(ProductRepository.class), org.mockito.Mockito.mock(TransactionDetailsRepository.class));
        }
    }
}
