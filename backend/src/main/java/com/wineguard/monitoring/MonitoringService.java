package com.wineguard.monitoring;

import com.wineguard.domain.*;
import com.wineguard.entity.Device;
import com.wineguard.entity.EnvironmentConfig;
import com.wineguard.entity.Incident;
import com.wineguard.repository.DeviceRepository;
import com.wineguard.rules.WineScoreCalculator;
import com.wineguard.service.EnvironmentConfigService;
import com.wineguard.service.EventService;
import com.wineguard.service.IncidentService;
import com.wineguard.telemetry.TelemetryReading;
import com.wineguard.telemetry.TelemetrySource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Ciclo de monitoramento (a cada TELEMETRY_POLL_INTERVAL_MS, padrão 2s):
 * <ol>
 *   <li>Lê os valores atuais de todos os dispositivos (Orion ou memória)</li>
 *   <li>Heartbeat: atualiza último contato e conectividade (online/instável/offline)</li>
 *   <li>Avalia triggers -> abre/atualiza/encerra incidentes</li>
 *   <li>Recalcula o Wine Score</li>
 *   <li>Reconcilia LED/buzzer do dispositivo com os incidentes abertos</li>
 * </ol>
 * Se a fonte (Orion) estiver indisponível, o ciclo NÃO marca os dispositivos como offline:
 * a falha é da plataforma, não dos ESP32 — o dashboard mostra o problema na barra de status.
 */
@Service
public class MonitoringService {

    private static final Logger log = LoggerFactory.getLogger(MonitoringService.class);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final DeviceRepository devices;
    private final TelemetrySource source;
    private final EnvironmentConfigService configService;
    private final DeviceStateRegistry states;
    private final IncidentService incidents;
    private final TriggerEvaluator triggers;
    private final AlertActuationService actuation;
    private final EventService events;
    private final DemoState demo;

    private volatile Instant lastCycleAt;
    private volatile boolean sourceAvailable = true;
    private volatile String sourceError;
    private volatile Instant lastSourceErrorLog = Instant.EPOCH;

    public MonitoringService(DeviceRepository devices, TelemetrySource source, EnvironmentConfigService configService,
                             DeviceStateRegistry states, IncidentService incidents, TriggerEvaluator triggers,
                             AlertActuationService actuation, EventService events, DemoState demo) {
        this.devices = devices;
        this.source = source;
        this.configService = configService;
        this.states = states;
        this.incidents = incidents;
        this.triggers = triggers;
        this.actuation = actuation;
        this.events = events;
        this.demo = demo;
    }

    @Scheduled(initialDelay = 2000, fixedDelayString = "${wineguard.telemetry-poll-interval-ms}")
    public void scheduledCycle() {
        try {
            runCycle(Instant.now());
        } catch (Exception e) {
            log.error("Erro no ciclo de monitoramento", e);
        }
    }

    public void runCycle(Instant now) {
        EnvironmentConfig config = configService.current();
        List<Device> active = devices.findByStatus(DeviceStatus.ACTIVE);
        states.retainOnly(active.stream().map(Device::getId).collect(Collectors.toSet()));

        Map<String, TelemetryReading> readings;
        try {
            readings = source.fetchLatest(active);
            if (!sourceAvailable) {
                events.record(EventType.SYSTEM, EventLevel.SUCCESS, null, source.name() + " voltou a responder");
            }
            sourceAvailable = true;
            sourceError = null;
        } catch (RuntimeException e) {
            if (sourceAvailable) {
                events.record(EventType.SYSTEM, EventLevel.CRITICAL, null, source.name() + " indisponível: " + e.getMessage());
            }
            sourceAvailable = false;
            sourceError = e.getMessage();
            if (Duration.between(lastSourceErrorLog, now).toSeconds() > 60) {
                log.warn("Fonte de telemetria indisponível: {}", e.getMessage());
                lastSourceErrorLog = now;
            }
            lastCycleAt = now;
            return;
        }

        for (Device device : active) {
            try {
                processDevice(device, readings.get(device.getEntityId()), config, now);
            } catch (RuntimeException e) {
                log.error("Falha ao processar {}", device.getDeviceKey(), e);
            }
        }
        lastCycleAt = now;
    }

