package com.ngng.api.user.point.payment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentVerificationConfigurationTest {
    private static final String PREFIX = "ngng.payment.verification.";
    private final PaymentVerificationConfiguration configuration = new PaymentVerificationConfiguration();
    @TempDir Path temporary;

    @Test
    void noProductionSettingsMeansDenyWithoutStartingAnyExternalService() {
        deny(configuration.paymentVerificationGate(new MockEnvironment()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"manifest-path", "expected-environment-id", "expected-database-generation",
            "expected-approval-version", "independent-controls-confirmed", "clock-trusted"})
    void everyDeploymentInputIsRequired(String field) throws Exception {
        MockEnvironment environment = configured();
        environment.getPropertySources().remove("mockProperties");
        MockEnvironment complete = configured();
        for (String name : new String[]{"manifest-path", "expected-environment-id", "expected-database-generation",
                "expected-approval-version", "independent-controls-confirmed", "clock-trusted"}) {
            if (!name.equals(field)) {
                environment.setProperty(PREFIX + name, complete.getProperty(PREFIX + name));
            }
        }
        deny(configuration.paymentVerificationGate(environment));
    }

    @ParameterizedTest
    @ValueSource(strings = {"false", "TRUE", "yes", "1", "", " true "})
    void malformedTrustAssertionsNeverBecomeTrue(String value) throws Exception {
        MockEnvironment environment = configured();
        environment.setProperty(PREFIX + "independent-controls-confirmed", value);
        deny(configuration.paymentVerificationGate(environment));
        environment.setProperty(PREFIX + "independent-controls-confirmed", "true");
        environment.setProperty(PREFIX + "clock-trusted", value);
        deny(configuration.paymentVerificationGate(environment));
    }

    @Test
    void explicitSyntheticInputsAllowButLaterPropertyMutationCannotHotReloadTheSnapshot() throws Exception {
        MockEnvironment environment = configured();
        PaymentVerificationGate gate = configuration.paymentVerificationGate(environment);
        assertThatCode(() -> allow(gate)).doesNotThrowAnyException();
        environment.setProperty(PREFIX + "expected-approval-version", "revoked-test");
        assertThatCode(() -> allow(gate)).doesNotThrowAnyException();
        deny(configuration.paymentVerificationGate(environment));
    }

    @Test
    void malformedPathAndUnresolvedConfigurationDenyWithoutExposingInput() throws Exception {
        MockEnvironment environment = configured();
        environment.setProperty(PREFIX + "manifest-path", "\u0000private-path");
        deny(configuration.paymentVerificationGate(environment));
        environment.setProperty(PREFIX + "manifest-path", "${missing-test-input}");
        deny(configuration.paymentVerificationGate(environment));
    }

    private MockEnvironment configured() throws Exception {
        Instant now = Instant.now();
        Path file = temporary.resolve("config-fixture.json");
        Files.writeString(file, """
                {"formatVersion":1,"approvalVersion":"approval-test","environmentId":"test-env",
                "databaseGeneration":"generation-test","verifiedAt":"%s","validFrom":"%s","validUntil":"%s",
                "entries":[{"transactionId":101,"productId":201,"buyerId":301,"sellerId":401,"price":500,
                "verificationId":"fixture","verifiedBy":"fixture","evidenceRef":"fixture"}]}
                """.formatted(now.minusSeconds(1), now.minusSeconds(1), now.plusSeconds(120)));
        return new MockEnvironment().withProperty(PREFIX + "manifest-path", file.toString())
                .withProperty(PREFIX + "expected-environment-id", "test-env")
                .withProperty(PREFIX + "expected-database-generation", "generation-test")
                .withProperty(PREFIX + "expected-approval-version", "approval-test")
                .withProperty(PREFIX + "independent-controls-confirmed", "true")
                .withProperty(PREFIX + "clock-trusted", "true");
    }

    private static void allow(PaymentVerificationGate gate) {
        gate.requireVerified(101L, 201L, 301L, 401L, 500L, 1L);
    }

    private static void deny(PaymentVerificationGate gate) {
        assertThatThrownBy(() -> allow(gate)).isInstanceOfSatisfying(ResponseStatusException.class,
                exception -> org.assertj.core.api.Assertions.assertThat(exception.getReason())
                        .isEqualTo("Payment verification is required"));
    }
}
