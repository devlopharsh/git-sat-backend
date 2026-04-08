package com.gitsat.backend.auth;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DeviceCodeCleanupJob {

    private final AuthService authService;

    public DeviceCodeCleanupJob(AuthService authService) {
        this.authService = authService;
    }

    @Scheduled(fixedDelayString = "${auth.device-code.cleanup-interval-ms:60000}")
    public void removeExpiredCodes() {
        authService.deleteExpiredDeviceCodes();
    }
}