    private void processDevice(Device device, TelemetryReading reading, EnvironmentConfig config, Instant now) {
        DeviceRuntimeState state = states.get(device.getId());

        // 1) Heartbeat: "último contato" usa o relógio do backend quando o conteúdo muda
        if (reading != null) {
            String marker = reading.changeMarker();
            if (state.lastMarker == null) {
                Instant src = reading.sourceTime();
                state.lastSeenAt = src == null || src.isAfter(now) ? now : src;
            } else if (!marker.equals(state.lastMarker)) {
                state.lastSeenAt = now;
            }
            state.lastMarker = marker;
            state.reading = reading;
        }
        updateConnectivity(device, state, config, now);

        // 2) Triggers e incidentes (somente com o dispositivo se comunicando)
        if (state.isReachable() && state.reading != null) {
            triggers.evaluate(device, state, state.reading, config, incidents.openFor(device.getId()), now,
                    demo.isDemo(device.getId()));
        }

        List<Incident> open = incidents.openFor(device.getId());
        state.environmentLevel = environmentLevel(state, open);

        // 3) Wine Score
        if (state.isReachable() && state.reading != null) {
            Map<Variable, Double> values = new EnumMap<>(Variable.class);
            for (Variable v : Variable.values()) {
                Double value = state.reading.value(v);
                if (device.hasSensor(v.sensor()) && value != null) values.put(v, value);
            }
            state.score = WineScoreCalculator.compute(values, config, incidents.last24h(device.getId(), now), now);

            // 4) Atuação remota (LED + buzzer)
            actuation.reconcile(device, state, open, config, now);
        } else {
            state.score = null;
        }
    }

    private void updateConnectivity(Device device, DeviceRuntimeState state, EnvironmentConfig config, Instant now) {
        Connectivity previous = state.connectivity;
        Connectivity next = Heartbeat.evaluate(state.lastSeenAt, now, config);
        if (next == previous) return;

        state.connectivity = next;
        state.connectivitySince = next == Connectivity.OFFLINE && state.lastSeenAt != null ? state.lastSeenAt : now;

        switch (next) {
            case OFFLINE -> {
                String since = state.lastSeenAt != null ? TIME.format(state.lastSeenAt) : "?";
                events.record(EventType.DEVICE_OFFLINE, EventLevel.CRITICAL, device, "Dispositivo OFFLINE — último contato às " + since);
                incidents.openOffline(device, state.lastSeenAt, now);
                triggers.reset(state);
                actuation.forget(state);
            }
            case UNSTABLE -> {
                if (previous == Connectivity.ONLINE) {
                    events.record(EventType.DEVICE_OFFLINE, EventLevel.WARNING, device, "Comunicação instável (mensagens atrasadas)");
                }
            }
            case ONLINE -> {
                if (previous == Connectivity.OFFLINE || previous == Connectivity.NEVER_SEEN) {
                    events.record(EventType.DEVICE_ONLINE, EventLevel.SUCCESS, device, "Dispositivo ONLINE");
                }
            }
            case NEVER_SEEN -> { }
        }
        if ((next == Connectivity.ONLINE || next == Connectivity.UNSTABLE)
                && (previous == Connectivity.OFFLINE || previous == Connectivity.NEVER_SEEN)) {
            incidents.resolveOffline(device, now);
        }
    }

    static AlertLevel environmentLevel(DeviceRuntimeState state, List<Incident> open) {
        AlertLevel level = state.getPending().isEmpty() ? AlertLevel.NORMAL : AlertLevel.ATTENTION;
        for (Incident i : open) {
            if (i.getType() != IncidentType.DEVICE_OFFLINE) level = level.max(i.getCurrentSeverity());
        }
        return level;
    }

    public Instant getLastCycleAt() { return lastCycleAt; }
    public boolean isSourceAvailable() { return sourceAvailable; }
    public String getSourceError() { return sourceError; }
    public String getSourceName() { return source.name(); }
}
