package com.gitsat.backend.auth;

import java.util.Optional;

public interface AppUserRepository {

    Optional<AppUser> findById(String id);

    Optional<AppUser> findByEmail(String email);

    AppUser save(AppUser user);
}
