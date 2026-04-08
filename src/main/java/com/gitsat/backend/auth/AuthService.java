package com.gitsat.backend.auth;

import com.gitsat.backend.dto.DeviceActivationResponse;
import com.gitsat.backend.dto.DeviceCodeResponse;
import com.gitsat.backend.dto.DeviceVerificationResponse;
import com.gitsat.backend.dto.RefreshTokenResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.net.URLEncoder;

@Service
public class AuthService {

    private static final char[] USER_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final DeviceCodeRepository deviceCodeRepository;
    private final AppUserService appUserService;
    private final JwtUtil jwtUtil;
    private final SecureRandom secureRandom = new SecureRandom();
    private final String verificationUrl;
    private final long deviceCodeTtlSeconds;
    private final int deviceCodeIntervalSeconds;

    public AuthService(
            DeviceCodeRepository deviceCodeRepository,
            AppUserService appUserService,
            JwtUtil jwtUtil,
            @Value("${auth.device-code.verification-url:http://localhost:8080/device}") String verificationUrl,
            @Value("${auth.device-code.ttl-seconds:600}") long deviceCodeTtlSeconds,
            @Value("${auth.device-code.interval-seconds:5}") int deviceCodeIntervalSeconds
    ) {
        this.deviceCodeRepository = deviceCodeRepository;
        this.appUserService = appUserService;
        this.jwtUtil = jwtUtil;
        this.verificationUrl = verificationUrl;
        this.deviceCodeTtlSeconds = deviceCodeTtlSeconds;
        this.deviceCodeIntervalSeconds = deviceCodeIntervalSeconds;
    }

    public DeviceCodeResponse createDeviceCode() {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(deviceCodeTtlSeconds);

        for (int attempt = 0; attempt < 10; attempt++) {
            String rawDeviceCode = generateRawDeviceCode();
            String deviceCodeHash = hashDeviceCode(rawDeviceCode);
            String userCode = generateUserCode();

            if (deviceCodeRepository.existsByDeviceCodeHash(deviceCodeHash)
                    || deviceCodeRepository.existsByUserCode(userCode)) {
                continue;
            }

            deviceCodeRepository.save(DeviceCode.pending(
                    deviceCodeHash,
                    userCode,
                    expiresAt,
                    now,
                    deviceCodeIntervalSeconds
            ));

            return new DeviceCodeResponse(
                    rawDeviceCode,
                    userCode,
                    buildVerificationUrl(userCode),
                    deviceCodeTtlSeconds,
                    deviceCodeIntervalSeconds
            );
        }

        throw new IllegalStateException("Failed to generate a unique device code.");
    }

    public DeviceActivationResponse activateDevice(String userCode, AuthenticatedUser authenticatedUser) {
        if (authenticatedUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required to activate a device.");
        }

        AppUser currentUser = appUserService.findUserById(authenticatedUser.userId())
                .or(() -> appUserService.findUserByEmail(authenticatedUser.email()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user not found."));

        DeviceCode deviceCode = deviceCodeRepository.findByUserCode(normalizeUserCode(userCode))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid user code."));

        if (isExpired(deviceCode)) {
            deviceCodeRepository.save(deviceCode.expire());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Device code has expired.");
        }

        if (deviceCode.status() == DeviceCodeStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Device code has already been used.");
        }

        if (deviceCode.status() == DeviceCodeStatus.EXPIRED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Device code has expired.");
        }

        if (deviceCode.status() == DeviceCodeStatus.APPROVED) {
            if (currentUser.id().equals(deviceCode.userId())) {
                return new DeviceActivationResponse("Device already approved.");
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Device code was approved by a different user.");
        }

        deviceCodeRepository.save(deviceCode.approve(currentUser.id()));
        return new DeviceActivationResponse("Device approved.");
    }

    public DeviceVerificationResponse verifyDeviceCode(String rawDeviceCode) {
        if (rawDeviceCode == null || rawDeviceCode.isBlank()) {
            return DeviceVerificationResponse.error("invalid_request");
        }

        DeviceCode deviceCode = deviceCodeRepository.findByDeviceCodeHash(hashDeviceCode(rawDeviceCode))
                .orElse(null);
        if (deviceCode == null) {
            return DeviceVerificationResponse.error("invalid_grant");
        }

        if (isExpired(deviceCode) || deviceCode.status() == DeviceCodeStatus.EXPIRED) {
            deviceCodeRepository.save(deviceCode.expire());
            return DeviceVerificationResponse.error("expired_token");
        }

        Instant now = Instant.now();
        if (deviceCode.lastPolledAt() != null
                && deviceCode.lastPolledAt().plusSeconds(deviceCode.intervalSeconds()).isAfter(now)) {
            deviceCodeRepository.save(deviceCode.withLastPolledAt(now));
            return DeviceVerificationResponse.error("slow_down");
        }

        if (deviceCode.status() == DeviceCodeStatus.PENDING) {
            deviceCodeRepository.save(deviceCode.withLastPolledAt(now));
            return DeviceVerificationResponse.error("authorization_pending");
        }

        if (deviceCode.status() == DeviceCodeStatus.COMPLETED) {
            return DeviceVerificationResponse.error("invalid_grant");
        }

        AppUser user = appUserService.findUserById(deviceCode.userId()).orElse(null);
        if (user == null) {
            return DeviceVerificationResponse.error("invalid_grant");
        }

        AuthenticatedUser authenticatedUser = user.toAuthenticatedUser();
        String accessToken = jwtUtil.generateAccessToken(authenticatedUser);
        String refreshToken = jwtUtil.generateRefreshToken(authenticatedUser);
        deviceCodeRepository.save(deviceCode.complete());

        return DeviceVerificationResponse.success(
                accessToken,
                refreshToken,
                jwtUtil.getAccessTokenTtlSeconds()
        );
    }

    public RefreshTokenResponse refreshAccessToken(String refreshToken) {
        AuthenticatedUser refreshUser = jwtUtil.parseRefreshToken(refreshToken);
        if (refreshUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token.");
        }

        AuthenticatedUser activeUser = appUserService.findAuthenticatedUserById(refreshUser.userId())
                .or(() -> appUserService.findAuthenticatedUserByEmail(refreshUser.email()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found."));

        return new RefreshTokenResponse(
                jwtUtil.generateAccessToken(activeUser),
                jwtUtil.getAccessTokenTtlSeconds()
        );
    }

    public long deleteExpiredDeviceCodes() {
        return deviceCodeRepository.deleteByExpiresAtBefore(Instant.now());
    }

    private boolean isExpired(DeviceCode deviceCode) {
        return Instant.now().isAfter(deviceCode.expiresAt());
    }

    private String generateRawDeviceCode() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String generateUserCode() {
        StringBuilder builder = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            builder.append(USER_CODE_CHARS[secureRandom.nextInt(USER_CODE_CHARS.length)]);
        }
        return builder.toString();
    }

    private String normalizeUserCode(String userCode) {
        if (userCode == null || userCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "user_code is required.");
        }
        return userCode.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    }

    private String buildVerificationUrl(String userCode) {
        String separator = verificationUrl.contains("?") ? "&" : "?";
        return verificationUrl + separator + "user_code=" + URLEncoder.encode(userCode, StandardCharsets.UTF_8);
    }

    private String hashDeviceCode(String deviceCode) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(deviceCode.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available.", e);
        }
    }
}


