package com.ngng.api.user.user.service.signup;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;

@Component
public class SignupCrypto {
    private final boolean enabled;
    private final String environment;
    private final String epoch;
    private final byte[] key;
    private final SecureRandom random = new SecureRandom();

    public SignupCrypto(@Value("${signup.verification.enabled:false}") boolean enabled,
                        @Value("${signup.verification.environment:}") String environment,
                        @Value("${signup.verification.epoch:}") String epoch,
                        @Value("${signup.verification.hmac-key:}") String encodedKey) {
        this.enabled = enabled;
        this.environment = environment;
        this.epoch = epoch;
        byte[] decoded;
        try { decoded = Base64.getDecoder().decode(encodedKey); } catch (IllegalArgumentException e) { decoded = new byte[0]; }
        this.key = decoded;
    }
    public void gate() {
        if (!enabled || !Set.of("local", "development", "test", "staging", "production").contains(environment)
                || !epoch.matches("[A-Za-z0-9_-]{16,64}") || key.length < 32) throw SignupFailure.unavailable();
    }
    public String token() {
        gate();
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    public String code() { gate(); return String.format(java.util.Locale.ROOT, "%06d", random.nextInt(1_000_000)); }
    public static void tokenFormat(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{43}")) throw SignupFailure.required();
    }
    public String digest(String purpose, String token) {
        gate(); tokenFormat(token);
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encoding(purpose, environment, epoch, "signup", token))); }
        catch (Exception e) { throw SignupFailure.unavailable(); }
    }
    public String hmac(String purpose, String... values) {
        gate();
        try {
            Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key, "HmacSHA256"));
            String[] fields = new String[values.length + 4];
            fields[0] = purpose; fields[1] = environment; fields[2] = epoch; fields[3] = "signup";
            System.arraycopy(values, 0, fields, 4, values.length);
            return HexFormat.of().formatHex(mac.doFinal(encoding(fields)));
        } catch (Exception e) { throw SignupFailure.unavailable(); }
    }
    private static byte[] encoding(String... fields) throws java.io.IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream data = new DataOutputStream(bytes);
        data.writeInt(1); data.writeInt(fields.length);
        for (String field : fields) { byte[] value = field.getBytes(StandardCharsets.UTF_8); data.writeInt(value.length); data.write(value); }
        return bytes.toByteArray();
    }
    public String key(String type, String channel, String digest) {
        gate(); return environment + ":{signup}:" + epoch + ":signup:" + channel + ":" + type + ":" + digest;
    }
}
