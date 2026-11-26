package com.ngng.api.user.user.dto.request;

public record SignupVerifyRequest(String attemptId, String attemptSecret, String challengeId, String code) {
    @Override public String toString() { return "SignupVerifyRequest[redacted]"; }
}
