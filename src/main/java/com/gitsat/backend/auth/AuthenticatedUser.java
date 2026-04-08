package com.gitsat.backend.auth;

public record AuthenticatedUser(
        String userId,
        String email,
        String name
) {
}
