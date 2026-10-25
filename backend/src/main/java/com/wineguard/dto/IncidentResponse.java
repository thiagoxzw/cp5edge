package com.wineguard.dto;

import com.wineguard.domain.*;
import com.wineguard.entity.Incident;

import java.time.Instant;

public record IncidentResponse(
        Long id,
        Long deviceId,
        String deviceName,
        String deviceKey,
        Zone zone,
        String zoneLabel,
        IncidentType type,
        String typeLabel,
        Variable variable,
        String unit,
        Double detectedValue,
        Double peakValue,
        Double lastValue,
        Double limitValue,
        AlertLevel severity,
        AlertLevel currentSeverity,
        IncidentStatus status,
        Instant startedAt,
        Instant acknowledgedAt,
        Instant normalizedAt,
        long durationSeconds,
        boolean demo
) {

    public static IncidentResponse from(Incident i) {
        Instant now = Instant.now();
        return new IncidentResponse(
                i.getId(),
                i.getDevice().getId(),
                i.getDevice().getName(),
                i.getDevice().getDeviceKey(),
                i.getZone(),
                i.getZone() != null ? i.getZone().label() : null,
                i.getType(),
                i.getType().label(),
                i.getVariable(),
                i.getVariable() != null ? i.getVariable().unit() : null,
                i.getDetectedValue(),
                i.getPeakValue(),
                i.getLastValue(),
                i.getLimitValue(),
                i.getSeverity(),
                i.getCurrentSeverity(),
                i.getStatus(),
                i.getStartedAt(),
                i.getAcknowledgedAt(),
                i.getNormalizedAt(),
                i.duration(now).toSeconds(),
                i.isDemo());
    }
}
