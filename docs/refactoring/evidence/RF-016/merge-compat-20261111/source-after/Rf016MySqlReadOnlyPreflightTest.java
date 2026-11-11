package com.ngng.api.isolation.mysql;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Separate opt-in. Only constructor read gates; server-side READ ONLY on every connection.
 * Never starts JPA, inserts fixtures, runs race probes or invokes cleanup. */
class Rf016MySqlReadOnlyPreflightTest {
    @Test void verifiesLiveIdentityAndMetadataVisibilityWithoutFixtureWrites() throws Exception {
        try (Rf016MySqlFixture fixture = new Rf016MySqlFixture(System.getenv(), true)) {
            assertTrue(fixture.readOnly);
            assertFalse(fixture.writesStarted);
            assertNull(fixture.context);
            assertTrue(fixture.ledger.rows().isEmpty());
            assertTrue(fixture.roles.equals(fixture.roleRows()));
            assertEquals(0L, fixture.count("user"));
            assertEquals(0L, fixture.count("point_history"));
            System.out.println("RF016 readonly-preflight=true identity/metadata/runtime/empty-target/original-roles=true fixture-writes=0");
        }
    }
}
