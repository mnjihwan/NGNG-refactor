package com.ngng.api.transaction.integration;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** RF-004 only: an allowlisted local target and exact, identity-tracked fixture cleanup. No DDL. */
final class Rf004MySqlFixture {
    static final String OPT_IN = "approved-local-transaction-fixture-only";
    private static final List<String> EMPTY_TABLES = List.of("user", "product", "product_thumbnail",
            "transaction_request", "transaction_details", "product_image", "tag", "point_history");
    private static final List<String> PRESERVED_TABLES = List.of("category", "status", "transaction_status", "user_role");
    private static final Map<String, String> KEYS = Map.of("user", "user_id", "product", "product_id",
            "product_thumbnail", "thumbnail_id", "transaction_request", "transaction_request_id",
            "transaction_details", "transaction_details_id", "category", "category_id",
            "status", "status_id", "transaction_status", "status_id");
    final String marker = "rf004_" + UUID.randomUUID().toString().replace("-", "");
    final JdbcTemplate jdbc;
    final DataSource source;
    private final Map<String, List<Map<String, Object>>> originals = new LinkedHashMap<>();
    private final Map<String, Map<Long, Owned>> owned = new ConcurrentHashMap<>();
    private final List<Map<String, Object>> references;
    private TransactionTemplate tx;
    private int sequence;
    private boolean writesStarted;
    long categoryId;
    long statusId;

