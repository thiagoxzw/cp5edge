package com.wineguard.telemetry;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Heartbeat: o que conta como "o dispositivo publicou algo novo". */
class TelemetryReadingTest {

    private static final Instant T0 = Instant.parse("2026-10-25T17:23:01Z");

    private static TelemetryReading reading(Long uptime, Instant timeInstant) {
        return new TelemetryReading("urn:ngsi-ld:WineSensor:wg001", 13.5, 66.0, 16.7, "temp", "on", "off", uptime, timeInstant);
    }

    @Test
    void commandUpdatesThatOnlyBumpTimeInstantAreNotANewMessage() {
        // O IoT Agent atualiza o TimeInstant ao processar comandos; o firmware parado mantém o mesmo uptime.
        assertThat(reading(35L, T0).changeMarker()).isEqualTo(reading(35L, T0.plusSeconds(20)).changeMarker());
    }

    @Test
    void newUptimeIsANewMessage() {
        assertThat(reading(35L, T0).changeMarker()).isNotEqualTo(reading(40L, T0).changeMarker());
    }

    @Test
    void withoutUptimeFallsBackToTimeInstant() {
        assertThat(reading(null, T0).changeMarker()).isNotEqualTo(reading(null, T0.plusSeconds(5)).changeMarker());
    }
}
