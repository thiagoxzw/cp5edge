package com.wineguard.integration.fiware;

import com.wineguard.config.WineGuardProperties;
import com.wineguard.domain.DeviceCommand;
import com.wineguard.entity.Device;
import com.wineguard.telemetry.CommandGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Fluxo de comando: Backend -> Orion (PATCH atributo "command") -> IoT Agent -> MQTT -> ESP32.
 * Vale tanto para o ESP32 físico/Wokwi quanto para os dispositivos virtuais (que assinam o MQTT).
 */
@Component
@ConditionalOnProperty(name = "wineguard.mode", havingValue = "fiware", matchIfMissing = true)
public class FiwareCommandGateway implements CommandGateway {

    private final OrionClient orion;
    private final WineGuardProperties props;

    public FiwareCommandGateway(OrionClient orion, WineGuardProperties props) {
        this.orion = orion;
        this.props = props;
    }

    @Override
    public void send(Device device, DeviceCommand command, String param) {
        orion.sendCommand(device.getEntityId(), props.fiware().entityType(), command.wireName(), param);
    }
}
