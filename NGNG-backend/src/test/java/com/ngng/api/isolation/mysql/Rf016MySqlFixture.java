package com.ngng.api.isolation.mysql;

import com.ngng.api.global.security.custom.CustomUserDetails;
import com.ngng.api.product.product.repository.ProductRepository;
import com.ngng.api.transaction.repository.TransactionDetailsRepository;
import com.ngng.api.user.point.dto.CreateAddPointRequestDTO;
import com.ngng.api.user.point.entity.PointHistory;
import com.ngng.api.user.point.repository.PointHistoryRepository;
import com.ngng.api.user.point.repository.PaymentSettlementRepository;
import com.ngng.api.user.point.payment.PaymentVerificationGate;
import com.ngng.api.user.point.service.PointHistoryService;
import com.ngng.api.user.user.entity.Role;
import com.ngng.api.user.user.entity.User;
import com.ngng.api.user.user.repository.UserRepository;
import com.ngng.api.user.user.service.AuthService;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactoryBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.*;

import javax.sql.DataSource;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

import static com.ngng.api.isolation.mysql.Rf016MySqlSafetyGate.*;
import static com.ngng.api.isolation.Rf016UnusedPaymentCollaborator.unused;

/** Explicit opt-in physical fixture. Constructing it is a DB connection and must only happen
 * after host/environment review. No Boot/YAML, broad scanning, DDL or network integrations. */
final class Rf016MySqlFixture implements AutoCloseable {
    final String marker = "rf016_" + UUID.randomUUID().toString().replace("-", "");
    final Ledger ledger;
    final DataSource source;
    final JdbcTemplate jdbc;
    final TransactionTemplate setupTx;
    final List<Map<String, Object>> roles;
    final List<Map<String, Object>> references;
    final String expectedDir;
    final long roleId;
    AnnotationConfigApplicationContext context;
    PointHistoryService service;
    boolean intentionalRollback;
    long serviceConnectionId;
    Long lastSavedPointId;
    boolean writesStarted;
    final Set<Long> rolledBackPoints = new HashSet<>();
    final Set<Long> rolledBackUsers = new HashSet<>();
    boolean cleanupLocksVerified;
    final boolean readOnly;

