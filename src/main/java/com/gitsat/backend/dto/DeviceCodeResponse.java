package com.gitsat.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DeviceCodeResponse(
        @JsonProperty("device_code") String deviceCode,
        @JsonProperty("user_code") String userCode,
        @JsonProperty("verification_url") String verificationUrl,
        @JsonProperty("expires_in") long expiresIn,
        @JsonProperty("interval") int interval
) {
}
