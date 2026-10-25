package com.wineguard.dto;

import java.time.Instant;
import java.util.List;

/** Saúde da plataforma exibida na barra superior do dashboard. */
public record SystemStatus(
        String mode,
        String fiwareService,
        String fiwareServicePath,
        String entityType,
        List<Component> components,
        Instant lastCycleAt,
        boolean telemetryAvailable,
        String telemetryError,
        boolean provisioningReady,
        String provisioningError,
        boolean simulatorReady
) {

    public record Component(String name, String url, boolean ok, String version, String detail) {
    }
}
