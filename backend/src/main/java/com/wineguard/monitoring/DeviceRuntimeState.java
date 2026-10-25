package com.wineguard.monitoring;

import com.wineguard.domain.AlertLevel;
import com.wineguard.domain.Connectivity;
import com.wineguard.domain.Variable;
import com.wineguard.rules.WineScore;
import com.wineguard.telemetry.TelemetryReading;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Estado em tempo de execução de um dispositivo (memória). Os valores atuais têm como fonte da
 * verdade o Orion; incidentes ficam no banco. Por isso este estado pode ser reconstruído após
 * um restart sem perda de informação relevante.
 * <p>
 * Escrito apenas pela thread do ciclo de monitoramento; lido pela API (campos voláteis).
 */
public class DeviceRuntimeState {

    private final Long deviceId;

    // --- Telemetria / heartbeat ---
    volatile TelemetryReading reading;
    volatile Instant lastSeenAt;
    volatile Connectivity connectivity = Connectivity.NEVER_SEEN;
    volatile Instant connectivitySince = Instant.now();
    String lastMarker;

    // --- Avaliação de triggers ---
    /** Desde quando cada variável está fora da faixa (antes de virar incidente = "pendente"). */
    final Map<Variable, Instant> violationSince = new EnumMap<>(Variable.class);
    /** Desde quando a variável voltou ao normal (para encerrar incidente após clearSeconds). */
    final Map<Variable, Instant> normalSince = new EnumMap<>(Variable.class);
    volatile Set<Variable> pending = EnumSet.noneOf(Variable.class);
    volatile AlertLevel environmentLevel = AlertLevel.NORMAL;
    volatile WineScore score;

    // --- Atuação (o que o backend já mandou o dispositivo fazer) ---
    Set<Variable> sentAlerts;
    Boolean sentLed;
    Instant lastActuationAt;
    Instant lastActuationFailureAt;
    volatile Instant lastAlertCommandAt;
    volatile Instant lastLedOnCommandAt;

    // --- Comandos manuais do dashboard: intenção própria, independente dos incidentes ---
    /** Alertas sonoros ligados manualmente (ativos até alertStop manual ou até manualUntil). */
    final Set<Variable> manualAlerts = EnumSet.noneOf(Variable.class);
    /** LED ligado manualmente (até ledOff manual ou até manualUntil). */
    boolean manualLed;
    /** Expiração do override manual; null = nenhum comando manual ativo. */
    Instant manualUntil;

    public DeviceRuntimeState(Long deviceId) {
        this.deviceId = deviceId;
    }

    public Long getDeviceId() { return deviceId; }
    public TelemetryReading getReading() { return reading; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public Connectivity getConnectivity() { return connectivity; }
    public Instant getConnectivitySince() { return connectivitySince; }
    public Set<Variable> getPending() { return pending; }
    public AlertLevel getEnvironmentLevel() { return environmentLevel; }
    public WineScore getScore() { return score; }
    public Instant getLastAlertCommandAt() { return lastAlertCommandAt; }
    public Instant getLastLedOnCommandAt() { return lastLedOnCommandAt; }

    /** Cópia de "desde quando cada variável está fora da faixa" (escrito pelo ciclo de monitoramento). */
    public Map<Variable, Instant> violationSinceSnapshot() {
        try {
            return new EnumMap<>(violationSince);
        } catch (java.util.ConcurrentModificationException e) {
            return new EnumMap<>(Variable.class);   // leitura concorrente rara: o próximo refresh corrige
        }
    }

    public synchronized Set<Variable> getManualAlerts() {
        return manualAlerts.isEmpty() ? EnumSet.noneOf(Variable.class) : EnumSet.copyOf(manualAlerts);
    }
    public synchronized boolean isManualLed() { return manualLed; }
    public synchronized Instant getManualUntil() { return manualUntil; }

    public void markAlertCommand(Instant at) { this.lastAlertCommandAt = at; }
    public void markLedOnCommand(Instant at) { this.lastLedOnCommandAt = at; }

    public boolean isReachable() {
        return connectivity == Connectivity.ONLINE || connectivity == Connectivity.UNSTABLE;
    }
}
