package com.ngng.api.isolation;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class Rf016EnvironmentIsolationTest {
    @Test void discardsAllIncomingConfigImportProfileAndHostPropertiesWithoutResolvingThem() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("fake-host", Map.of(
                "spring.config.location", "file:must-never-resolve.properties",
                "spring.config.additional-location", "file:additional-must-never-resolve.properties",
                "spring.config.import", "file:import-must-never-resolve.properties",
                "spring.profiles.active", "poison", "spring.profiles.include", "poison-include",
                "jwt.secret.key", "poison-key", "host-only-sentinel", "must-be-removed")));
        environment.setActiveProfiles("already-active");
        Rf016SafeContextLoader.isolate(environment);
        assertEquals(1, environment.getPropertySources().size());
        assertEquals("classpath:/rf016-isolation.properties", environment.getProperty("spring.config.location"));
        assertEquals("", environment.getProperty("spring.config.import"));
        assertEquals("", environment.getProperty("spring.config.additional-location"));
        assertEquals("", environment.getProperty("spring.profiles.include"));
        assertTrue(Rf016SafeContextLoader.SECRET.equals(environment.getProperty("jwt.secret.key")));
        assertNull(environment.getProperty("host-only-sentinel"));
        assertEquals(0, environment.getActiveProfiles().length);
    }
}
