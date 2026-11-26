package com.ngng.api.user.user.dto.request;

public record JoinRequest(String name,
                          String email,
                          String password,
                          String nickname,
                          String phoneNumber,
                          String attemptId,
                          String attemptSecret,
                          String emailProof,
                          String phoneProof) {
    public JoinRequest(String name, String email, String password, String nickname, String phoneNumber) {
        this(name, email, password, nickname, phoneNumber, null, null, null, null);
    }
    @Override public String toString() { return "JoinRequest[redacted]"; }
}
