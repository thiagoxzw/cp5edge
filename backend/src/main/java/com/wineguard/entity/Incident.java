package com.wineguard.entity;

import com.wineguard.domain.*;
import jakarta.persistence.*;

import java.time.Duration;
import java.time.Instant;

/**
 * Incidente: uma ocorrência ÚNICA enquanto a mesma anomalia (dispositivo + variável)
 * permanecer ativa. Leituras repetidas apenas atualizam valor atual/pico/severidade.
 */
@Entity
@Table(name = "incidents", indexes = {
        @Index(name = "idx_incident_device_status", columnList = "device_id,status"),
        @Index(name = "idx_incident_started", columnList = "startedAt")
})
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "device_id")
    private Device device;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IncidentType type;

    /** null para incidentes de conectividade (DEVICE_OFFLINE). */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Variable variable;

    /** Área no momento do incidente (snapshot, caso o dispositivo mude de local). */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private Zone zone;

    private Double detectedValue;
    private Double peakValue;
    private Double lastValue;
    private Double limitValue;

    /** Maior severidade atingida. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertLevel severity;

    /** Severidade no momento (pode cair se o valor se aproximar do limite). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertLevel currentSeverity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IncidentStatus status = IncidentStatus.ACTIVE;

    @Column(nullable = false)
    private Instant startedAt;
    private Instant acknowledgedAt;
    private Instant normalizedAt;

    /** Incidente gerado durante o Modo Demo. */
    @Column(nullable = false)
    private boolean demo;

    public boolean isOpen() {
        return status != IncidentStatus.RESOLVED;
    }

    public Duration duration(Instant now) {
        return Duration.between(startedAt, normalizedAt != null ? normalizedAt : now);
    }

    public Long getId() { return id; }
    public Device getDevice() { return device; }
    public void setDevice(Device device) { this.device = device; }
    public IncidentType getType() { return type; }
    public void setType(IncidentType type) { this.type = type; }
    public Variable getVariable() { return variable; }
    public void setVariable(Variable variable) { this.variable = variable; }
    public Zone getZone() { return zone; }
    public void setZone(Zone zone) { this.zone = zone; }
    public Double getDetectedValue() { return detectedValue; }
    public void setDetectedValue(Double detectedValue) { this.detectedValue = detectedValue; }
    public Double getPeakValue() { return peakValue; }
    public void setPeakValue(Double peakValue) { this.peakValue = peakValue; }
    public Double getLastValue() { return lastValue; }
    public void setLastValue(Double lastValue) { this.lastValue = lastValue; }
    public Double getLimitValue() { return limitValue; }
    public void setLimitValue(Double limitValue) { this.limitValue = limitValue; }
    public AlertLevel getSeverity() { return severity; }
    public void setSeverity(AlertLevel severity) { this.severity = severity; }
    public AlertLevel getCurrentSeverity() { return currentSeverity; }
    public void setCurrentSeverity(AlertLevel currentSeverity) { this.currentSeverity = currentSeverity; }
    public IncidentStatus getStatus() { return status; }
    public void setStatus(IncidentStatus status) { this.status = status; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(Instant acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }
    public Instant getNormalizedAt() { return normalizedAt; }
    public void setNormalizedAt(Instant normalizedAt) { this.normalizedAt = normalizedAt; }
    public boolean isDemo() { return demo; }
    public void setDemo(boolean demo) { this.demo = demo; }
}
