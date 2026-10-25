package com.wineguard.dto;

import com.wineguard.entity.EnvironmentConfig;
import jakarta.validation.constraints.*;

import java.time.Instant;

/** Corpo de GET/PUT /api/config/environment. */
public record EnvironmentConfigDto(
        @NotNull @DecimalMin("-10") @DecimalMax("40") Double temperatureMin,
        @NotNull @DecimalMin("-10") @DecimalMax("40") Double temperatureMax,
        @NotNull @Min(0) @Max(3600) Integer temperatureTriggerSeconds,

        @NotNull @DecimalMin("0") @DecimalMax("100") Double humidityMin,
        @NotNull @DecimalMin("0") @DecimalMax("100") Double humidityMax,
        @NotNull @Min(0) @Max(3600) Integer humidityTriggerSeconds,

        @NotNull @DecimalMin("0") @DecimalMax("100") Double luminosityMax,
        @NotNull @Min(0) @Max(3600) Integer luminosityTriggerSeconds,

        @NotNull @DecimalMin("0.1") Double temperatureCriticalDelta,
        @NotNull @DecimalMin("0.1") Double temperatureEmergencyDelta,
        @NotNull @DecimalMin("0.1") Double humidityCriticalDelta,
        @NotNull @DecimalMin("0.1") Double humidityEmergencyDelta,
        @NotNull @DecimalMin("0.1") Double luminosityCriticalDelta,
        @NotNull @DecimalMin("0.1") Double luminosityEmergencyDelta,
        @NotNull @Min(1) @Max(1440) Integer escalationMinutes,
        @NotNull @Min(0) @Max(600) Integer clearSeconds,

        @NotNull Boolean buzzerEnabled,
        @NotNull Boolean ledEnabled,

        @NotNull @Min(5) @Max(3600) Integer unstableAfterSeconds,
        @NotNull @Min(10) @Max(86400) Integer offlineTimeoutSeconds,

        Instant updatedAt
) {

    @AssertTrue(message = "Temperatura mínima deve ser menor que a máxima")
    public boolean isTemperatureRangeValid() {
        return temperatureMin == null || temperatureMax == null || temperatureMin < temperatureMax;
    }

    @AssertTrue(message = "Umidade mínima deve ser menor que a máxima")
    public boolean isHumidityRangeValid() {
        return humidityMin == null || humidityMax == null || humidityMin < humidityMax;
    }

    @AssertTrue(message = "A faixa de emergência deve ser maior que a faixa crítica")
    public boolean isSeverityBandsValid() {
        return lt(temperatureCriticalDelta, temperatureEmergencyDelta)
                && lt(humidityCriticalDelta, humidityEmergencyDelta)
                && lt(luminosityCriticalDelta, luminosityEmergencyDelta);
    }

    @AssertTrue(message = "O tempo para offline deve ser maior que o tempo para instável")
    public boolean isHeartbeatValid() {
        return unstableAfterSeconds == null || offlineTimeoutSeconds == null || unstableAfterSeconds < offlineTimeoutSeconds;
    }

    private static boolean lt(Double a, Double b) {
        return a == null || b == null || a < b;
    }

    public static EnvironmentConfigDto from(EnvironmentConfig c) {
        return new EnvironmentConfigDto(
                c.getTemperatureMin(), c.getTemperatureMax(), c.getTemperatureTriggerSeconds(),
                c.getHumidityMin(), c.getHumidityMax(), c.getHumidityTriggerSeconds(),
                c.getLuminosityMax(), c.getLuminosityTriggerSeconds(),
                c.getTemperatureCriticalDelta(), c.getTemperatureEmergencyDelta(),
                c.getHumidityCriticalDelta(), c.getHumidityEmergencyDelta(),
                c.getLuminosityCriticalDelta(), c.getLuminosityEmergencyDelta(),
                c.getEscalationMinutes(), c.getClearSeconds(),
                c.isBuzzerEnabled(), c.isLedEnabled(),
                c.getUnstableAfterSeconds(), c.getOfflineTimeoutSeconds(),
                c.getUpdatedAt());
    }

    public void applyTo(EnvironmentConfig c) {
        c.setTemperatureMin(temperatureMin);
        c.setTemperatureMax(temperatureMax);
        c.setTemperatureTriggerSeconds(temperatureTriggerSeconds);
        c.setHumidityMin(humidityMin);
        c.setHumidityMax(humidityMax);
        c.setHumidityTriggerSeconds(humidityTriggerSeconds);
        c.setLuminosityMax(luminosityMax);
        c.setLuminosityTriggerSeconds(luminosityTriggerSeconds);
        c.setTemperatureCriticalDelta(temperatureCriticalDelta);
        c.setTemperatureEmergencyDelta(temperatureEmergencyDelta);
        c.setHumidityCriticalDelta(humidityCriticalDelta);
        c.setHumidityEmergencyDelta(humidityEmergencyDelta);
        c.setLuminosityCriticalDelta(luminosityCriticalDelta);
        c.setLuminosityEmergencyDelta(luminosityEmergencyDelta);
        c.setEscalationMinutes(escalationMinutes);
        c.setClearSeconds(clearSeconds);
        c.setBuzzerEnabled(buzzerEnabled);
        c.setLedEnabled(ledEnabled);
        c.setUnstableAfterSeconds(unstableAfterSeconds);
        c.setOfflineTimeoutSeconds(offlineTimeoutSeconds);
    }
}
