package com.gitsat.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DeviceVerificationRequest(
        @JsonProperty("device_code") String deviceCode
) {
}