    Rf016MySqlFixture(Map<String, String> environment) throws Exception {
        this(environment, false);
    }
    Rf016MySqlFixture(Map<String, String> environment, boolean readOnly) throws Exception {
        this.readOnly = readOnly;
        endpoint(environment.get("RF016_MYSQL_TEST"), URL, readOnly);
        Path journal = Path.of(required(environment, "RF016_LEDGER_FILE")).toAbsolutePath().normalize();
        Path privateRoot = Path.of(System.getProperty("user.dir")).getParent().resolve("local/rf016-results").toRealPath();
        require(journal.getParent().toRealPath().startsWith(privateRoot) && !Files.exists(journal), "RF016 new private ledger path required");
        ledger = new Ledger(row -> {
            try {
                byte[] bytes = (new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of(
                        "run", marker, "table", row.table(), "pk", row.id(), "identity", row.identity(), "state", "inserted-before-commit")) + "\n").getBytes(StandardCharsets.UTF_8);
                try (FileChannel file = FileChannel.open(journal, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
                    ByteBuffer buffer = ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) file.write(buffer); file.force(true);
                }
            } catch (Exception failure) { throw new IllegalStateException("RF016 private ledger persistence failed (details withheld)"); }
        });
        expectedDir = required(environment, "RF016_EXPECTED_DATADIR");
        String executable = required(environment, "RF016_EXPECTED_MYSQLD");
        long pid = Long.parseLong(required(environment, "RF016_EXPECTED_PID"));
        require(Files.isDirectory(Path.of(expectedDir)) && Files.isRegularFile(Path.of(executable)), "RF016 expected host files missing");
        Runnable verifyHost = () -> verifyProcess(pid, executable, expectedDir);
        verifyHost.run(); // No connection until the live process/listener identity has matched.
        DriverManagerDataSource dataSource = new DriverManagerDataSource(URL, "ngng", required(environment, "RF016_DB_PASSWORD")) {
            @Override public Connection getConnection() throws SQLException {
                verifyHost.run();
                Connection connection = super.getConnection();
                try {
                    if (readOnly) {
                        try (Statement session = connection.createStatement()) { session.execute("SET SESSION TRANSACTION READ ONLY"); }
                        require(new JdbcTemplate(new SingleConnectionDataSource(connection, true)).queryForObject("SELECT @@transaction_read_only", Boolean.class),
                                "RF016 server-side read-only session required");
                    }
                    Map<String, Object> metadata = new JdbcTemplate(new SingleConnectionDataSource(connection, true)).queryForMap(
                            "SELECT DATABASE() db,CURRENT_USER() account,VERSION() version,@@port port,@@datadir datadir,@@transaction_isolation isolation_level");
                    database(metadata, expectedDir);
                    JdbcTemplate audit = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
                    // Read internally only. Schema grants/filtered metadata cannot prove absence.
                    metadataVisibility(audit.query("SHOW GRANTS FOR CURRENT_USER", (rs, row) -> rs.getString(1)));
                    Map<String, String> variables = new HashMap<>();
                    audit.query("SHOW GLOBAL VARIABLES WHERE Variable_name IN ('event_scheduler','persisted_globals_load')", rs -> {
                        variables.put(rs.getString(1), rs.getString(2));
                    });
                    serverRuntime(variables, audit.queryForList("SHOW REPLICA STATUS").size(),
                            audit.queryForObject("SELECT COUNT(*) FROM performance_schema.replication_connection_configuration", Long.class),
                            audit.queryForObject("SELECT COUNT(*) FROM performance_schema.replication_group_members", Long.class));
                    return connection;
                } catch (RuntimeException | SQLException failure) { connection.close(); throw failure; }
            }
        };
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        source = dataSource;
        jdbc = new JdbcTemplate(source);
        setupTx = new TransactionTemplate(new DataSourceTransactionManager(source));
        for (String table : List.of("user", "point_history", "user_role")) {
            require(jdbc.queryForList("SELECT engine FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?",
                    String.class, table).equals(List.of("InnoDB")), "RF016 missing InnoDB fixture table");
            String key = table.equals("user_role") ? "role_id" : KEYS.get(table);
            require(jdbc.queryForList("SELECT column_name FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND table_name=? AND constraint_name='PRIMARY' ORDER BY ordinal_position",
                    String.class, table).equals(List.of(key)), "RF016 fixture PK mismatch");
        }
        require(count("user") == 0 && count("point_history") == 0, "RF016 user/point tables must initially be empty");
        roles = roleRows();
        List<Long> userRoles = jdbc.queryForList("SELECT role_id FROM user_role WHERE role_type='USER'", Long.class);
        require(userRoles.size() == 1, "RF016 requires one existing USER role; will not create/update role");
        roleId = userRoles.get(0);
        references = readReferences();
        require(references.stream().anyMatch(r -> "point_history".equals(r.get("table_name")) && "user_id".equals(r.get("column_name"))
                && "user".equals(r.get("referenced_table_name")) && "user_id".equals(r.get("referenced_column_name"))), "RF016 point/user FK missing");
        require(references.stream().anyMatch(r -> "user".equals(r.get("table_name")) && "role_id".equals(r.get("column_name"))
                && "user_role".equals(r.get("referenced_table_name")) && "role_id".equals(r.get("referenced_column_name"))), "RF016 user/role FK missing");
        schemaSafety();
        System.out.println("RF016 DB preflight verified=true run=" + marker + " role-count=" + roles.size());
    }
    static String required(Map<String, String> environment, String key) {
        String value = environment.get(key); require(value != null && !value.isBlank(), "RF016 required environment key missing: " + key); return value;
    }
    static void verifyProcess(long expectedPid, String executable, String datadir) {
        try {
            ProcessHandle process = ProcessHandle.of(expectedPid).orElseThrow(() -> new IllegalStateException("RF016 expected process absent"));
            ProcessHandle.Info info = process.info();
            Process probe = new ProcessBuilder("netstat", "-ano", "-p", "tcp").redirectErrorStream(true).start();
            String output = new String(probe.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            require(probe.waitFor() == 0, "RF016 listener query failed");
            List<String> listeners = new ArrayList<>();
            for (String line : output.split("\\R")) {
                String[] fields = line.trim().split("\\s+");
                if (fields.length >= 5 && fields[1].endsWith(":13306") && fields[3].equals("LISTENING"))
                    listeners.add(fields[1] + ":" + fields[4]);
            }
            host(expectedPid, executable, datadir, process.pid(), info.command().orElse(""),
                    Arrays.asList(info.arguments().orElseThrow()), listeners);
        } catch (Exception failure) {
            // Never expose process arguments, personal paths, JDBC causes or credentials in shared XML.
            throw new IllegalStateException("RF016 host identity verification failed (details withheld)");
        }
    }
    void startJpa() {
        require(!readOnly, "RF016 read-only preflight may not create JPA context");
        context = new AnnotationConfigApplicationContext();
        context.registerBean(Rf016MySqlFixture.class, () -> this);
        context.register(JpaConfiguration.class);
        try { context.refresh(); } catch (RuntimeException failure) { context.close(); throw failure; }
        service = context.getBean(PointHistoryService.class);
        require(org.springframework.aop.support.AopUtils.isAopProxy(service), "RF016 actual service transaction proxy required");
    }
    long count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + quote(table), Long.class); }
    List<Map<String, Object>> roleRows() { return jdbc.queryForList("SELECT * FROM user_role ORDER BY role_id"); }
    List<Map<String, Object>> readReferences() {
        return jdbc.queryForList("SELECT k.table_schema,k.table_name,k.column_name,k.referenced_table_name,k.referenced_column_name,r.delete_rule "
                + "FROM information_schema.key_column_usage k JOIN information_schema.referential_constraints r "
                + "ON r.constraint_schema=k.constraint_schema AND r.constraint_name=k.constraint_name AND r.table_name=k.table_name "
                + "WHERE k.referenced_table_schema=DATABASE() AND k.referenced_table_name IN ('user','point_history','user_role') ORDER BY k.table_schema,k.table_name,k.constraint_name,k.ordinal_position");
    }
    void schemaSafety() {
        require(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema=DATABASE()", Long.class) == 0,
                "RF016 triggers not supported");
        require(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.events WHERE event_schema=DATABASE()", Long.class) == 0,
                "RF016 scheduled events not supported");
        for (Map<String, Object> ref : readReferences()) {
            require("ngng_local".equals(ref.get("table_schema")), "RF016 external schema reference refused");
            require(Set.of("RESTRICT", "NO ACTION").contains(ref.get("delete_rule")), "RF016 cascading FK refused");
            if (KEYS.containsKey(ref.get("referenced_table_name")))
                require(KEYS.get(ref.get("referenced_table_name")).equals(ref.get("referenced_column_name")), "RF016 non-PK reference refused");
        }
    }
    User setupUser() {
        require(!readOnly, "RF016 read-only preflight may not insert fixtures");
        writesStarted = true;
        try {
            return setupTx.execute(status -> {
                long id = insert("user", linked("name", marker, "email", marker + "@example.invalid", "role_id", roleId));
                insert("point_history", linked("user_id", id, "before_cost", 0L, "add_cost", 1000L, "cost", 1000L,
                        "type", "fixture", "type_detail", marker));
                return User.builder().userId(id).email(marker + "@example.invalid").name(marker)
                        .role(Role.builder().roleId(roleId).roleType("USER").build()).build();
            });
        } catch (RuntimeException failure) { ledger.uncertain(); throw failure; }
    }
    long insert(String table, LinkedHashMap<String, Object> identity) {
        require(!readOnly, "RF016 read-only preflight may not insert");
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        String columns = String.join(",", identity.keySet().stream().map(Rf016MySqlSafetyGate::quote).toList());
        String placeholders = String.join(",", Collections.nCopies(identity.size(), "?"));
        require(jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("INSERT INTO " + quote(table) + " (" + columns + ") VALUES (" + placeholders + ")", Statement.RETURN_GENERATED_KEYS);
            int index = 0; for (Object value : identity.values()) statement.setObject(++index, value); return statement;
        }, keys) == 1, "RF016 insert row count mismatch");
        Number key = keys.getKey(); require(key != null, "RF016 generated PK unavailable");
        ledger.add(new Owned(table, key.longValue(), identity)); // Before transaction commit.
        return key.longValue();
    }
    PointHistory observe(PointHistory saved, EntityManagerFactory factory) {
        try {
            require(saved.getId() != null && ledger.owns("user", saved.getUser().getUserId()), "RF016 untracked service generated identity");
            ledger.add(new Owned("point_history", saved.getId(), linked("user_id", saved.getUser().getUserId(),
                    "before_cost", saved.getBeforeCost(), "add_cost", saved.getAddCost(), "cost", saved.getCost(),
                    "type", saved.getType(), "type_detail", saved.getTypeDetail())));
            lastSavedPointId = saved.getId();
            var entityManager = Objects.requireNonNull(EntityManagerFactoryUtils.getTransactionalEntityManager(factory));
            entityManager.flush(); // Real INSERT must reach the server before intentional rollback.
            serviceConnectionId = ((Number) entityManager.createNativeQuery("SELECT CONNECTION_ID()").getSingleResult()).longValue();
        } catch (RuntimeException failure) { ledger.uncertain(); throw failure; }
        if (intentionalRollback) throw new DeliberateRollback();
        return saved;
    }
    PointHistory charge(User user, boolean rollback) {
        intentionalRollback = rollback; lastSavedPointId = null;
        require(!TransactionSynchronizationManager.isActualTransactionActive(), "RF016 no enclosing test transaction allowed");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new CustomUserDetails(
                User.builder().email(user.getEmail()).build()), null, List.of()));
        try { return service.updateCost(new CreateAddPointRequestDTO(200L, "충전")); }
        catch (DeliberateRollback expected) {
            require(lastSavedPointId != null, "RF016 rollback PK missing");
            rolledBackPoints.add(lastSavedPointId); throw expected;
        }
        catch (RuntimeException unexpected) { ledger.uncertain(); throw unexpected; }
        finally { intentionalRollback = false; SecurityContextHolder.clearContext(); }
    }
    <T> T independent(Function<JdbcTemplate, T> observation) {
        require(!TransactionSynchronizationManager.isActualTransactionActive(), "RF016 observation must be outside service transaction");
        try (Connection connection = source.getConnection()) {
            require(connection.getAutoCommit(), "RF016 independent observer must use autocommit");
            JdbcTemplate observer = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            long connectionId = observer.queryForObject("SELECT CONNECTION_ID()", Long.class);
            require(connectionId != serviceConnectionId, "RF016 independent physical connection required");
            return observation.apply(observer);
        } catch (SQLException failure) { throw new IllegalStateException("RF016 independent observation connection failed (details withheld)"); }
    }
    List<Map<String, Object>> pointState(JdbcTemplate observer, long userId) {
        return observer.queryForList("SELECT point_id,user_id,before_cost,add_cost,cost,type,type_detail FROM point_history WHERE user_id=? ORDER BY point_id", userId);
    }
    void lockCleanupRanges() {
        for (String table : List.of("user", "point_history")) {
            ledger.referenced(table, jdbc.queryForList("SELECT " + quote(KEYS.get(table))
                    + " FROM " + quote(table) + " FORCE INDEX (PRIMARY) ORDER BY " + quote(KEYS.get(table)) + " FOR UPDATE", Long.class));
        }
    }
    void verifyCleanupLockingRaces(User user) {
        require(!TransactionSynchronizationManager.isActualTransactionActive(), "RF016 race probe must start outside test transaction");
        var before = independent(observer -> pointState(observer, user.getUserId()));
        for (String table : List.of("user", "point_history")) {
            ExecutorService worker = Executors.newSingleThreadExecutor();
            try {
                try {
                    setupTx.executeWithoutResult(status -> {
                        lockCleanupRanges(); // The same helper is used before cleanup's first DELETE.
                        Future<Integer> blocked = worker.submit(() -> {
                            try (Connection connection = source.getConnection()) {
                                connection.setAutoCommit(false);
                                try {
                                    try (Statement session = connection.createStatement()) { session.execute("SET SESSION innodb_lock_wait_timeout=1"); }
                                    LinkedHashMap<String, Object> values = table.equals("user")
                                            ? linked("name", marker + "_race", "email", marker + "_race@example.invalid", "role_id", roleId)
                                            : linked("user_id", user.getUserId(), "before_cost", 1200L, "add_cost", 1L, "cost", 1201L, "type", "fixture", "type_detail", marker + "_race");
                                    String columns = String.join(",", values.keySet().stream().map(Rf016MySqlSafetyGate::quote).toList());
                                    String placeholders = String.join(",", Collections.nCopies(values.size(), "?"));
                                    try (PreparedStatement insert = connection.prepareStatement("INSERT INTO " + quote(table) + " (" + columns + ") VALUES (" + placeholders + ")", Statement.RETURN_GENERATED_KEYS)) {
                                        int index = 0; for (Object value : values.values()) insert.setObject(++index, value);
                                        insert.executeUpdate();
                                        // Unexpected success is still traced before mandatory rollback, never committed.
                                        try (ResultSet key = insert.getGeneratedKeys()) {
                                            require(key.next(), "RF016 unexpected competitor insert lost PK"); long id = key.getLong(1);
                                            ledger.add(new Owned(table, id, values));
                                            (table.equals("user") ? rolledBackUsers : rolledBackPoints).add(id);
                                        }
                                    }
                                    return 0;
                                } catch (SQLException failure) { return failure.getErrorCode(); }
                                finally { try { connection.rollback(); } catch (SQLException failure) { ledger.uncertain(); throw failure; } }
                            }
                        });
                        try { require(blocked.get(30, TimeUnit.SECONDS) == 1205, "RF016 competing INSERT was not blocked by cleanup range locks"); }
                        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); ledger.uncertain(); throw new IllegalStateException("RF016 race interrupted"); }
                        catch (ExecutionException | TimeoutException failure) { ledger.uncertain(); throw new IllegalStateException("RF016 race outcome unknown; cleanup refused"); }
                        throw new ProbeRollback(); // Also prove the guard's own transaction rolls back.
                    });
                } catch (ProbeRollback expected) { /* lock-only probe transaction is intentionally rolled back */ }
            } finally {
                worker.shutdownNow();
                try { if (!worker.awaitTermination(30, TimeUnit.SECONDS)) { ledger.uncertain(); throw new IllegalStateException("RF016 race worker remains active; cleanup refused"); } }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); ledger.uncertain(); throw new IllegalStateException("RF016 race worker stop interrupted"); }
            }
        }
        independent(observer -> {
            require(before.equals(pointState(observer, user.getUserId())) && observer.queryForObject("SELECT COUNT(*) FROM `user`", Long.class) == 1
                    && observer.queryForList("SELECT * FROM user_role ORDER BY role_id").equals(roles), "RF016 lock probe rollback changed fixture/original rows");
            return null;
        });
        cleanupLocksVerified = true;
        System.out.println("RF016 cleanup-lock-races user/point INSERT timeout1205=true guard-rollback-unchanged=true run=" + marker);
    }
    void cleanup() {
        require(!readOnly, "RF016 read-only preflight may not delete fixtures");
        if (!writesStarted) return;
        ledger.assertSafe();
        require(cleanupLocksVerified, "RF016 cleanup lock races not verified in this run; automatic cleanup refused");
        schemaSafety();
        require(readReferences().equals(references), "RF016 FK metadata changed; cleanup refused");
        try {
            setupTx.executeWithoutResult(status -> {
                // RR full PRIMARY scans are current locking reads, including gaps/supremum.
                // Acquire before snapshots/deletes; competing user/point inserts cannot commit
                // between ownership checks and this cleanup transaction's commit.
                lockCleanupRanges();
                require(jdbc.queryForList("SELECT * FROM user_role FORCE INDEX (PRIMARY) ORDER BY role_id FOR SHARE").equals(roles),
                        "RF016 original roles changed; cleanup refused");
                // Inspect all owned identities/references and refuse unknown rows before the first DELETE.
                for (Owned row : ledger.rows()) {
                    List<Map<String, Object>> actual = jdbc.queryForList("SELECT * FROM " + quote(row.table()) + " WHERE " + quote(KEYS.get(row.table())) + "=? FOR UPDATE", row.id());
                    boolean rolledBack = row.table().equals("point_history") ? rolledBackPoints.contains(row.id()) : rolledBackUsers.contains(row.id());
                    require(rolledBack ? actual.isEmpty() : actual.size() == 1, "RF016 owned row presence changed; cleanup refused");
                    if (!actual.isEmpty()) { require(actual.size() == 1, "RF016 PK not unique"); ledger.identity(row, actual.get(0)); }
                    for (Map<String, Object> ref : references) {
                        if (!row.table().equals(ref.get("referenced_table_name"))) continue;
                        String table = ref.get("table_name").toString(), column = ref.get("column_name").toString();
                        String selected = KEYS.getOrDefault(table, column);
                        List<Long> matching = jdbc.queryForList("SELECT " + quote(selected) + " FROM " + quote(table)
                                + " WHERE " + quote(column) + "=? FOR UPDATE", Long.class, row.id());
                        if (matching.isEmpty()) continue;
                        require(KEYS.containsKey(table), "RF016 unknown referring table; cleanup refused");
                        ledger.referenced(table, matching);
                    }
                }
                for (String table : List.of("point_history", "user")) {
                    for (Owned row : ledger.rows()) {
                        if (!row.table().equals(table)) continue;
                        LinkedHashMap<String, Object> predicates = linked(KEYS.get(table), row.id()); predicates.putAll(row.identity());
                        String where = String.join(" AND ", predicates.keySet().stream().map(k -> quote(k) + " <=> ?").toList());
                        long expected = jdbc.queryForObject("SELECT COUNT(*) FROM " + quote(table) + " WHERE " + quote(KEYS.get(table)) + "=?", Long.class, row.id());
                        require(jdbc.update("DELETE FROM " + quote(table) + " WHERE " + where, predicates.values().toArray()) == expected, "RF016 exact cleanup count mismatch");
                    }
                }
                require(count("user") == 0 && count("point_history") == 0 && roleRows().equals(roles), "RF016 cleanup transaction postcondition failed");
            });
            independent(observer -> {
                require(observer.queryForObject("SELECT COUNT(*) FROM `user`", Long.class) == 0
                        && observer.queryForObject("SELECT COUNT(*) FROM point_history", Long.class) == 0
                        && observer.queryForList("SELECT * FROM user_role ORDER BY role_id").equals(roles), "RF016 committed cleanup postcondition failed");
                return null;
            });
            System.out.println("RF016 exact cleanup committed=true remaining-owned=0 original-roles-unchanged=true run=" + marker);
        } catch (RuntimeException failure) { ledger.uncertain(); throw failure; }
    }
    void recoverySummary() {
        System.err.println("RF016 cleanup review required run=" + marker + " tracked=" + ledger.rows().stream().map(r -> r.table() + ":" + r.id()).toList());
    }
    @Override public void close() { if (context != null) context.close(); SecurityContextHolder.clearContext(); }
    static final class DeliberateRollback extends RuntimeException { }
    static final class ProbeRollback extends RuntimeException { }
    static LinkedHashMap<String, Object> linked(Object... pairs) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>(); for (int i = 0; i < pairs.length; i += 2) values.put((String) pairs[i], pairs[i + 1]); return values;
    }

    @Configuration(proxyBeanMethods = false) @EnableTransactionManagement
    static class JpaConfiguration {
        @Bean DataSource dataSource(Rf016MySqlFixture fixture) { return fixture.source; }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
            var factory = new LocalContainerEntityManagerFactoryBean(); factory.setDataSource(source);
            factory.setManagedTypes(PersistenceManagedTypes.of(User.class.getName(), Role.class.getName(), PointHistory.class.getName()));
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            // JPA database.action=none overrides Hibernate validate; leave it unset to retain validation.
            // Only validate is enabled. No Boot SQL initializer, schema generation or create/update fallback.
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate", "jakarta.persistence.schema-generation.scripts.action", "none",
                    "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy", "hibernate.show_sql", "false"));
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) { return new JpaTransactionManager(factory); }
        @Bean JpaRepositoryFactoryBean<UserRepository, User, Long> users(EntityManagerFactory factory) {
            var repository = new JpaRepositoryFactoryBean<UserRepository, User, Long>(UserRepository.class);
            repository.setEntityManager(SharedEntityManagerCreator.createSharedEntityManager(factory)); repository.setTransactionManager("transactionManager"); return repository;
        }
        @Bean JpaRepositoryFactoryBean<PointHistoryRepository, PointHistory, Long> histories(EntityManagerFactory factory) {
            var repository = new JpaRepositoryFactoryBean<PointHistoryRepository, PointHistory, Long>(PointHistoryRepository.class);
            repository.setEntityManager(SharedEntityManagerCreator.createSharedEntityManager(factory)); repository.setTransactionManager("transactionManager"); return repository;
        }
        @Bean AuthService authService(UserRepository users) { return new AuthService(users); }
        @Bean PointHistoryService pointHistoryService(UserRepository users, PointHistoryRepository histories, AuthService auth,
                                                     EntityManagerFactory factory, Rf016MySqlFixture fixture) {
            PointHistoryRepository observed = (PointHistoryRepository) Proxy.newProxyInstance(PointHistoryRepository.class.getClassLoader(), new Class<?>[]{PointHistoryRepository.class},
                    (proxy, method, args) -> {
                        try { Object result = method.invoke(histories, args); return method.getName().equals("save") ? fixture.observe((PointHistory) result, factory) : result; }
                        catch (InvocationTargetException failure) { fixture.ledger.uncertain(); throw failure.getCause(); }
                    });
            // charge() calls updateCost only. Payment-only access must never reach JPA or files.
            return new PointHistoryService(observed, auth, users, unused(ProductRepository.class),
                    unused(TransactionDetailsRepository.class), unused(PaymentVerificationGate.class),
                    unused(PaymentSettlementRepository.class));
        }
    }
}
