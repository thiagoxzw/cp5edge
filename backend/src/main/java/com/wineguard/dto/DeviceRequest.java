package com.wineguard.dto;

import com.wineguard.domain.DeviceStatus;
import com.wineguard.domain.SensorType;
import com.wineguard.domain.Zone;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

/** Corpo de POST/PUT /api/devices. */
public record DeviceRequest(
        @NotBlank @Size(max = 80) String name,
        /** device_id usado no IoT Agent e no tópico MQTT. */
        @NotBlank
        @Pattern(regexp = "^[a-zA-Z0-9_-]{2,40}$", message = "use 2–40 caracteres: letras, números, _ ou -")
        String deviceKey,
        @NotNull Zone zone,
        @Size(max = 500) String description,
        Set<SensorType> sensors,
        DeviceStatus status,
        /** Dispositivo virtual (simulado pelo backend). */
        boolean simulated
) {
}
