package com.wineguard.dto;

import com.wineguard.domain.DeviceStatus;
import jakarta.validation.constraints.NotNull;

public record StatusChangeRequest(@NotNull DeviceStatus status) {
}
