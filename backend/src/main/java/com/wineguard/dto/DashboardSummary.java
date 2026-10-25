package com.wineguard.dto;

import com.wineguard.domain.AlertLevel;
import com.wineguard.domain.HealthStatus;
import com.wineguard.domain.Zone;

import java.time.Instant;
import java.util.List;

/** Tudo que a página inicial precisa em uma única chamada (GET /api/dashboard). */
public record DashboardSummary(
        Instant generatedAt,
        String mode,
        Counters counters,
        Integer overallScore,
        String overallScoreLabel,
        List<ZoneStatus> zones,
        List<DeviceResponse> devices,
        List<IncidentResponse> activeIncidents,
        List<EventResponse> recentEvents
) {

    public record Counters(int total, int online, int unstable, int alert, int offline, int inactive,
                           int activeIncidents, int criticalIncidents) {
    }

    /**
     * @param status normal | attention | critical | offline | empty  (cores do mapa)
     */
    public record ZoneStatus(Zone zone, String label, String status, List<ZoneDevice> devices) {
    }

    public record ZoneDevice(Long id, String name, HealthStatus health, AlertLevel environmentLevel,
                             Double temperature, Double humidity, Double luminosity, Integer score) {
    }
}
