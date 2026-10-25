package com.wineguard.service;

import com.wineguard.domain.*;
import com.wineguard.dto.IncidentResponse;
import com.wineguard.entity.Device;
import com.wineguard.entity.EnvironmentConfig;
import com.wineguard.entity.Incident;
import com.wineguard.exception.BusinessException;
import com.wineguard.exception.NotFoundException;
import com.wineguard.repository.IncidentRepository;
import com.wineguard.rules.TriggerRules;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Ciclo de vida dos incidentes. Garante UMA ocorrência por (dispositivo, variável) enquanto a
 * anomalia persistir — leituras repetidas só atualizam valor atual, pico e severidade.
 */
@Service
public class IncidentService {

    public static final Set<IncidentStatus> OPEN = EnumSet.of(IncidentStatus.ACTIVE, IncidentStatus.ACKNOWLEDGED);

    private final IncidentRepository repository;
    private final EventService events;

    public IncidentService(IncidentRepository repository, EventService events) {
        this.repository = repository;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<Incident> openFor(Long deviceId) {
        return repository.findByDeviceIdAndStatusIn(deviceId, OPEN);
    }

    @Transactional(readOnly = true)
    public List<Incident> allOpen() {
        return repository.findByStatusIn(OPEN);
    }

    @Transactional(readOnly = true)
    public List<Incident> last24h(Long deviceId, Instant now) {
        return repository.findOverlapping(deviceId, now.minus(Duration.ofHours(24)));
    }

    /** Abre o incidente após o "tempo para disparo" ter sido cumprido. */
    @Transactional
    public Incident open(Device device, TriggerRules.Violation v, EnvironmentConfig config, Instant now, boolean demo) {
        Incident i = new Incident();
        i.setDevice(device);
        i.setZone(device.getZone());
        i.setVariable(v.variable());
        i.setType(IncidentType.of(v.variable(), v.high()));
        i.setDetectedValue(v.value());
        i.setPeakValue(v.value());
        i.setLastValue(v.value());
        i.setLimitValue(v.limit());
        AlertLevel level = TriggerRules.severity(v.variable(), v.deviation(), config);
        i.setSeverity(level);
        i.setCurrentSeverity(level);
        i.setStartedAt(now);
        i.setDemo(demo);
        i = repository.save(i);
        events.record(EventType.INCIDENT_OPENED, eventLevel(level), device, String.format(Locale.ROOT,
                "%s %s: %.1f%s (limite %.1f%s)", label(level), i.getType().label().toUpperCase(Locale.ROOT),
                v.value(), v.variable().unit(), v.limit(), v.variable().unit()));
        return i;
    }

    /** Nova leitura de uma anomalia já registrada: atualiza valores e escalona a severidade. */
    @Transactional
    public void update(Incident i, TriggerRules.Violation v, EnvironmentConfig config, Instant now) {
        i.setLastValue(v.value());
        boolean worse = v.high() ? v.value() > i.getPeakValue() : v.value() < i.getPeakValue();
        if (worse) i.setPeakValue(v.value());
        long minutes = Duration.between(i.getStartedAt(), now).toMinutes();
        AlertLevel current = TriggerRules.severity(v.variable(), v.deviation(), minutes, config);
        AlertLevel previousPeak = i.getSeverity();
        i.setCurrentSeverity(current);
        i.setSeverity(previousPeak.max(current));
        repository.save(i);
        if (i.getSeverity() != previousPeak) {
            events.record(EventType.INCIDENT_ESCALATED, eventLevel(i.getSeverity()), i.getDevice(), String.format(Locale.ROOT,
                    "%s escalado para %s (%.1f%s)", i.getType().label(), label(i.getSeverity()), v.value(), v.variable().unit()));
        }
    }

    @Transactional
    public void resolve(Incident i, Double lastValue, Instant now) {
        i.setStatus(IncidentStatus.RESOLVED);
        i.setNormalizedAt(now);
        if (lastValue != null) i.setLastValue(lastValue);
        i.setCurrentSeverity(AlertLevel.NORMAL);
        repository.save(i);
        events.record(EventType.INCIDENT_RESOLVED, EventLevel.SUCCESS, i.getDevice(),
                i.getType().label() + " normalizado após " + formatDuration(i.duration(now)));
    }

    /** Abre incidente de conectividade (se ainda não houver um aberto). */
    @Transactional
    public void openOffline(Device device, Instant lastSeenAt, Instant now) {
        boolean exists = openFor(device.getId()).stream().anyMatch(i -> i.getType() == IncidentType.DEVICE_OFFLINE);
        if (exists) return;
        Incident i = new Incident();
        i.setDevice(device);
        i.setZone(device.getZone());
        i.setType(IncidentType.DEVICE_OFFLINE);
        i.setSeverity(AlertLevel.CRITICAL);
        i.setCurrentSeverity(AlertLevel.CRITICAL);
        i.setStartedAt(lastSeenAt != null ? lastSeenAt : now);
        repository.save(i);
    }

    @Transactional
    public void resolveOffline(Device device, Instant now) {
        for (Incident i : openFor(device.getId())) {
            if (i.getType() == IncidentType.DEVICE_OFFLINE) {
                i.setStatus(IncidentStatus.RESOLVED);
                i.setNormalizedAt(now);
                i.setCurrentSeverity(AlertLevel.NORMAL);
                repository.save(i);
            }
        }
    }

    /** Reconhecimento pelo operador: silencia o buzzer; o LED continua até normalizar. */
    @Transactional
    public IncidentResponse acknowledge(Long id) {
        Incident i = repository.findById(id).orElseThrow(() -> new NotFoundException("Incidente " + id + " não encontrado"));
        if (i.getStatus() != IncidentStatus.ACTIVE) {
            throw new BusinessException(HttpStatus.CONFLICT, "Somente incidentes ATIVOS podem ser reconhecidos");
        }
        i.setStatus(IncidentStatus.ACKNOWLEDGED);
        i.setAcknowledgedAt(Instant.now());
        repository.save(i);
        events.record(EventType.INCIDENT_ACKNOWLEDGED, EventLevel.INFO, i.getDevice(),
                i.getType().label() + " reconhecido pelo operador (buzzer silenciado)");
        return IncidentResponse.from(i);
    }

    @Transactional(readOnly = true)
    public IncidentResponse get(Long id) {
        return repository.findById(id).map(IncidentResponse::from)
                .orElseThrow(() -> new NotFoundException("Incidente " + id + " não encontrado"));
    }

    /**
     * @param status "open" (ativos + reconhecidos), ACTIVE, ACKNOWLEDGED, RESOLVED ou null (todos)
     */
    @Transactional(readOnly = true)
    public Page<IncidentResponse> search(String status, Long deviceId, AlertLevel severity, IncidentType type,
                                         Instant from, Pageable pageable) {
        List<Specification<Incident>> specs = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            if (status.equalsIgnoreCase("open")) {
                specs.add((r, q, cb) -> r.get("status").in(OPEN));
            } else {
                IncidentStatus s = IncidentStatus.valueOf(status.toUpperCase(Locale.ROOT));
                specs.add((r, q, cb) -> cb.equal(r.get("status"), s));
            }
        }
        if (deviceId != null) specs.add((r, q, cb) -> cb.equal(r.get("device").get("id"), deviceId));
        if (severity != null) specs.add((r, q, cb) -> cb.equal(r.get("severity"), severity));
        if (type != null) specs.add((r, q, cb) -> cb.equal(r.get("type"), type));
        if (from != null) specs.add((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("startedAt"), from));
        return repository.findAll(Specification.allOf(specs), pageable).map(IncidentResponse::from);
    }

    @Transactional
    public int deleteResolvedDemoIncidents() {
        return repository.deleteResolvedDemoIncidents();
    }

    static EventLevel eventLevel(AlertLevel level) {
        return level.isAtLeast(AlertLevel.CRITICAL) ? EventLevel.CRITICAL : EventLevel.WARNING;
    }

    static String label(AlertLevel level) {
        return switch (level) {
            case NORMAL -> "NORMAL";
            case ATTENTION -> "ATENÇÃO";
            case CRITICAL -> "CRÍTICO";
            case EMERGENCY -> "EMERGÊNCIA";
        };
    }

    public static String formatDuration(Duration d) {
        long s = Math.max(0, d.toSeconds());
        return s >= 3600
                ? String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
                : String.format(Locale.ROOT, "%02d:%02d", s / 60, s % 60);
    }
}
