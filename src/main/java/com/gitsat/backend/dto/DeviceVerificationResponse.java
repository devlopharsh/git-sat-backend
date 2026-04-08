package com.gitsat.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DeviceVerificationResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("expires_in") Long expiresIn,
        String error
) {
    public static DeviceVerificationResponse success(String accessToken, String refreshToken, long expiresIn) {
        return new DeviceVerificationResponse(accessToken, refreshToken, expiresIn, null);
    }

    public static DeviceVerificationResponse error(String error) {
        return new DeviceVerificationResponse(null, null, null, error);
    }
}
