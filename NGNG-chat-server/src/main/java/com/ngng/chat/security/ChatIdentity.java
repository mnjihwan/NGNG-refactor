package com.ngng.chat.security;

import java.security.Principal;
import java.time.Instant;

/** Server-resolved identity; never retains the bearer credential. */
public record ChatIdentity(Long userId, String email, String role, Instant expiresAt) implements Principal {
    @Override public String getName() { return email; }
}
