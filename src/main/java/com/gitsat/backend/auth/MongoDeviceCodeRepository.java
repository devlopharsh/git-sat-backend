package com.gitsat.backend.auth;

import java.time.Instant;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class MongoDeviceCodeRepository implements DeviceCodeRepository {

    private final SpringDataMongoDeviceCodeRepository repository;

    public MongoDeviceCodeRepository(SpringDataMongoDeviceCodeRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<DeviceCode> findByDeviceCodeHash(String deviceCodeHash) {
        return repository.findByDeviceCodeHash(deviceCodeHash);
    }

    @Override
    public Optional<DeviceCode> findByUserCode(String userCode) {
        return repository.findByUserCode(userCode);
    }

    @Override
    public boolean existsByDeviceCodeHash(String deviceCodeHash) {
        return repository.existsByDeviceCodeHash(deviceCodeHash);
    }

    @Override
    public boolean existsByUserCode(String userCode) {
        return repository.existsByUserCode(userCode);
    }

    @Override
    public DeviceCode save(DeviceCode deviceCode) {
        try {
            return repository.save(deviceCode);
        } catch (DuplicateKeyException ex) {
            throw new IllegalStateException("Device code already exists.", ex);
        }
    }

    @Override
    public long deleteByExpiresAtBefore(Instant expiresAt) {
        return repository.deleteByExpiresAtBefore(expiresAt);
    }
}
