package com.ngng.api.user.point.payment;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.nio.file.Path;

/**
 * No approving defaults or bundled manifest. Properties must be supplied by trusted deployment
 * controls independently of the manifest/database. A property assertion alone does not prove
 * that independence, clock synchronization, writer exclusion or reconciliation truth.
 * All settings and the manifest are captured once; revocation requires ingress stop/drain/redeploy.
 */
@Configuration(proxyBeanMethods = false)
public class PaymentVerificationConfiguration {
    private static final String PREFIX = "ngng.payment.verification.";

    @Bean
    public PaymentVerificationGate paymentVerificationGate(Environment environment) {
        try {
            String path = environment.getProperty(PREFIX + "manifest-path");
            if (path == null || path.isBlank()) {
                return PaymentVerificationGate.denyAll();
            }
            var expected = new PaymentVerificationGate.Expectations(
                    environment.getProperty(PREFIX + "expected-environment-id"),
                    environment.getProperty(PREFIX + "expected-database-generation"),
                    environment.getProperty(PREFIX + "expected-approval-version"),
                    "true".equals(environment.getProperty(PREFIX + "independent-controls-confirmed")),
                    "true".equals(environment.getProperty(PREFIX + "clock-trusted")));
            return PaymentVerificationGate.fromFile(Path.of(path), expected);
        } catch (RuntimeException failure) {
            return PaymentVerificationGate.denyAll();
        }
    }
}
