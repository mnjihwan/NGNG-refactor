package com.ngng.api.user.user.dto.request;

import lombok.Builder;

@Builder
public record PhoneNumberAuthRequest(String name,
                                     String phoneNumber, String attemptId, String attemptSecret) {
    public PhoneNumberAuthRequest(String name, String phoneNumber) { this(name, phoneNumber, null, null); }
    @Override public String toString() { return "PhoneNumberAuthRequest[redacted]"; }
}
