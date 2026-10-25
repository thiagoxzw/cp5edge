package com.wineguard.service;

import com.wineguard.domain.*;
import com.wineguard.dto.DeviceLiveView;
import com.wineguard.dto.DeviceResponse;
import com.wineguard.entity.Device;
import com.wineguard.entity.Incident;
import com.wineguard.monitoring.DeviceRuntimeState;
import com.wineguard.monitoring.DeviceStateRegistry;
import com.wineguard.telemetry.TelemetryReading;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Combina cadastro (banco) + estado em tempo real (memória) + incidentes abertos. */
@Component
public class DeviceViewMapper {

    private final DeviceStateRegistry states;

    public DeviceViewMapper(DeviceStateRegistry states) {
        this.states = states;
    }

    public DeviceResponse toResponse(Device d, Collection<Incident> openIncidentsOfDevice) {
        return new DeviceResponse(d.getId(), d.getName(), d.getDeviceKey(), d.getEntityId(), d.getZone(),
                d.getZone().label(), d.getDescription(), d.getSensors(), d.getStatus(), d.isSimulated(),
                d.getProvisioning(), d.getProvisioningMessage(), d.getCreatedAt(), d.getUpdatedAt(),
                live(d, openIncidentsOfDevice));
    }

    public DeviceLiveView live(Device d, Collection<Incident> open) {
        DeviceRuntimeState s = d.isActive() ? states.peek(d.getId()) : null;
        Connectivity connectivity = s != null ? s.getConnectivity() : Connectivity.NEVER_SEEN;
        long environmental = open.stream().filter(i -> i.getType() != IncidentType.DEVICE_OFFLINE).count();
        HealthStatus health = health(d, connectivity, environmental);

        TelemetryReading r = s != null ? s.getReading() : null;
        Instant lastSeen = s != null ? s.getLastSeenAt() : null;
        Set<Variable> buzzer = r != null ? r.reportedAlerts() : null;
        var score = s != null ? s.getScore() : null;

        return new DeviceLiveView(
                health,
                connectivity,
                s != null ? s.getEnvironmentLevel() : AlertLevel.NORMAL,
                r != null && d.hasSensor(SensorType.TEMPERATURE) ? r.temperature() : null,
                r != null && d.hasSensor(SensorType.HUMIDITY) ? r.humidity() : null,
                r != null && d.hasSensor(SensorType.LUMINOSITY) ? r.luminosity() : null,
                lastSeen,
                lastSeen != null ? Math.max(0, Duration.between(lastSeen, Instant.now()).toSeconds()) : null,
                s != null ? s.getConnectivitySince() : null,
                s != null ? s.getPending().stream().map(Variable::code).toList() : List.of(),
                score != null ? score.score() : null,
                score != null ? score.label() : null,
                score != null ? score.components() : List.of(),
                r != null ? r.reportedLed() : null,
                buzzer != null ? buzzer.stream().map(Variable::code).toList() : List.of(),
                r != null ? r.demoMode() : null,
                r != null ? r.uptime() : null,
                (int) environmental);
    }

    static HealthStatus health(Device d, Connectivity connectivity, long environmentalIncidents) {
        if (!d.isActive()) return HealthStatus.INACTIVE;
        if (connectivity == Connectivity.OFFLINE || connectivity == Connectivity.NEVER_SEEN) return HealthStatus.OFFLINE;
        if (environmentalIncidents > 0) return HealthStatus.ALERT;
        if (connectivity == Connectivity.UNSTABLE) return HealthStatus.UNSTABLE;
        return HealthStatus.ONLINE;
    }
}
