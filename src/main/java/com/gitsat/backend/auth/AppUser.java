package com.gitsat.backend.auth;

import java.time.Instant;

public record AppUser(
        String email,
        String name,
        String passwordHash,
        Instant createdAt
) {
    public AuthenticatedUser toAuthenticatedUser() {
        return new AuthenticatedUser(email, name);
    }
}
