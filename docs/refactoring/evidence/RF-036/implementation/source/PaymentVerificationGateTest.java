package com.ngng.api.user.point.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentVerificationGateTest {
    private static final Instant NOW = Instant.parse("2026-11-11T00:00:00Z");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final PaymentVerificationGate.Expectations EXPECTED =
            new PaymentVerificationGate.Expectations("test-env", "generation-test", "approval-test", true, true);
    @TempDir Path temporary;
    private final MutableClock clock = new MutableClock(NOW);
    private final AtomicLong nanos = new AtomicLong(100);

    @Test
    void exactLockedTupleIsAllowedWithoutSynthesizingAReceiptOrConsumingApproval() throws Exception {
        PaymentVerificationGate gate = load(valid());
        assertThatCode(() -> allowed(gate)).doesNotThrowAnyException();
        assertThatCode(() -> allowed(gate)).doesNotThrowAnyException();
        denied(gate, 101L, 201L, 301L, 401L, 500L, 2L);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("malformed")
    void entireMalformedManifestDeniesEvenAnOtherwiseMatchingEntry(String name, String input) throws Exception {
        denied(load(input));
    }

    static Stream<Arguments> malformed() throws Exception {
        List<Arguments> cases = new ArrayList<>();
        cases.add(Arguments.of("empty", ""));
        cases.add(Arguments.of("syntax", "{"));
        cases.add(Arguments.of("array", "[]"));
        cases.add(Arguments.of("null", "null"));
        cases.add(Arguments.of("trailing object", valid() + "{}"));
        cases.add(Arguments.of("trailing scalar", valid() + " 1"));
        cases.add(Arguments.of("comment", "/*comment*/" + valid()));
        cases.add(Arguments.of("duplicate root key", valid().replace("\"formatVersion\":1", "\"formatVersion\":1,\"formatVersion\":1")));
        cases.add(Arguments.of("duplicate entry key", valid().replace("\"price\":500", "\"price\":500,\"price\":500")));
        for (String field : List.of("formatVersion", "approvalVersion", "environmentId", "databaseGeneration",
                "verifiedAt", "validFrom", "validUntil", "entries")) {
            mutation(cases, "missing root " + field, root -> root.remove(field));
            mutation(cases, "null root " + field, root -> root.putNull(field));
        }
        mutation(cases, "unknown root", root -> root.put("verified", true));
        mutation(cases, "unsupported format", root -> root.put("formatVersion", 2));
        mutation(cases, "fractional format", root -> root.put("formatVersion", 1.0));
        mutation(cases, "string format", root -> root.put("formatVersion", "1"));
        mutation(cases, "format overflow", root -> root.put("formatVersion", Long.MAX_VALUE));
        for (String field : List.of("environmentId", "databaseGeneration", "approvalVersion")) {
            mutation(cases, "wrong " + field, root -> root.put(field, "different"));
            mutation(cases, "blank " + field, root -> root.put(field, "  "));
            mutation(cases, "type " + field, root -> root.put(field, 1));
            mutation(cases, "oversized " + field, root -> root.put(field, "x".repeat(129)));
        }
        for (String field : List.of("transactionId", "productId", "buyerId", "sellerId", "price")) {
            mutation(cases, "missing entry " + field, root -> entry(root).remove(field));
            mutation(cases, "null entry " + field, root -> entry(root).putNull(field));
            mutation(cases, "zero " + field, root -> entry(root).put(field, 0));
            mutation(cases, "negative " + field, root -> entry(root).put(field, -1));
            mutation(cases, "fractional " + field, root -> entry(root).put(field, 1.5));
            mutation(cases, "string " + field, root -> entry(root).put(field, "1"));
            mutation(cases, "overflow " + field, root -> entry(root).put(field, new java.math.BigInteger("9223372036854775808")));
            mutation(cases, "underflow " + field, root -> entry(root).put(field, new java.math.BigInteger("-9223372036854775809")));
        }
        for (String field : List.of("verificationId", "verifiedBy", "evidenceRef")) {
            mutation(cases, "missing " + field, root -> entry(root).remove(field));
            mutation(cases, "null " + field, root -> entry(root).putNull(field));
            mutation(cases, "empty " + field, root -> entry(root).put(field, ""));
            mutation(cases, "blank " + field, root -> entry(root).put(field, "\t \n"));
            mutation(cases, "numeric " + field, root -> entry(root).put(field, 1));
        }
        mutation(cases, "long verificationId", root -> entry(root).put("verificationId", "x".repeat(129)));
        mutation(cases, "long verifiedBy", root -> entry(root).put("verifiedBy", "x".repeat(513)));
        mutation(cases, "long evidenceRef", root -> entry(root).put("evidenceRef", "x".repeat(2049)));
        mutation(cases, "unknown entry", root -> entry(root).put("paymentProof", true));
        mutation(cases, "entries object", root -> root.set("entries", JSON.createObjectNode()));
        mutation(cases, "null array member", root -> ((ArrayNode) root.get("entries")).addNull());
        mutation(cases, "duplicate transaction", root -> {
            ObjectNode second = entry(root).deepCopy();
            second.put("verificationId", "other-verification");
            ((ArrayNode) root.get("entries")).add(second);
        });
        mutation(cases, "duplicate verificationId", root -> {
            ObjectNode second = entry(root).deepCopy();
            second.put("transactionId", 102);
            ((ArrayNode) root.get("entries")).add(second);
        });
        for (String time : List.of("2026-11-11T00:00:00+00:00", "2026-11-11T09:00:00+09:00",
                "2026-11-11T00:00:00", "2026-02-30T00:00:00Z", "2026-11-11T24:00:00Z",
                "2026-11-11T00:00:60Z", "2026-11-11 00:00:00Z")) {
            mutation(cases, "noncanonical UTC " + time, root -> root.put("validFrom", time));
        }
        mutation(cases, "verified after start", root -> root.put("verifiedAt", "2026-11-11T00:00:01Z"));
        mutation(cases, "equal validity endpoints", root -> root.put("validUntil", "2026-11-11T00:00:00Z"));
        mutation(cases, "reversed validity", root -> root.put("validUntil", "2026-11-10T23:59:59Z"));
        mutation(cases, "excess count", root -> {
            ArrayNode entries = (ArrayNode) root.get("entries");
            for (int i = 1; i <= PaymentVerificationGate.MAX_ENTRIES; i++) {
                ObjectNode next = entry(root).deepCopy();
                next.put("transactionId", 101 + i).put("verificationId", "v-" + i);
                entries.add(next);
            }
        });
        cases.add(Arguments.of("deep nesting", valid().replace("\"evidence-fixture\"", "[".repeat(20) + "0" + "]".repeat(20))));
        return cases.stream();
    }

    @ParameterizedTest
    @MethodSource("mismatchedTuples")
    void everyTupleFieldAndStateIsRequired(Long transaction, Long product, Long buyer, Long seller,
                                           Long price, Long status) throws Exception {
        denied(load(valid()), transaction, product, buyer, seller, price, status);
    }

    static Stream<Arguments> mismatchedTuples() {
        List<Arguments> cases = new ArrayList<>();
        Long[] valid = {101L, 201L, 301L, 401L, 500L, 1L};
        for (int field = 0; field < valid.length; field++) {
            for (Long value : new Long[]{null, 0L, -1L, 999L}) {
                Long[] altered = valid.clone();
                altered[field] = value;
                cases.add(Arguments.of((Object[]) altered));
            }
        }
        for (long status = 2; status <= 7; status++) {
            cases.add(Arguments.of(101L, 201L, 301L, 401L, 500L, status));
        }
        return cases.stream();
    }

    @Test
    void allPositiveLongMaximumsAndFractionalUtcInstantsAreSupported() throws Exception {
        ObjectNode root = root();
        for (String field : List.of("transactionId", "productId", "buyerId", "sellerId", "price")) {
            entry(root).put(field, Long.MAX_VALUE);
        }
        root.put("verifiedAt", "2026-11-10T23:59:59.999999999Z");
        root.put("validUntil", "2026-11-11T00:10:00.000000001Z");
        PaymentVerificationGate gate = load(root.toString());
        assertThatCode(() -> gate.requireVerified(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE,
                Long.MAX_VALUE, Long.MAX_VALUE, 1L)).doesNotThrowAnyException();
    }

    @Test
    void emptyListAndUnlistedNewIdsDenyWithoutAnyAgeOrHighWaterException() throws Exception {
        ObjectNode root = root();
        root.putArray("entries");
        denied(load(root.toString()));
        denied(load(valid()), Long.MAX_VALUE, Long.MAX_VALUE, 301L, 401L, 500L, 1L);
    }

    @Test
    void maximumCountIsBoundedButUsable() throws Exception {
        ObjectNode root = root();
        ArrayNode entries = (ArrayNode) root.get("entries");
        for (int i = 1; i < PaymentVerificationGate.MAX_ENTRIES; i++) {
            ObjectNode next = entry(root).deepCopy();
            next.put("transactionId", 101 + i).put("verificationId", "verification-" + i);
            entries.add(next);
        }
        assertThatCode(() -> allowed(load(root.toString()))).doesNotThrowAnyException();
    }

    @Test
    void absentUnreadableShapeOversizedAndInvalidUtf8InputsDeny() throws Exception {
        denied(PaymentVerificationGate.fromFile(null, EXPECTED));
        denied(PaymentVerificationGate.fromFile(temporary.resolve("absent.json"), EXPECTED));
        denied(PaymentVerificationGate.fromFile(temporary, EXPECTED));
        Path input = temporary.resolve("bounded.json");
        Files.write(input, new byte[PaymentVerificationGate.MAX_FILE_BYTES + 1]);
        denied(PaymentVerificationGate.fromFile(input, EXPECTED));
        Files.write(input, new byte[]{(byte) 0xC3, 0x28});
        denied(PaymentVerificationGate.fromFile(input, EXPECTED));
        Files.writeString(input, valid() + " ".repeat(PaymentVerificationGate.MAX_FILE_BYTES));
        denied(PaymentVerificationGate.fromFile(input, EXPECTED));
    }

    @Test
    void missingOrUnconfirmedIndependentExpectationsCannotApproveTheFile() throws Exception {
        Path file = write(valid());
        denied(PaymentVerificationGate.fromFile(file, null));
        for (String missing : new String[]{null, "", "  ", "x".repeat(129)}) {
            denied(PaymentVerificationGate.fromFile(file, new PaymentVerificationGate.Expectations(missing,
                    "generation-test", "approval-test", true, true)));
            denied(PaymentVerificationGate.fromFile(file, new PaymentVerificationGate.Expectations("test-env",
                    missing, "approval-test", true, true)));
            denied(PaymentVerificationGate.fromFile(file, new PaymentVerificationGate.Expectations("test-env",
                    "generation-test", missing, true, true)));
        }
        denied(PaymentVerificationGate.fromFile(file, new PaymentVerificationGate.Expectations("test-env",
                "generation-test", "approval-test", false, true)));
        denied(PaymentVerificationGate.fromFile(file, new PaymentVerificationGate.Expectations("test-env",
                "generation-test", "approval-test", true, false)));
    }

    @Test
    void snapshotNeverReloadsOrConsumesMutableFiles() throws Exception {
        Path file = write(valid());
        PaymentVerificationGate gate = PaymentVerificationGate.fromFile(file, EXPECTED, clock, nanos::get);
        ObjectNode different = root();
        entry(different).put("price", 999);
        Files.writeString(file, different.toString());
        assertThatCode(() -> allowed(gate)).doesNotThrowAnyException();
        denied(gate, 101L, 201L, 301L, 401L, 999L, 1L);
        Files.writeString(file, "invalid");
        assertThatCode(() -> allowed(gate)).doesNotThrowAnyException();
        Files.delete(file);
        assertThatCode(() -> allowed(gate)).doesNotThrowAnyException();
    }

    @Test
    void validityStartsInclusivelyAndEndsExclusivelyAtTheActualFinalCheck() throws Exception {
        clock.now = NOW.minusNanos(1);
        PaymentVerificationGate gate = load(valid());
        denied(gate);
        advance(Duration.ofNanos(1));
        assertThatCode(() -> allowed(gate)).doesNotThrowAnyException();
        advance(Duration.ofMinutes(10).minusNanos(1));
        assertThatCode(() -> allowed(gate)).doesNotThrowAnyException();
        advance(Duration.ofNanos(1));
        denied(gate);
    }

    @Test
    void lockWaitOrWorkAfterAnEarlierApprovalCannotReuseExpiredPermission() throws Exception {
        PaymentVerificationGate gate = load(valid());
        allowed(gate);
        advance(Duration.ofMinutes(11));
        denied(gate);
    }

    @Test
    void alreadyExpiredInputDenies() throws Exception {
        clock.now = NOW.plusSeconds(600);
        denied(load(valid()));
    }

    @Test
    void backwardsWallClockLatchesDenyEvenAfterTheClockRecovers() throws Exception {
        PaymentVerificationGate gate = load(valid());
        allowed(gate);
        clock.now = NOW.minusNanos(1);
        denied(gate);
        clock.now = NOW;
        denied(gate);
    }

    @Test
    void elapsedClockDriftIncludesAccumulatedSmallJumpsAndCannotRecover() throws Exception {
        PaymentVerificationGate gate = load(valid());
        clock.now = NOW.plusSeconds(3);
        allowed(gate);
        clock.now = NOW.plusSeconds(6);
        denied(gate);
        nanos.set(Duration.ofSeconds(6).toNanos() + 100);
        denied(gate);
    }

    @Test
    void monotonicAdvanceWithoutWallAdvanceIsAlsoAnUntrustedClock() throws Exception {
        PaymentVerificationGate gate = load(valid());
        nanos.addAndGet(Duration.ofSeconds(6).toNanos());
        denied(gate);
    }

    @Test
    void backwardsMonotonicAndOverflowDeny() throws Exception {
        PaymentVerificationGate backward = load(valid());
        nanos.set(99);
        denied(backward);
        nanos.set(Long.MIN_VALUE);
        PaymentVerificationGate overflow = load(valid());
        nanos.set(Long.MAX_VALUE);
        denied(overflow);
    }

    @Test
    void failedNullAndThrowingClocksDeny() throws Exception {
        Path path = write(valid());
        denied(PaymentVerificationGate.fromFile(path, EXPECTED, null, nanos::get));
        denied(PaymentVerificationGate.fromFile(path, EXPECTED, clock, null));
        clock.now = null;
        denied(PaymentVerificationGate.fromFile(path, EXPECTED, clock, nanos::get));
        clock.now = NOW;
        clock.fail = true;
        denied(PaymentVerificationGate.fromFile(path, EXPECTED, clock, nanos::get));
        clock.fail = false;
        PaymentVerificationGate gate = load(valid());
        clock.fail = true;
        denied(gate);
        clock.fail = false;
        denied(gate);
        denied(PaymentVerificationGate.fromFile(path, EXPECTED, clock, () -> { throw new IllegalStateException(); }));
    }

    @Test
    void presentTupleAloneCannotDetectAbaIdReuseOrRestoredStateAndIsNotPaymentProof() throws Exception {
        PaymentVerificationGate gate = load(valid());
        allowed(gate);
        denied(gate, 101L, 201L, 301L, 401L, 501L, 1L);
        // Restoring price or status to the approved tuple is undetectable without the required
        // independent writer/restore controls; these calls explicitly document the limitation.
        allowed(gate);
        denied(gate, 101L, 201L, 301L, 401L, 500L, 2L);
        allowed(gate);
    }

    private void advance(Duration duration) {
        clock.now = clock.now.plus(duration);
        nanos.addAndGet(duration.toNanos());
    }

    private PaymentVerificationGate load(String json) throws IOException {
        return PaymentVerificationGate.fromFile(write(json), EXPECTED, clock, nanos::get);
    }

    private Path write(String json) throws IOException {
        Path path = temporary.resolve("approval-fixture.json");
        Files.writeString(path, json);
        return path;
    }

    private static void allowed(PaymentVerificationGate gate) {
        gate.requireVerified(101L, 201L, 301L, 401L, 500L, 1L);
    }

    private static void denied(PaymentVerificationGate gate) {
        denied(gate, 101L, 201L, 301L, 401L, 500L, 1L);
    }

    private static void denied(PaymentVerificationGate gate, Long transaction, Long product, Long buyer,
                               Long seller, Long price, Long status) {
        assertThatThrownBy(() -> gate.requireVerified(transaction, product, buyer, seller, price, status))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getReason()).isEqualTo("Payment verification is required");
                    assertThat(exception.getMessage()).doesNotContain("approval-fixture", "evidence-fixture", "operator-fixture");
                });
    }

    private static void mutation(List<Arguments> cases, String name, Consumer<ObjectNode> mutation) throws Exception {
        ObjectNode root = root();
        mutation.accept(root);
        cases.add(Arguments.of(name, root.toString()));
    }

    private static ObjectNode root() throws Exception {
        return (ObjectNode) JSON.readTree(valid());
    }

    private static ObjectNode entry(ObjectNode root) {
        return (ObjectNode) root.get("entries").get(0);
    }

    private static String valid() {
        return """
                {"formatVersion":1,"approvalVersion":"approval-test","environmentId":"test-env",
                "databaseGeneration":"generation-test","verifiedAt":"2026-11-11T00:00:00Z",
                "validFrom":"2026-11-11T00:00:00Z","validUntil":"2026-11-11T00:10:00Z",
                "entries":[{"transactionId":101,"productId":201,"buyerId":301,"sellerId":401,"price":500,
                "verificationId":"verification-fixture","verifiedBy":"operator-fixture","evidenceRef":"evidence-fixture"}]}
                """;
    }

    private static final class MutableClock extends Clock {
        private Instant now;
        private boolean fail;
        MutableClock(Instant now) { this.now = now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() {
            if (fail) { throw new IllegalStateException("Synthetic clock failure"); }
            return now;
        }
    }
}
