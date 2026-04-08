package com.gitsat.backend.auth;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

interface SpringDataMongoDeviceCodeRepository extends MongoRepository<DeviceCode, String> {

    Optional<DeviceCode> findByDeviceCodeHash(String deviceCodeHash);

    Optional<DeviceCode> findByUserCode(String userCode);

    boolean existsByDeviceCodeHash(String deviceCodeHash);

    boolean existsByUserCode(String userCode);

    long deleteByExpiresAtBefore(Instant expiresAt);
}
