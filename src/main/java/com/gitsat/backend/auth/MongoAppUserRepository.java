package com.gitsat.backend.auth;

import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class MongoAppUserRepository implements AppUserRepository {

    private final SpringDataMongoAppUserRepository repository;

    public MongoAppUserRepository(SpringDataMongoAppUserRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<AppUser> findById(String id) {
        return repository.findById(id);
    }

    @Override
    public Optional<AppUser> findByEmail(String email) {
        return repository.findByEmail(email);
    }

    @Override
    public AppUser save(AppUser user) {
        try {
            return repository.save(user);
        } catch (DuplicateKeyException ex) {
            throw new IllegalStateException("User already exists.", ex);
        }
    }
}
