package com.wineguard.integration.standalone;

import com.wineguard.domain.DeviceCommand;
import com.wineguard.entity.Device;
import com.wineguard.exception.BusinessException;
import com.wineguard.simulation.VirtualDeviceRegistry;
import com.wineguard.telemetry.CommandGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Modo standalone: comandos vão direto para o dispositivo virtual (não há IoT Agent). */
@Component
@ConditionalOnProperty(name = "wineguard.mode", havingValue = "standalone")
public class StandaloneCommandGateway implements CommandGateway {

    private final VirtualDeviceRegistry registry;

    public StandaloneCommandGateway(VirtualDeviceRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void send(Device device, DeviceCommand command, String param) {
        if (!device.isSimulated()) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Modo standalone: o dispositivo físico " + device.getDeviceKey() + " só recebe comandos via FIWARE");
        }
        registry.getOrCreate(device);
        registry.handleCommand(device.getDeviceKey(), command.wireName(), param);
    }
}
