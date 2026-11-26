package com.ngng.api.user.user.dto.response;
import com.fasterxml.jackson.annotation.JsonIgnore;
public record PhoneNumberAuthResponse(String challengeId, long expiresAt, @JsonIgnore boolean isSuccess) {
    public static PhoneNumberAuthResponse success(String challengeId, long expiresAt) { return new PhoneNumberAuthResponse(challengeId, expiresAt, true); }
    public static PhoneNumberAuthResponse fail() { return new PhoneNumberAuthResponse(null, 0, false); }
}
