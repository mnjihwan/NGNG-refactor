package com.ngng.api.user.user.dto.request;

public record EmailAuthRequest(String name,
                               String email, String attemptId, String attemptSecret) {
    public EmailAuthRequest(String name, String email) { this(name, email, null, null); }
    @Override public String toString() { return "EmailAuthRequest[redacted]"; }
}
