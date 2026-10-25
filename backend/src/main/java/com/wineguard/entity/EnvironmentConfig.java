package com.wineguard.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Configuração do ambiente (triggers). Registro único (id = 1), persistido no banco
 * e editado pela tela "Configuração do Ambiente". Nada disso fica hardcoded no
 * frontend ou no ESP32: o firmware só executa os comandos que o backend decide enviar.
 */
@Entity
@Table(name = "environment_config")
public class EnvironmentConfig {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    // --- Temperatura (°C) ---
    private double temperatureMin = 10.0;
    private double temperatureMax = 18.0;
    private int temperatureTriggerSeconds = 15;

    // --- Umidade relativa (%) ---
    private double humidityMin = 50.0;
    private double humidityMax = 80.0;
    private int humidityTriggerSeconds = 15;

    // --- Luminosidade (% do LDR) ---
    private double luminosityMax = 30.0;
    private int luminosityTriggerSeconds = 10;

    // --- Faixas de severidade (desvio além do limite) ---
    private double temperatureCriticalDelta = 2.0;
    private double temperatureEmergencyDelta = 5.0;
    private double humidityCriticalDelta = 5.0;
    private double humidityEmergencyDelta = 15.0;
    private double luminosityCriticalDelta = 10.0;
    private double luminosityEmergencyDelta = 30.0;
    /** Incidente CRÍTICO que dura mais que isso é escalado para EMERGÊNCIA. */
    private int escalationMinutes = 10;

    /** Tempo em condição normal para encerrar o incidente (evita "flapping"). */
    private int clearSeconds = 5;

    // --- Atuadores (processo de alerta configurado pelo frontend) ---
    private boolean buzzerEnabled = true;
    private boolean ledEnabled = true;

    // --- Heartbeat ---
    private int unstableAfterSeconds = 20;
    private int offlineTimeoutSeconds = 60;

    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public double getTemperatureMin() { return temperatureMin; }
    public void setTemperatureMin(double v) { this.temperatureMin = v; }
    public double getTemperatureMax() { return temperatureMax; }
    public void setTemperatureMax(double v) { this.temperatureMax = v; }
    public int getTemperatureTriggerSeconds() { return temperatureTriggerSeconds; }
    public void setTemperatureTriggerSeconds(int v) { this.temperatureTriggerSeconds = v; }
    public double getHumidityMin() { return humidityMin; }
    public void setHumidityMin(double v) { this.humidityMin = v; }
    public double getHumidityMax() { return humidityMax; }
    public void setHumidityMax(double v) { this.humidityMax = v; }
    public int getHumidityTriggerSeconds() { return humidityTriggerSeconds; }
    public void setHumidityTriggerSeconds(int v) { this.humidityTriggerSeconds = v; }
    public double getLuminosityMax() { return luminosityMax; }
    public void setLuminosityMax(double v) { this.luminosityMax = v; }
    public int getLuminosityTriggerSeconds() { return luminosityTriggerSeconds; }
    public void setLuminosityTriggerSeconds(int v) { this.luminosityTriggerSeconds = v; }
    public double getTemperatureCriticalDelta() { return temperatureCriticalDelta; }
    public void setTemperatureCriticalDelta(double v) { this.temperatureCriticalDelta = v; }
    public double getTemperatureEmergencyDelta() { return temperatureEmergencyDelta; }
    public void setTemperatureEmergencyDelta(double v) { this.temperatureEmergencyDelta = v; }
    public double getHumidityCriticalDelta() { return humidityCriticalDelta; }
    public void setHumidityCriticalDelta(double v) { this.humidityCriticalDelta = v; }
    public double getHumidityEmergencyDelta() { return humidityEmergencyDelta; }
    public void setHumidityEmergencyDelta(double v) { this.humidityEmergencyDelta = v; }
    public double getLuminosityCriticalDelta() { return luminosityCriticalDelta; }
    public void setLuminosityCriticalDelta(double v) { this.luminosityCriticalDelta = v; }
    public double getLuminosityEmergencyDelta() { return luminosityEmergencyDelta; }
    public void setLuminosityEmergencyDelta(double v) { this.luminosityEmergencyDelta = v; }
    public int getEscalationMinutes() { return escalationMinutes; }
    public void setEscalationMinutes(int v) { this.escalationMinutes = v; }
    public int getClearSeconds() { return clearSeconds; }
    public void setClearSeconds(int v) { this.clearSeconds = v; }
    public boolean isBuzzerEnabled() { return buzzerEnabled; }
    public void setBuzzerEnabled(boolean v) { this.buzzerEnabled = v; }
    public boolean isLedEnabled() { return ledEnabled; }
    public void setLedEnabled(boolean v) { this.ledEnabled = v; }
    public int getUnstableAfterSeconds() { return unstableAfterSeconds; }
    public void setUnstableAfterSeconds(int v) { this.unstableAfterSeconds = v; }
    public int getOfflineTimeoutSeconds() { return offlineTimeoutSeconds; }
    public void setOfflineTimeoutSeconds(int v) { this.offlineTimeoutSeconds = v; }
    public Instant getUpdatedAt() { return updatedAt; }
}
