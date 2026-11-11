package com.ngng.api.user.point.payment;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * Immutable operational approval input, not payment proof or a consumed receipt.
 * Call after current row locks and again immediately before monetary/state writes.
 * This gate cannot detect restored databases, ABA changes, old writers or untrue approvals;
 * independent deployment controls must exclude them for the whole approval lifetime.
 */
public final class PaymentVerificationGate {
    static final int MAX_FILE_BYTES = 1_048_576;
    static final int MAX_ENTRIES = 1_000;
    static final Duration MAX_CLOCK_DRIFT = Duration.ofSeconds(5);
    private static final Set<String> ROOT_FIELDS = Set.of("formatVersion", "approvalVersion",
            "environmentId", "databaseGeneration", "verifiedAt", "validFrom", "validUntil", "entries");
    private static final Set<String> ENTRY_FIELDS = Set.of("transactionId", "productId", "buyerId",
            "sellerId", "price", "verificationId", "verifiedBy", "evidenceRef");

    /** Values supplied separately from the manifest and database by the deployment operator. */
    public record Expectations(String environmentId, String databaseGeneration, String approvalVersion,
                               boolean independentControlsConfirmed, boolean clockTrusted) {
        boolean complete() {
            return nonBlank(environmentId, 128) && nonBlank(databaseGeneration, 128)
                    && nonBlank(approvalVersion, 128) && independentControlsConfirmed && clockTrusted;
        }
    }

    private record Binding(long transactionId, long productId, long buyerId, long sellerId, long price) { }
    private record Snapshot(Instant validFrom, Instant validUntil, Map<Long, Binding> entries) { }

    private final Snapshot snapshot;
    private final Clock clock;
    private final LongSupplier monotonicTime;
    private Instant initialWallTime;
    private long initialMonotonicTime;
    private Instant lastWallTime;
    private long lastMonotonicTime;
    private boolean clockFailed;

    private PaymentVerificationGate(Snapshot snapshot, Clock clock, LongSupplier monotonicTime) {
        this.snapshot = snapshot;
        this.clock = clock;
        this.monotonicTime = monotonicTime;
        if (snapshot != null) {
            try {
                lastWallTime = clock.instant();
                lastMonotonicTime = monotonicTime.getAsLong();
                initialWallTime = lastWallTime;
                initialMonotonicTime = lastMonotonicTime;
                clockFailed = lastWallTime == null;
            } catch (RuntimeException failure) {
                clockFailed = true;
            }
        }
    }

    public static PaymentVerificationGate denyAll() {
        return new PaymentVerificationGate(null, Clock.systemUTC(), System::nanoTime);
    }

    /**
     * Reads once and rejects a symbolic link at the final file component. Parent directory
     * symlinks/junctions, path replacement and permissions require trusted deployment controls.
     * Failures expose neither paths nor approval evidence.
     */
    public static PaymentVerificationGate fromFile(Path path, Expectations expected) {
        return fromFile(path, expected, Clock.systemUTC(), System::nanoTime);
    }

