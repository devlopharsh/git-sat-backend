package com.gitsat.backend.auth;

import java.time.Instant;
import java.util.Optional;

public interface DeviceCodeRepository {

    Optional<DeviceCode> findByDeviceCodeHash(String deviceCodeHash);

    Optional<DeviceCode> findByUserCode(String userCode);

    boolean existsByDeviceCodeHash(String deviceCodeHash);

    boolean existsByUserCode(String userCode);

    DeviceCode save(DeviceCode deviceCode);

    long deleteByExpiresAtBefore(Instant expiresAt);
}
