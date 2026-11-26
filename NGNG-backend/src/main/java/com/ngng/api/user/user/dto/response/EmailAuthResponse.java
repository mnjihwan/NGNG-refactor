package com.ngng.api.user.user.dto.response;
import com.fasterxml.jackson.annotation.JsonIgnore;
public record EmailAuthResponse(String challengeId, long expiresAt, @JsonIgnore boolean isSuccess) {
    public static EmailAuthResponse success(String challengeId, long expiresAt) { return new EmailAuthResponse(challengeId, expiresAt, true); }
    public static EmailAuthResponse fail() { return new EmailAuthResponse(null, 0, false); }
}
