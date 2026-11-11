package com.ngng.api.isolation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.web.context.support.StandardServletEnvironment;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Base64;

/** Sanitizes the environment before SpringApplication publishes the environment-prepared event.
 * ConfigDataEnvironmentPostProcessor therefore never sees host configuration/import locations.
 * Deliberately discards host system/environment, profiles and test property sources as a whole.
 * This is configuration isolation, not an OS firewall or protection from arbitrary JVM agents. */
public class Rf016SafeContextLoader extends SpringBootContextLoader {
    static final String SOURCE = "rf016-controlled";
    static final String SECRET = Base64.getUrlEncoder().withoutPadding().encodeToString(
            "rf016-synthetic-key-only-abcdefghijklmnopqrstuvwxyz-0123456789-abcdef".getBytes(StandardCharsets.UTF_8));

    // Supply the environment up front: SpringBootContextLoader then adds merged test properties
    // before run(), instead of installing its later PrepareEnvironmentListener that would undo
    // configureEnvironment sanitization immediately before ConfigData processing.
    @Override protected ConfigurableEnvironment getEnvironment() { return new StandardServletEnvironment(); }

    @Override protected SpringApplication getSpringApplication() {
        SpringApplication application = new SpringApplication() {
            @Override protected void configureEnvironment(ConfigurableEnvironment environment, String[] args) {
                super.configureEnvironment(environment, args);
                isolate(environment);
            }
        };
        application.setAddCommandLineProperties(false);
        return application;
    }

    static void isolate(ConfigurableEnvironment environment) {
        ArrayList<String> names = new ArrayList<>();
        environment.getPropertySources().forEach(source -> names.add(source.getName()));
        names.forEach(environment.getPropertySources()::remove);
        environment.setActiveProfiles();
        environment.setDefaultProfiles("default");
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("spring.config.location", "classpath:/rf016-isolation.properties");
        values.put("spring.config.additional-location", "");
        values.put("spring.config.import", "");
        values.put("spring.profiles.active", "");
        values.put("spring.profiles.include", "");
        values.put("spring.profiles.default", "default");
        values.put("spring.cloud.bootstrap.enabled", "false");
        values.put("spring.main.banner-mode", "off");
        values.put("spring.main.allow-bean-definition-overriding", "false");
        values.put("jwt.secret.key", SECRET);
        values.put("jwt.expiration.access", "3600000");
        values.put("jwt.expiration.refresh", "7200000");
        values.put("spring.mail.username", "rf016-sender@example.invalid");
        environment.getPropertySources().addFirst(new MapPropertySource(SOURCE, values));
    }
}
