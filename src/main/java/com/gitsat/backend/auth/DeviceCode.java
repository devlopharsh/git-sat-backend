package com.gitsat.backend.auth;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("device_codes")
public record DeviceCode(
        @Id
        String id,
        @Indexed(unique = true)
        String deviceCodeHash,
        @Indexed(unique = true)
        String userCode,
        DeviceCodeStatus status,
        String userId,
        @Indexed(expireAfterSeconds = 0)
        Instant expiresAt,
        Instant createdAt,
        Instant lastPolledAt,
        int intervalSeconds
) {
    public static DeviceCode pending(String deviceCodeHash, String userCode, Instant expiresAt, Instant createdAt, int intervalSeconds) {
        return new DeviceCode(null, deviceCodeHash, userCode, DeviceCodeStatus.PENDING, null, expiresAt, createdAt, null, intervalSeconds);
    }

    public DeviceCode approve(String approvedUserId) {
        return new DeviceCode(id, deviceCodeHash, userCode, DeviceCodeStatus.APPROVED, approvedUserId, expiresAt, createdAt, lastPolledAt, intervalSeconds);
    }

    public DeviceCode complete() {
        return new DeviceCode(id, deviceCodeHash, userCode, DeviceCodeStatus.COMPLETED, userId, expiresAt, createdAt, lastPolledAt, intervalSeconds);
    }

    public DeviceCode expire() {
        return new DeviceCode(id, deviceCodeHash, userCode, DeviceCodeStatus.EXPIRED, userId, expiresAt, createdAt, lastPolledAt, intervalSeconds);
    }

    public DeviceCode withLastPolledAt(Instant value) {
        return new DeviceCode(id, deviceCodeHash, userCode, status, userId, expiresAt, createdAt, value, intervalSeconds);
    }
}
