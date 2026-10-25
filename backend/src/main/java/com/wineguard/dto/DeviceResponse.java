package com.wineguard.dto;

import com.wineguard.domain.DeviceStatus;
import com.wineguard.domain.ProvisioningStatus;
import com.wineguard.domain.SensorType;
import com.wineguard.domain.Zone;

import java.time.Instant;
import java.util.Set;

public record DeviceResponse(
        Long id,
        String name,
        String deviceKey,
        String entityId,
        Zone zone,
        String zoneLabel,
        String description,
        Set<SensorType> sensors,
        DeviceStatus status,
        boolean simulated,
        ProvisioningStatus provisioning,
        String provisioningMessage,
        Instant createdAt,
        Instant updatedAt,
        DeviceLiveView live
) {
}
