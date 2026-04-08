package com.gitsat.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ActivateDeviceRequest(
        @JsonProperty("user_code") String userCode
) {
}
