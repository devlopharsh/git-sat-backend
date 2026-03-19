package com.gitsat.backend.dto;

public record LoginRequest(
        String email,
        String password
) {
}
