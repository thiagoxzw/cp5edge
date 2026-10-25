package com.wineguard.monitoring;

import com.wineguard.config.WineGuardProperties;
import com.wineguard.domain.DeviceCommand;
import com.wineguard.domain.IncidentStatus;
import com.wineguard.domain.IncidentType;
import com.wineguard.domain.Variable;
import com.wineguard.entity.Device;
import com.wineguard.entity.EnvironmentConfig;
import com.wineguard.entity.Incident;
import com.wineguard.service.CommandService;
import com.wineguard.telemetry.TelemetryReading;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Comandos manuais × alertas automáticos na reconciliação da atuação. */
class AlertActuationServiceTest {

    private static final Instant T0 = Instant.parse("2026-10-25T12:00:00Z");

    private CommandService commands;
    private AlertActuationService actuation;
    private Device device;
    private EnvironmentConfig config;
    private DeviceRuntimeState state;

    @BeforeEach
    void setUp() {
        commands = mock(CommandService.class);
        WineGuardProperties props = mock(WineGuardProperties.class);
        when(props.actuationResyncSeconds()).thenReturn(20);
        actuation = new AlertActuationService(commands, props);
        device = mock(Device.class);
        when(device.getDeviceKey()).thenReturn("wg001");
        config = new EnvironmentConfig();
        config.setBuzzerEnabled(true);
        config.setLedEnabled(true);
        state = new DeviceRuntimeState(1L);
    }

    /** Leitura como o ESP32 reporta: a = alertas ativos, k = LED. */
    private static TelemetryReading reported(String alertState, String ledState) {
        return new TelemetryReading("urn:ngsi-ld:WineSensor:wg001", 20.0, 60.0, 10.0,
                alertState, ledState, "off", 100L, T0);
    }

    private static Incident activeIncident(Variable v) {
        Incident i = new Incident();
        i.setType(switch (v) {
            case TEMPERATURE -> IncidentType.TEMPERATURE_HIGH;
            case HUMIDITY -> IncidentType.HUMIDITY_LOW;
            case LUMINOSITY -> IncidentType.LUMINOSITY_HIGH;
        });
        i.setVariable(v);
        i.setStatus(IncidentStatus.ACTIVE);
        return i;
    }

    @Test
    void manualAlertIsNotUndoneByResyncWithoutIncident() {
        state.reading = reported("none", "off");
        actuation.reconcile(device, state, List.of(), config, T0);           // estado inicial: tudo desligado

        actuation.registerManual(state, DeviceCommand.ALERT_TEMP, "", T0);   // operador liga alertTemp
        state.reading = reported("temp", "off");                              // ESP32 confirma

        // Muito além do tempo de resync (20 s): antes da correção, aqui saía alertStop|all.
        actuation.reconcile(device, state, List.of(), config, T0.plusSeconds(60));
        actuation.reconcile(device, state, List.of(), config, T0.plusSeconds(120));

        verify(commands, never()).send(any(), eq(DeviceCommand.ALERT_STOP), anyString(), anyString());
        assertThat(state.getManualAlerts()).containsExactly(Variable.TEMPERATURE);
    }

    @Test
    void manualAlertExpiresAfterTtl() {
        state.reading = reported("none", "off");
        actuation.reconcile(device, state, List.of(), config, T0);
        actuation.registerManual(state, DeviceCommand.ALERT_TEMP, "", T0);
        state.reading = reported("temp", "off");

        actuation.reconcile(device, state, List.of(), config, T0.plus(AlertActuationService.MANUAL_TTL).plusSeconds(1));

        verify(commands).send(eq(device), eq(DeviceCommand.ALERT_STOP), eq("all"), anyString());
        assertThat(state.getManualAlerts()).isEmpty();
        assertThat(state.getManualUntil()).isNull();
    }

    @Test
    void manualStopClearsManualAlert() {
        state.reading = reported("none", "off");
        actuation.reconcile(device, state, List.of(), config, T0);
        actuation.registerManual(state, DeviceCommand.ALERT_TEMP, "", T0);
        actuation.registerManual(state, DeviceCommand.ALERT_STOP, "all", T0.plusSeconds(5));
        state.reading = reported("none", "off");

        actuation.reconcile(device, state, List.of(), config, T0.plusSeconds(60));

        assertThat(state.getManualAlerts()).isEmpty();
        verify(commands, never()).send(any(), eq(DeviceCommand.ALERT_TEMP), anyString(), anyString());
    }

    @Test
    void automaticIncidentStillTriggersAlertAndLed() {
        state.reading = reported("none", "off");
        actuation.reconcile(device, state, List.of(), config, T0);

        actuation.reconcile(device, state, List.of(activeIncident(Variable.HUMIDITY)), config, T0.plusSeconds(3));

        verify(commands).send(eq(device), eq(DeviceCommand.ALERT_HUM), anyString(), anyString());
        verify(commands).send(eq(device), eq(DeviceCommand.LED_ON), anyString(), anyString());
    }

    @Test
    void resolvingAutomaticIncidentKeepsManualAlertOfSameVariable() {
        state.reading = reported("none", "off");
        actuation.reconcile(device, state, List.of(), config, T0);
        actuation.registerManual(state, DeviceCommand.ALERT_TEMP, "", T0);
        state.reading = reported("temp", "on");

        // Incidente automático de temperatura abre e depois é resolvido.
        actuation.reconcile(device, state, List.of(activeIncident(Variable.TEMPERATURE)), config, T0.plusSeconds(5));
        actuation.reconcile(device, state, List.of(), config, T0.plusSeconds(30));

        verify(commands, never()).send(any(), eq(DeviceCommand.ALERT_STOP), anyString(), anyString());
        assertThat(state.getManualAlerts()).containsExactly(Variable.TEMPERATURE);
    }

    @Test
    void divergenceIsStillResyncedForAutomaticState() {
        state.reading = reported("none", "off");
        actuation.reconcile(device, state, List.of(), config, T0);
        actuation.reconcile(device, state, List.of(activeIncident(Variable.LUMINOSITY)), config, T0.plusSeconds(1));
        // ESP32 reiniciou e perdeu o alerta: reporta nada por mais que o tempo de resync.
        state.reading = reported("none", "off");

        actuation.reconcile(device, state, List.of(activeIncident(Variable.LUMINOSITY)), config, T0.plusSeconds(40));

        verify(commands).send(eq(device), eq(DeviceCommand.ALERT_STOP), eq("all"), eq("ressincronização"));
    }
}