    Rf004MySqlFixture() throws Exception {
        require(OPT_IN.equals(System.getenv("RF004_MYSQL_TEST")), "Explicit RF004 opt-in required");
        String datadir = required("RF004_EXPECTED_DATADIR");
        verifyListenerAndProcess(datadir);
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:mysql://127.0.0.1:13306/ngng_local?serverTimezone=Asia/Seoul"
                        + "&characterEncoding=UTF-8&connectTimeout=3000&socketTimeout=15000",
                "ngng", required("RF004_DB_PASSWORD")) {
            @Override public Connection getConnection() throws SQLException {
                Connection connection = super.getConnection();
                try (Statement statement = connection.createStatement()) {
                    statement.execute("SET SESSION innodb_lock_wait_timeout=5");
                    return connection;
                } catch (SQLException failure) { connection.close(); throw failure; }
            }
        };
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        source = dataSource;
        jdbc = new JdbcTemplate(source);
        Map<String, Object> metadata = jdbc.queryForMap("SELECT DATABASE() db,CURRENT_USER() account,"
                + "VERSION() version,@@port port,@@datadir datadir,@@transaction_isolation isolation_level");
        require("ngng_local".equals(metadata.get("db")), "Wrong schema");
        require("ngng@127.0.0.1".equals(metadata.get("account")), "Wrong DB account");
        require(((Number) metadata.get("port")).intValue() == 13306, "Wrong DB port");
        require(normalize(datadir).equals(normalize(metadata.get("datadir").toString())), "Datadir mismatch");
        require(metadata.get("version").toString().startsWith("8.0."), "Expected verified MySQL 8.0");
        require("REPEATABLE-READ".equals(metadata.get("isolation_level")), "Expected RR");
        for (String table : combinedTables()) {
            assertThat(jdbc.queryForList("SELECT engine FROM information_schema.tables WHERE table_schema=DATABASE()"
                    + " AND table_name=?", String.class, table)).containsExactly("InnoDB");
        }
        for (String table : EMPTY_TABLES) assertThat(count(table)).as("empty isolated table %s", table).isZero();
        for (String table : PRESERVED_TABLES) originals.put(table, rows(table));
        // A real unique on product_id is recorded and required, never created by the test.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM (SELECT index_name FROM information_schema.statistics"
                + " WHERE table_schema=DATABASE() AND table_name='transaction_details' AND non_unique=0"
                + " GROUP BY index_name HAVING COUNT(*)=1 AND MAX(column_name)='product_id') indexes", Long.class)).isPositive();
        references = jdbc.queryForList("SELECT k.table_name,k.column_name,k.referenced_table_name,"
                + "k.referenced_column_name,r.delete_rule FROM information_schema.key_column_usage k"
                + " JOIN information_schema.referential_constraints r ON r.constraint_schema=k.constraint_schema"
                + " AND r.constraint_name=k.constraint_name AND r.table_name=k.table_name"
                + " WHERE k.constraint_schema=DATABASE() AND k.referenced_table_name IS NOT NULL");
        for (String relation : List.of("product.user_id:user", "product.category_id:category", "product.status_id:status",
                "product_thumbnail.product_id:product", "transaction_request.product_id:product",
                "transaction_request.buyer_id:user", "transaction_request.seller_id:user",
                "transaction_details.product_id:product", "transaction_details.consumer_id:user",
                "transaction_details.seller_id:user", "transaction_details.status_id:transaction_status")) {
            String[] sides = relation.split(":"), sourceColumn = sides[0].split("\\.");
            require(references.stream().anyMatch(ref -> sourceColumn[0].equals(ref.get("table_name"))
                    && sourceColumn[1].equals(ref.get("column_name")) && sides[1].equals(ref.get("referenced_table_name"))
                    && KEYS.get(sides[1]).equals(ref.get("referenced_column_name"))), "Expected fixture FK missing");
        }
        for (Map<String, Object> ref : references) {
            if (KEYS.containsKey(ref.get("referenced_table_name").toString())) {
                require(Set.of("RESTRICT", "NO ACTION").contains(ref.get("delete_rule").toString()),
                        "Refuse cleanup with cascading foreign key");
            }
        }
        System.out.println("RF004 read-only target verified: loopback-listener/process/datadir-match=true"
                + " db=ngng_local account=ngng@127.0.0.1 port=13306 version=" + metadata.get("version")
                + " isolation=REPEATABLE-READ InnoDB=true empty-fixture-tables=true unique-product=true run=" + marker);
    }

    void start(TransactionTemplate transactionTemplate) {
        tx = transactionTemplate;
        writesStarted = true;
        tx.executeWithoutResult(status -> {
            categoryId = insert("category", linked("category_name", marker + "_category"));
            statusId = insert("status", linked("status_name", marker + "_status"));
            if (jdbc.queryForObject("SELECT COUNT(*) FROM transaction_status WHERE status_id=1", Long.class) == 0) {
                LinkedHashMap<String, Object> values = linked("status_id", 1L, "status", marker + "_transaction_status");
                assertThat(jdbc.update("INSERT INTO transaction_status(status_id,status) VALUES(?,?)",
                        1L, values.get("status"))).isEqualTo(1);
                track("transaction_status", 1L, linked("status", values.get("status")));
            }
        });
    }

    synchronized ProductCase product(boolean thumbnail) { return product(user(), thumbnail); }

    synchronized ProductCase product(long sellerId, boolean thumbnail) {
        return tx.execute(status -> {
            String label = marker + "_p" + (++sequence);
            long product = insert("product", linked("title", label, "content", label, "price", 100L,
                    "for_sale", true, "visible", true, "user_id", sellerId, "category_id", categoryId, "status_id", statusId));
            if (thumbnail) insert("product_thumbnail", linked("product_id", product, "thumbnail_url", label + "_thumbnail"));
            long first = user(), second = user();
            long requestA = insert("transaction_request", linked("product_id", product, "buyer_id", first,
                    "seller_id", sellerId, "price", 80L));
            long requestB = insert("transaction_request", linked("product_id", product, "buyer_id", second,
                    "seller_id", sellerId, "price", 90L));
            return new ProductCase(product, sellerId, first, second, requestA, requestB);
        });
    }

    synchronized long user() {
        return tx.execute(status -> {
            String label = marker + "_u" + (++sequence);
            return insert("user", linked("name", label, "email", label + "@example.invalid", "visible", true));
        });
    }

    private long insert(String table, LinkedHashMap<String, Object> values) {
        String sql = "INSERT INTO " + quote(table) + " (" + String.join(",", values.keySet().stream()
                .map(Rf004MySqlFixture::quote).toList()) + ") VALUES (" + String.join(",", Collections.nCopies(values.size(), "?")) + ")";
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        assertThat(jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            int index = 0;
            for (Object value : values.values()) statement.setObject(++index, value);
            return statement;
        }, key)).isEqualTo(1);
        long id = Objects.requireNonNull(key.getKey()).longValue();
        // Identity recorded before the surrounding transaction attempts commit.
        track(table, id, values);
        return id;
    }

    void trackDetails(long id, long product, long buyer, long seller, long price, long status) {
        require(owned.getOrDefault("product", Map.of()).containsKey(product), "Unowned product from service save");
        require(owned.getOrDefault("user", Map.of()).containsKey(buyer), "Unowned buyer from service save");
        require(owned.getOrDefault("user", Map.of()).containsKey(seller), "Unowned seller from service save");
        track("transaction_details", id, linked("product_id", product, "consumer_id", buyer,
                "seller_id", seller, "price", price, "status_id", status));
    }

    private void track(String table, long id, Map<String, Object> values) {
        LinkedHashMap<String, Object> identity = new LinkedHashMap<>(values);
        // Business state may change only on owned product/request rows. Marker and FK identities remain immutable.
        if (table.equals("product")) identity.remove("for_sale");
        owned.computeIfAbsent(table, ignored -> new ConcurrentHashMap<>()).put(id, new Owned(identity));
    }

    void cleanup() {
        if (!writesStarted) return;
        require(tx != null, "No transaction manager for cleanup");
        Map<String, Integer> deleted = new LinkedHashMap<>();
        tx.executeWithoutResult(status -> {
            // Inspect every identity and inbound reference before deleting any row. Unknown data aborts cleanup.
            for (Map.Entry<String, Map<Long, Owned>> table : owned.entrySet()) {
                for (Map.Entry<Long, Owned> row : table.getValue().entrySet()) {
                    List<Map<String, Object>> actual = jdbc.queryForList("SELECT * FROM " + quote(table.getKey())
                            + " WHERE " + quote(KEYS.get(table.getKey())) + "=? FOR UPDATE", row.getKey());
                    if (actual.isEmpty()) continue; // insert/commit/intentional rollback can leave no owned row.
                    assertThat(actual).hasSize(1);
                    verifyIdentity(actual.get(0), row.getValue().identity());
                    verifyReferences(table.getKey(), row.getKey());
                }
            }
            for (String table : List.of("transaction_details", "transaction_request", "product_thumbnail",
                    "product", "user", "category", "status", "transaction_status")) {
                for (Map.Entry<Long, Owned> row : owned.getOrDefault(table, Map.of()).entrySet()) {
                    LinkedHashMap<String, Object> predicate = linked(KEYS.get(table), row.getKey());
                    predicate.putAll(row.getValue().identity());
                    String where = String.join(" AND ", predicate.keySet().stream().map(name -> quote(name) + " <=> ?").toList());
                    int count = jdbc.update("DELETE FROM " + quote(table) + " WHERE " + where, predicate.values().toArray());
                    assertThat(count).isBetween(0, 1);
                    deleted.merge(table, count, Integer::sum);
                }
            }
            for (String table : EMPTY_TABLES) assertThat(count(table)).as("cleanup remaining %s", table).isZero();
            for (String table : PRESERVED_TABLES) assertThat(rows(table)).as("preserved %s", table).isEqualTo(originals.get(table));
        });
        for (String table : EMPTY_TABLES) assertThat(count(table)).as("committed cleanup remaining %s", table).isZero();
        for (String table : PRESERVED_TABLES) assertThat(rows(table)).as("committed preserved %s", table).isEqualTo(originals.get(table));
        System.out.println("RF004 cleanup committed and verified: exact-owned-row-deletes=" + deleted
                + " empty-fixture-tables=true original-reference-rows-unchanged=true run=" + marker);
    }

    void reportTrackedIdentitiesForManualReadOnlyRecovery() {
        Map<String, List<Long>> trackedIds = new TreeMap<>();
        owned.forEach((table, rows) -> trackedIds.put(table, rows.keySet().stream().sorted().toList()));
        System.err.println("RF004 cleanup stopped; no broader deletion attempted. run=" + marker + " tracked-PKs=" + trackedIds);
    }

    private void verifyReferences(String target, long id) {
        for (Map<String, Object> ref : references) {
            if (!target.equals(ref.get("referenced_table_name"))) continue;
            String table = ref.get("table_name").toString(), column = ref.get("column_name").toString();
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM " + quote(table) + " WHERE " + quote(column) + "=?", Long.class, id);
            if (count == 0) continue;
            require(KEYS.containsKey(table), "Unexpected external fixture reference");
            for (Long referencingId : jdbc.queryForList("SELECT " + quote(KEYS.get(table)) + " FROM " + quote(table)
                    + " WHERE " + quote(column) + "=?", Long.class, id)) {
                require(owned.getOrDefault(table, Map.of()).containsKey(referencingId), "Untracked fixture reference");
            }
        }
    }

    private static void verifyIdentity(Map<String, Object> actual, Map<String, Object> expected) {
        for (Map.Entry<String, Object> field : expected.entrySet()) {
            Object value = actual.get(field.getKey()), required = field.getValue();
            if (value instanceof Number a && required instanceof Number b) require(a.longValue() == b.longValue(), "Owned identity changed");
            else if (value instanceof Boolean a && required instanceof Boolean b) require(a.equals(b), "Owned identity changed");
            else require(Objects.equals(value, required), "Owned marker/identity changed");
        }
    }

    List<Map<String, Object>> state(ProductCase item) {
        return state(jdbc, item);
    }

    List<Map<String, Object>> committedState(ProductCase item) {
        require(owned.getOrDefault("product", Map.of()).containsKey(item.product()), "Unowned committed-state read");
        // Direct source.getConnection deliberately bypasses Spring's caller-bound connection.
        // This connection is read-only and closes before returning; it never writes fixture rows.
        try (Connection connection = source.getConnection()) {
            connection.setReadOnly(true);
            return state(new JdbcTemplate(new SingleConnectionDataSource(connection, true)), item);
        } catch (SQLException failure) { throw new IllegalStateException("Separate read-only state check failed", failure); }
    }

    private List<Map<String, Object>> state(JdbcTemplate reader, ProductCase item) {
        List<Map<String, Object>> result = new ArrayList<>();
        result.addAll(reader.queryForList("SELECT * FROM product WHERE product_id=?", item.product()));
        result.addAll(reader.queryForList("SELECT * FROM transaction_request WHERE product_id=? ORDER BY transaction_request_id", item.product()));
        result.addAll(reader.queryForList("SELECT * FROM transaction_details WHERE product_id=? ORDER BY transaction_details_id", item.product()));
        return result;
    }

    private List<Map<String, Object>> rows(String table) { return jdbc.queryForList("SELECT * FROM " + quote(table) + " ORDER BY 1"); }
    private long count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + quote(table), Long.class); }
    private static List<String> combinedTables() { List<String> result = new ArrayList<>(EMPTY_TABLES); result.addAll(PRESERVED_TABLES); return result; }
    static LinkedHashMap<String, Object> linked(Object... pairs) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put(pairs[i].toString(), pairs[i + 1]);
        return result;
    }
    private static String quote(String identifier) { require(identifier.matches("[a-z_]+"), "Unsafe identifier"); return "`" + identifier + "`"; }
    private static String required(String name) { return Objects.requireNonNull(System.getenv(name), "Missing environment " + name); }
    private static String normalize(String value) { return value.replace('\\', '/').replaceAll("/+$", "").toLowerCase(Locale.ROOT); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }

    private static void verifyListenerAndProcess(String expectedDatadir) throws Exception {
        long pid = Long.parseLong(required("RF004_EXPECTED_PID"));
        ProcessHandle process = ProcessHandle.of(pid).orElseThrow(() -> new IllegalStateException("Verified mysqld missing"));
        String executable = process.info().command().orElseThrow();
        String rawArguments = process.info().commandLine().orElse(null);
        if (rawArguments == null) {
            // Java 17 on Windows may expose command() but not commandLine(). This fixed,
            // read-only OS query is captured in memory; neither paths nor raw output are logged.
            Process inspect = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
                    "[Console]::OutputEncoding=[System.Text.UTF8Encoding]::new($false); "
                            + "$entry = Get-CimInstance Win32_Process -Filter 'ProcessId=" + pid
                            + "' -ErrorAction Stop; if ($null -eq $entry) { exit 2 }; [Console]::Write($entry.CommandLine)")
                    .redirectErrorStream(true).start();
            if (!inspect.waitFor(10, TimeUnit.SECONDS)) { inspect.destroyForcibly(); throw new IllegalStateException("OS process inspection timed out"); }
            require(inspect.exitValue() == 0, "OS process inspection failed");
            rawArguments = new String(inspect.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            require(!rawArguments.isBlank(), "OS process arguments unavailable");
        }
        String arguments = normalize(rawArguments).replace("\"", "");
        require(normalize(executable).equals(normalize(required("RF004_EXPECTED_MYSQLD"))), "mysqld executable mismatch");
        require(executable.toLowerCase(Locale.ROOT).endsWith("mysqld.exe"), "Not mysqld");
        require(arguments.contains("--no-defaults") && arguments.contains("--port=13306")
                && arguments.contains("--bind-address=127.0.0.1")
                && arguments.contains("--datadir=" + normalize(expectedDatadir)), "mysqld options mismatch");
        Process netstat = new ProcessBuilder("netstat", "-ano", "-p", "TCP").redirectErrorStream(true).start();
        String listing = new String(netstat.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        require(netstat.waitFor() == 0, "Listener verification failed");
        int found = 0;
        for (String line : listing.split("\\R")) {
            String[] fields = line.trim().split("\\s+");
            if (fields.length < 5 || !fields[1].endsWith(":13306") || !fields[3].equals("LISTENING")) continue;
            require(fields[1].equals("127.0.0.1:13306") && Long.parseLong(fields[4]) == pid, "Listener target mismatch");
            found++;
        }
        require(found == 1 && process.isAlive(), "Expected one live loopback listener");
    }

    record ProductCase(long product, long seller, long buyerA, long buyerB, long requestA, long requestB) { }
    private record Owned(Map<String, Object> identity) { }
}
