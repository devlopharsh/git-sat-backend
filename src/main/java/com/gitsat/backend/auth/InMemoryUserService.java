package com.gitsat.backend.auth;

import com.gitsat.backend.dto.LoginRequest;
import com.gitsat.backend.dto.RegisterRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InMemoryUserService {

    private final Map<String, AppUser> users = new ConcurrentHashMap<>();
    private final PasswordEncoder passwordEncoder;

    public InMemoryUserService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    public AuthenticatedUser register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        String password = normalizePassword(request.password());
        String name = normalizeName(request.name());

        AppUser newUser = new AppUser(
                email,
                name,
                passwordEncoder.encode(password),
                Instant.now()
        );

        AppUser existing = users.putIfAbsent(email, newUser);
        if (existing != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User already exists.");
        }
        return newUser.toAuthenticatedUser();
    }

    public AuthenticatedUser login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        String password = normalizePassword(request.password());

        AppUser user = users.get(email);
        if (user == null || !passwordEncoder.matches(password, user.passwordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
        }
        return user.toAuthenticatedUser();
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required.");
        }
        String normalized = email.trim().toLowerCase();
        if (!normalized.contains("@")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid email is required.");
        }
        return normalized;
    }

    private String normalizePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required.");
        }
        if (password.length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 8 characters.");
        }
        return password;
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required.");
        }
        String normalized = name.trim();
        if (normalized.length() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name must be at least 2 characters.");
        }
        return normalized;
    }
}
