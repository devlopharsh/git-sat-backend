package com.gitsat.backend.dto;

public record RegisterRequest(
        String name,
        String email,
        String password
) {
}
