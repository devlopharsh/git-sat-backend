package com.gitsat.backend.dto;

public record AuthResponse(
        String email,
        String name,
        String message
) {
}
