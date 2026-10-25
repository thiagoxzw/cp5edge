package com.wineguard.telemetry;

import com.wineguard.domain.DeviceCommand;
import com.wineguard.entity.Device;

/**
 * Envio de comandos ao dispositivo.
 * FIWARE: PATCH no Orion -> registro do IoT Agent -> MQTT -> ESP32.
 */
public interface CommandGateway {

    void send(Device device, DeviceCommand command, String param);
}
