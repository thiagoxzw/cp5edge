package com.wineguard.service;

import com.wineguard.domain.DeviceCommand;
import com.wineguard.domain.EventLevel;
import com.wineguard.domain.EventType;
import com.wineguard.entity.Device;
import com.wineguard.monitoring.DeviceStateRegistry;
import com.wineguard.telemetry.CommandGateway;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Ponto único de envio de comandos (Frontend -> Backend -> FIWARE -> ESP32).
 * Registra cada comando na linha do tempo, inclusive falhas.
 */
@Service
public class CommandService {

    private final CommandGateway gateway;
    private final EventService events;
    private final DeviceStateRegistry states;

    public CommandService(CommandGateway gateway, EventService events, DeviceStateRegistry states) {
        this.gateway = gateway;
        this.events = events;
        this.states = states;
    }

    public void send(Device device, DeviceCommand command, String param, String reason) {
        String text = command.wireName() + (param != null && !param.isBlank() ? "|" + param : "");
        try {
            gateway.send(device, command, param == null ? "" : param);
        } catch (RuntimeException e) {
            events.record(EventType.COMMAND_FAILED, EventLevel.WARNING, device,
                    "Falha ao enviar " + text + ": " + e.getMessage());
            throw e;
        }
        var state = states.get(device.getId());
        switch (command) {
            case ALERT_TEMP, ALERT_HUM, ALERT_LUX -> state.markAlertCommand(Instant.now());
            case LED_ON -> state.markLedOnCommand(Instant.now());
            default -> { }
        }
        events.record(EventType.COMMAND_SENT, EventLevel.INFO, device, "Comando " + text + " enviado (" + reason + ")");
    }
}
