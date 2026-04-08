package com.gitsat.backend.auth;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryAppUserRepository implements AppUserRepository {

    private final ConcurrentMap<String, AppUser> usersById = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> userIdsByEmail = new ConcurrentHashMap<>();

    @Override
    public Optional<AppUser> findById(String id) {
        return Optional.ofNullable(usersById.get(id));
    }

    @Override
    public Optional<AppUser> findByEmail(String email) {
        String userId = userIdsByEmail.get(email);
        return userId == null ? Optional.empty() : Optional.ofNullable(usersById.get(userId));
    }

    @Override
    public AppUser save(AppUser user) {
        String userId = user.id();
        if (userId == null || userId.isBlank()) {
            userId = UUID.randomUUID().toString();
        }

        String existingUserId = userIdsByEmail.putIfAbsent(user.email(), userId);
        if (existingUserId != null && !existingUserId.equals(userId)) {
            throw new IllegalStateException("User already exists.");
        }

        AppUser persistedUser = new AppUser(
                userId,
                user.email(),
                user.name(),
                user.passwordHash(),
                user.createdAt(),
                user.lastLoginAt()
        );
        usersById.put(userId, persistedUser);
        userIdsByEmail.put(user.email(), userId);
        return persistedUser;
    }
}
