package com.gitsat.backend.auth;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryDeviceCodeRepository implements DeviceCodeRepository {

    private final ConcurrentMap<String, DeviceCode> deviceCodesById = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> deviceCodeIdsByHash = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> deviceCodeIdsByUserCode = new ConcurrentHashMap<>();

    @Override
    public Optional<DeviceCode> findByDeviceCodeHash(String deviceCodeHash) {
        String deviceCodeId = deviceCodeIdsByHash.get(deviceCodeHash);
        return deviceCodeId == null ? Optional.empty() : Optional.ofNullable(deviceCodesById.get(deviceCodeId));
    }

    @Override
    public Optional<DeviceCode> findByUserCode(String userCode) {
        String deviceCodeId = deviceCodeIdsByUserCode.get(userCode);
        return deviceCodeId == null ? Optional.empty() : Optional.ofNullable(deviceCodesById.get(deviceCodeId));
    }

    @Override
    public boolean existsByDeviceCodeHash(String deviceCodeHash) {
        return deviceCodeIdsByHash.containsKey(deviceCodeHash);
    }

    @Override
    public boolean existsByUserCode(String userCode) {
        return deviceCodeIdsByUserCode.containsKey(userCode);
    }

    @Override
    public DeviceCode save(DeviceCode deviceCode) {
        String deviceCodeId = deviceCode.id();
        if (deviceCodeId == null || deviceCodeId.isBlank()) {
            deviceCodeId = UUID.randomUUID().toString();
        }

        String existingIdByHash = deviceCodeIdsByHash.putIfAbsent(deviceCode.deviceCodeHash(), deviceCodeId);
        if (existingIdByHash != null && !existingIdByHash.equals(deviceCodeId)) {
            throw new IllegalStateException("Device code hash already exists.");
        }

        String existingIdByUserCode = deviceCodeIdsByUserCode.putIfAbsent(deviceCode.userCode(), deviceCodeId);
        if (existingIdByUserCode != null && !existingIdByUserCode.equals(deviceCodeId)) {
            throw new IllegalStateException("User code already exists.");
        }

        DeviceCode persistedDeviceCode = new DeviceCode(
                deviceCodeId,
                deviceCode.deviceCodeHash(),
                deviceCode.userCode(),
                deviceCode.status(),
                deviceCode.userId(),
                deviceCode.expiresAt(),
                deviceCode.createdAt(),
                deviceCode.lastPolledAt(),
                deviceCode.intervalSeconds()
        );
        deviceCodesById.put(deviceCodeId, persistedDeviceCode);
        deviceCodeIdsByHash.put(deviceCode.deviceCodeHash(), deviceCodeId);
        deviceCodeIdsByUserCode.put(deviceCode.userCode(), deviceCodeId);
        return persistedDeviceCode;
    }

    @Override
    public long deleteByExpiresAtBefore(Instant expiresAt) {
        List<String> expiredIds = deviceCodesById.values().stream()
                .filter(deviceCode -> deviceCode.expiresAt().isBefore(expiresAt))
                .map(DeviceCode::id)
                .toList();

        for (String expiredId : expiredIds) {
            DeviceCode removed = deviceCodesById.remove(expiredId);
            if (removed != null) {
                deviceCodeIdsByHash.remove(removed.deviceCodeHash(), expiredId);
                deviceCodeIdsByUserCode.remove(removed.userCode(), expiredId);
            }
        }

        return expiredIds.size();
    }
}
