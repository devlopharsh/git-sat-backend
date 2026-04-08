package com.gitsat.backend.auth;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("app_users")
public record AppUser(
        @Id
        String id,
        @Indexed(unique = true)
        String email,
        String name,
        String passwordHash,
        Instant createdAt,
        Instant lastLoginAt
) {
    public AppUser withLastLoginAt(Instant value) {
        return new AppUser(id, email, name, passwordHash, createdAt, value);
    }

    public static AppUser newUser(String email, String name, String passwordHash, Instant createdAt) {
        return new AppUser(null, email, name, passwordHash, createdAt, null);
    }

    public AuthenticatedUser toAuthenticatedUser() {
        return new AuthenticatedUser(id, email, name);
    }
}
