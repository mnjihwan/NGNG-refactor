package com.ngng.api.isolation.mysql;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static com.ngng.api.isolation.mysql.Rf016MySqlSafetyGate.*;
import static org.junit.jupiter.api.Assertions.*;

/** All metadata is synthetic; never opens a socket, starts mysqld or reads host credentials. */
class Rf016MySqlSafetyGateTest {
    @Test void missingOptInRefusesBeforeAnyConnectionCanBeRequested() {
        assertThrows(IllegalStateException.class, () -> endpoint(null, URL));
        assertThrows(IllegalStateException.class, () -> new Rf016MySqlFixture(Map.of()));
        assertDoesNotThrow(() -> endpoint(OPT_IN, URL));
    }
    @Test void readOnlyAndWriteModesRequireDifferentOptIns() {
        assertDoesNotThrow(() -> endpoint(READ_ONLY_OPT_IN, URL, true));
        assertThrows(IllegalStateException.class, () -> endpoint(READ_ONLY_OPT_IN, URL, false));
        assertThrows(IllegalStateException.class, () -> endpoint(OPT_IN, URL, true));
    }
    @ParameterizedTest @ValueSource(strings = {"jdbc:mysql://example.invalid:13306/ngng_local", "jdbc:mysql://127.0.0.1:3306/ngng_local", "jdbc:mysql://127.0.0.1:13306/other"})
    void wrongEndpointIsRefusedWithoutResolvingIt(String url) {
        assertThrows(IllegalStateException.class, () -> endpoint(OPT_IN, url));
    }
    List<String> args() { return List.of("--no-defaults", "--port=13306", "--bind-address=127.0.0.1", "--datadir=C:/fixture/data",
            "--persisted-globals-load=OFF", "--skip-slave-start", "--event-scheduler=OFF", "--mysqlx=0"); }
    @Test void hostIdentityRequiresExactPidExecutableDatadirAndOnlyLoopbackListener() {
        assertDoesNotThrow(() -> host(42, "C:/fixture/mysqld.exe", "C:/fixture/data", 42,
                "C:/fixture/mysqld.exe", args(), List.of("127.0.0.1:13306:42")));
        assertThrows(IllegalStateException.class, () -> host(42, "C:/fixture/mysqld.exe", "C:/fixture/data", 43,
                "C:/fixture/mysqld.exe", args(), List.of("127.0.0.1:13306:43")));
        assertThrows(IllegalStateException.class, () -> host(42, "C:/fixture/mysqld.exe", "C:/fixture/data", 42,
                "C:/other/mysqld.exe", args(), List.of("127.0.0.1:13306:42")));
        assertThrows(IllegalStateException.class, () -> host(42, "C:/fixture/mysqld.exe", "C:/fixture/other", 42,
                "C:/fixture/mysqld.exe", args(), List.of("127.0.0.1:13306:42")));
        assertThrows(IllegalStateException.class, () -> host(42, "C:/fixture/mysqld.exe", "C:/fixture/data", 42,
                "C:/fixture/mysqld.exe", args(), List.of("0.0.0.0:13306:42")));
    }
    Map<String, Object> metadata() { return new HashMap<>(Map.of("db", "ngng_local", "account", "ngng@127.0.0.1", "port", 13306,
            "datadir", "C:/fixture/data/", "version", "8.0.35", "isolation_level", "REPEATABLE-READ")); }
    @ParameterizedTest @ValueSource(strings = {"db", "account", "port", "datadir", "version", "isolation_level"})
    void wrongDatabaseIdentityIsRefused(String key) {
        var value = metadata(); value.put(key, key.equals("port") ? 3306 : "wrong");
        assertThrows(IllegalStateException.class, () -> database(value, "C:/fixture/data"));
    }
    @Test void unknownReferenceOrModifiedIdentityOrUncertainCommitNeverAllowsCleanup() {
        Ledger ledger = new Ledger();
        Owned row = new Owned("user", 12, Map.of("name", "rf016_fake", "email", "rf016_fake@example.invalid", "role_id", 1L)); ledger.add(row);
        assertDoesNotThrow(() -> ledger.referenced("user", List.of(12L)));
        assertThrows(IllegalStateException.class, () -> ledger.referenced("point_history", List.of(999L)));
        assertThrows(IllegalStateException.class, () -> ledger.identity(row, Map.of("email", "other@example.invalid")));
        ledger.uncertain();
        assertThrows(IllegalStateException.class, ledger::assertSafe);
        assertThrows(IllegalStateException.class, () -> ledger.referenced("user", List.of(12L)));
    }
    @Test void generatedPkLossAndUnsafeIdentifiersAreRefused() {
        assertThrows(IllegalStateException.class, () -> new Owned("user", 0, Map.of()));
        assertThrows(IllegalStateException.class, () -> quote("user;delete"));
        assertThrows(IllegalStateException.class, () -> new Owned("user_role", 1, Map.of()));
    }
    @Test void journalFailurePreservesKnownPkButForbidsAnyCleanup() {
        Ledger ledger = new Ledger(row -> { throw new IllegalStateException("synthetic journal failure"); });
        assertThrows(IllegalStateException.class, () -> ledger.add(new Owned("user", 12,
                Map.of("name", "rf016_fake", "email", "rf016_fake@example.invalid", "role_id", 1L))));
        assertTrue(ledger.owns("user", 12));
        assertThrows(IllegalStateException.class, ledger::assertSafe);
    }
    @Test void schemaOnlyOrRoleBasedGrantsDoNotEstablishMetadataVisibility() {
        assertThrows(IllegalStateException.class, () -> metadataVisibility(List.of("GRANT ALL PRIVILEGES ON `ngng_local`.* TO 'fixture'@'127.0.0.1'")));
        assertThrows(IllegalStateException.class, () -> metadataVisibility(List.of("GRANT `reader_role` TO 'fixture'@'127.0.0.1'")));
        assertThrows(IllegalStateException.class, () -> metadataVisibility(List.of("GRANT SELECT, EVENT ON *.* TO 'fixture'@'127.0.0.1'")));
        assertDoesNotThrow(() -> metadataVisibility(List.of("GRANT SELECT, TRIGGER, EVENT, REPLICATION CLIENT ON *.* TO 'fixture'@'127.0.0.1'")));
    }
    @Test void existingServerWithoutSafetyFlagsOrWithRuntimeReplicationIsRefused() {
        var oldArgs = new ArrayList<>(args()); oldArgs.remove("--persisted-globals-load=OFF");
        assertThrows(IllegalStateException.class, () -> host(42, "C:/fixture/mysqld.exe", "C:/fixture/data", 42,
                "C:/fixture/mysqld.exe", oldArgs, List.of("127.0.0.1:13306:42")));
        var safe = Map.of("event_scheduler", "OFF", "persisted_globals_load", "OFF");
        assertDoesNotThrow(() -> serverRuntime(safe, 0, 0, 0));
        assertThrows(IllegalStateException.class, () -> serverRuntime(Map.of("event_scheduler", "ON", "persisted_globals_load", "OFF"), 0, 0, 0));
        assertThrows(IllegalStateException.class, () -> serverRuntime(safe, 1, 0, 0));
        assertThrows(IllegalStateException.class, () -> serverRuntime(safe, 0, 1, 0));
        assertThrows(IllegalStateException.class, () -> serverRuntime(safe, 0, 0, 1));
    }
    @Test void partialRevokesAreRefusedEvenWhenGlobalPrivilegesAppearPresent() {
        assertThrows(IllegalStateException.class, () -> metadataVisibility(List.of(
                "GRANT ALL PRIVILEGES ON *.* TO 'fixture'@'127.0.0.1'",
                "REVOKE TRIGGER, EVENT ON `ngng_local`.* FROM 'fixture'@'127.0.0.1'")));
    }
}