    static PaymentVerificationGate fromFile(Path path, Expectations expected, Clock clock,
                                            LongSupplier monotonicTime) {
        try {
            if (expected == null || !expected.complete() || path == null || clock == null
                    || monotonicTime == null || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                    || Files.size(path) > MAX_FILE_BYTES) {
                return denyAll();
            }
            byte[] bytes;
            try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
                bytes = input.readNBytes(MAX_FILE_BYTES + 1);
            }
            if (bytes.length > MAX_FILE_BYTES) {
                return denyAll();
            }
            return new PaymentVerificationGate(parse(bytes, expected), clock, monotonicTime);
        } catch (IOException | RuntimeException failure) {
            return denyAll();
        }
    }

    /** Caller must pass the locked recorded seller, never the current product owner. */
    public synchronized void requireVerified(Long transactionId, Long productId, Long buyerId,
                                              Long sellerId, Long price, Long statusId) {
        if (snapshot == null || !validClockAndWindow() || !Long.valueOf(1).equals(statusId)
                || !positive(transactionId) || !positive(productId) || !positive(buyerId)
                || !positive(sellerId) || !positive(price)
                || !new Binding(transactionId, productId, buyerId, sellerId, price)
                        .equals(snapshot.entries().get(transactionId))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment verification is required");
        }
    }

    private boolean validClockAndWindow() {
        if (clockFailed) {
            return false;
        }
        try {
            Instant now = clock.instant();
            long monotonicNow = monotonicTime.getAsLong();
            long elapsedNanos = Math.subtractExact(monotonicNow, lastMonotonicTime);
            Duration wallElapsed = Duration.between(lastWallTime, now);
            long totalNanos = Math.subtractExact(monotonicNow, initialMonotonicTime);
            Duration totalWallElapsed = Duration.between(initialWallTime, now);
            if (elapsedNanos < 0 || wallElapsed.isNegative()
                    || totalNanos < 0
                    || totalWallElapsed.minus(Duration.ofNanos(totalNanos)).abs()
                            .compareTo(MAX_CLOCK_DRIFT) > 0) {
                clockFailed = true;
                return false;
            }
            lastWallTime = now;
            lastMonotonicTime = monotonicNow;
            return !now.isBefore(snapshot.validFrom()) && now.isBefore(snapshot.validUntil());
        } catch (RuntimeException failure) {
            clockFailed = true;
            return false;
        }
    }

    private static Snapshot parse(byte[] bytes, Expectations expected) throws IOException {
        String json = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        JsonFactory factory = JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(8)
                        .maxStringLength(2_048).maxNumberLength(20).build()).build();
        ObjectMapper mapper = new ObjectMapper(factory);
        JsonNode root;
        try (var parser = factory.createParser(json)) {
            root = mapper.readTree(parser);
            require(parser.nextToken() == null);
        }
        fields(root, ROOT_FIELDS);
        require(root.get("formatVersion").isIntegralNumber() && root.get("formatVersion").canConvertToInt()
                && root.get("formatVersion").intValue() == 1);
        require(text(root, "environmentId", 128).equals(expected.environmentId()));
        require(text(root, "databaseGeneration", 128).equals(expected.databaseGeneration()));
        require(text(root, "approvalVersion", 128).equals(expected.approvalVersion()));
        Instant verifiedAt = utc(root, "verifiedAt");
        Instant from = utc(root, "validFrom");
        Instant until = utc(root, "validUntil");
        require(!verifiedAt.isAfter(from) && from.isBefore(until));
        JsonNode entries = root.get("entries");
        require(entries.isArray() && entries.size() <= MAX_ENTRIES);
        Map<Long, Binding> bindings = new HashMap<>();
        Set<String> verificationIds = new HashSet<>();
        for (JsonNode entry : entries) {
            fields(entry, ENTRY_FIELDS);
            Binding binding = new Binding(number(entry, "transactionId"), number(entry, "productId"),
                    number(entry, "buyerId"), number(entry, "sellerId"), number(entry, "price"));
            require(verificationIds.add(text(entry, "verificationId", 128)));
            text(entry, "verifiedBy", 512);
            text(entry, "evidenceRef", 2_048);
            require(bindings.putIfAbsent(binding.transactionId(), binding) == null);
        }
        return new Snapshot(from, until, Map.copyOf(bindings));
    }

    private static void fields(JsonNode object, Set<String> fields) {
        require(object != null && object.isObject() && object.size() == fields.size());
        object.fieldNames().forEachRemaining(name -> require(fields.contains(name)));
        fields.forEach(name -> require(object.hasNonNull(name)));
    }

    private static long number(JsonNode object, String name) {
        JsonNode value = object.get(name);
        require(value.isIntegralNumber() && value.canConvertToLong() && value.longValue() > 0);
        return value.longValue();
    }

    private static String text(JsonNode object, String name, int maxLength) {
        JsonNode value = object.get(name);
        require(value.isTextual() && nonBlank(value.textValue(), maxLength));
        return value.textValue();
    }

    private static Instant utc(JsonNode object, String name) {
        String value = text(object, name, 40);
        require(value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]{1,9})?Z"));
        return OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant();
    }

    private static boolean nonBlank(String value, int maxLength) {
        return value != null && !value.isBlank() && value.length() <= maxLength;
    }

    private static boolean positive(Long value) {
        return value != null && value > 0;
    }

    private static void require(boolean condition) {
        if (!condition) {
            throw new IllegalArgumentException("Invalid payment verification input");
        }
    }
}
