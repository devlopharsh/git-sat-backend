package com.gitsat.backend.auth;

import com.gitsat.backend.dto.LoginRequest;
import com.gitsat.backend.dto.RegisterRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

@Service
public class AppUserService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AppUserService(AppUserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public AuthenticatedUser register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        String password = normalizePassword(request.password());
        String name = normalizeName(request.name());

        if (userRepository.findByEmail(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User already exists.");
        }

        AppUser newUser = AppUser.newUser(
                email,
                name,
                passwordEncoder.encode(password),
                Instant.now()
        );

        try {
            return userRepository.save(newUser).toAuthenticatedUser();
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User already exists.");
        }
    }

    public AuthenticatedUser login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        String password = normalizePassword(request.password());

        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password."));

        if (!passwordEncoder.matches(password, user.passwordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
        }

        AppUser updatedUser = userRepository.save(user.withLastLoginAt(Instant.now()));
        return updatedUser.toAuthenticatedUser();
    }

    public Optional<AuthenticatedUser> findAuthenticatedUserById(String userId) {
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findById(userId).map(AppUser::toAuthenticatedUser);
    }

    public Optional<AuthenticatedUser> findAuthenticatedUserByEmail(String email) {
        return findUserByEmail(email).map(AppUser::toAuthenticatedUser);
    }

    public Optional<AppUser> findUserById(String userId) {
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findById(userId);
    }

    public Optional<AppUser> findUserByEmail(String email) {
        String normalized = normalizeEmailForLookup(email);
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByEmail(normalized);
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required.");
        }
        String normalized = normalizeEmailForLookup(email);
        if (!normalized.contains("@")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid email is required.");
        }
        return normalized;
    }

    private String normalizeEmailForLookup(String email) {
        return email == null ? "" : email.trim().toLowerCase();
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
