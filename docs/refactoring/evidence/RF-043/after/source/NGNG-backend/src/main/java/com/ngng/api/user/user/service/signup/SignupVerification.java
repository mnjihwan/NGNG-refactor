package com.ngng.api.user.user.service.signup;

import com.ngng.api.user.user.dto.request.JoinRequest;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class SignupVerification {
    private final SignupCrypto crypto;
    private final SignupStore store;
    public SignupVerification(SignupCrypto crypto, SignupStore store) { this.crypto = crypto; this.store = store; }
    public record Attempt(String attemptId, String attemptSecret, long expiresAt) {
        @Override public String toString() { return "Attempt[redacted]"; }
    }
    public record Challenge(String challengeId, long expiresAt) { }
    public record Proof(String proof, long expiresAt) {
        @Override public String toString() { return "Proof[redacted]"; }
    }
    // Reservation contains the transient code, and must never be serialized or logged.
    public record Reservation(String attemptId, String secretDigest, String channel, String challengeId, String code, long expiresAt) {
        @Override public String toString() { return "Reservation[redacted]"; }
    }

    private void channel(String channel) { if (!"email".equals(channel) && !"phone".equals(channel)) throw SignupFailure.required(); }

    public void gate() { crypto.gate(); }
    private String attemptKey(String id) { return crypto.key("attempt", "both", crypto.digest("signup-attempt-id", id)); }
    private String secret(String value) { return crypto.digest("signup-attempt-secret", value); }
    private String target(String channel, String value) { return crypto.hmac("signup-target-binding", channel, value); }
    private long expiry(List<String> result) { return Long.parseLong(result.get(1)); }
    private List<String> checked(List<String> result) {
        switch (result.get(0)) {
            case "OK": return result;
            case "REQUIRED": throw SignupFailure.required();
            case "RESTART": throw new SignupFailure(409, "SIGNUP_VERIFICATION_RESTART_REQUIRED");
            case "LIMIT": throw new SignupFailure(429, "SIGNUP_LIMIT_REACHED", Long.parseLong(result.get(1)));
            default: throw SignupFailure.unavailable();
        }
    }
    public Attempt create() {
        gate();
        for (int i = 0; i < 3; i++) {
            String id = crypto.token(), secret = crypto.token();
            List<String> result = store.run("create", List.of(attemptKey(id)), secret(secret));
            if (result.get(0).equals("COLLISION")) continue;
            return new Attempt(id, secret, expiry(checked(result)));
        }
        throw SignupFailure.unavailable();
    }
    public Reservation reserve(String id, String secret, String channel, String canonicalTarget) {
        gate(); channel(channel);
        String attemptKey = attemptKey(id), secretDigest = secret(secret);
        for (int i = 0; i < 3; i++) {
            String challenge = crypto.token(), code = crypto.code(), member = crypto.token();
            List<String> result = store.run("issue", List.of(attemptKey,
                    crypto.key("quota", channel, crypto.hmac("signup-target-quota", channel, canonicalTarget)),
                    crypto.key("challenge", channel, crypto.digest("signup-challenge-id", challenge))),
                    secretDigest, channel, target(channel, canonicalTarget), challenge,
                    crypto.hmac("signup-code-verifier", id, channel, challenge, code), member);
            if (result.get(0).equals("COLLISION")) continue;
            return new Reservation(id, secretDigest, channel, challenge, code, expiry(checked(result)));
        }
        throw SignupFailure.unavailable();
    }
    public Challenge complete(Reservation reservation, boolean sent) {
        gate();
        checked(store.run("complete", List.of(attemptKey(reservation.attemptId())), reservation.secretDigest(), reservation.channel(), reservation.challengeId(), sent ? "sent" : "failed"));
        if (!sent) throw SignupFailure.unavailable();
        return new Challenge(reservation.challengeId(), reservation.expiresAt());
    }
    public Proof verify(String id, String secret, String channel, String challenge, String code) {
        gate(); channel(channel); SignupCrypto.tokenFormat(challenge);
        if (code == null || !code.matches("[0-9]{6}")) throw SignupFailure.required();
        String proof = crypto.token();
        List<String> result = checked(store.run("verify", List.of(attemptKey(id)), secret(secret), channel, challenge,
                crypto.hmac("signup-code-verifier", id, channel, challenge, code), crypto.digest("signup-proof-" + channel, proof)));
        return new Proof(proof, expiry(result));
    }
    public void consume(JoinRequest request, String email, String phone) {
        gate();
        checked(store.run("consume", List.of(attemptKey(request.attemptId())), secret(request.attemptSecret()),
                target("email", email), crypto.digest("signup-proof-email", request.emailProof()),
                target("phone", phone), crypto.digest("signup-proof-phone", request.phoneProof())));
    }
}
