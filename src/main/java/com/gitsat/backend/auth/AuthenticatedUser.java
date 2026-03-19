package com.gitsat.backend.auth;

public record AuthenticatedUser(
        String email,
        String name
) {
}
