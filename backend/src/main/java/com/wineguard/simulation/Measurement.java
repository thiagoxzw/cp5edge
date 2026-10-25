package com.wineguard.simulation;

import com.wineguard.telemetry.TelemetryReading;

import java.time.Instant;

/** Uma medição produzida por um dispositivo virtual (mesmo conteúdo do payload do ESP32). */
public record Measurement(
        double temperature,
        double humidity,
        double luminosity,
        String alertState,
        String ledState,
        String demoMode,
        long uptime
) {

    public TelemetryReading toReading(String entityId, Instant at) {
        return new TelemetryReading(entityId, round(temperature), round(humidity), round(luminosity),
                alertState, ledState, demoMode, uptime, at);
    }

    private static double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
