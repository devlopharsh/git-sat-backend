package com.gitsat.backend.auth;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

interface SpringDataMongoAppUserRepository extends MongoRepository<AppUser, String> {

    Optional<AppUser> findByEmail(String email);
}
