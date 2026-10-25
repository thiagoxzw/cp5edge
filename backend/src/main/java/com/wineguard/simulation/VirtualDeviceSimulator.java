package com.wineguard.simulation;

import com.wineguard.config.WineGuardProperties;
import com.wineguard.domain.DeviceStatus;
import com.wineguard.entity.Device;
import com.wineguard.repository.DeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Publica periodicamente as medições dos dispositivos virtuais ativos,
 * exatamente como o firmware faria (UltraLight via MQTT no modo fiware).
 */
@Component
public class VirtualDeviceSimulator {

    private static final Logger log = LoggerFactory.getLogger(VirtualDeviceSimulator.class);

    private final DeviceRepository devices;
    private final VirtualDeviceRegistry registry;
    private final SimulatorTransport transport;
    private final double intervalSeconds;

    public VirtualDeviceSimulator(DeviceRepository devices, VirtualDeviceRegistry registry,
                                  SimulatorTransport transport, WineGuardProperties props) {
        this.devices = devices;
        this.registry = registry;
        this.transport = transport;
        this.intervalSeconds = props.simulator().publishIntervalMs() / 1000.0;
    }

    @Scheduled(initialDelay = 3000, fixedRateString = "${wineguard.simulator.publish-interval-ms}")
    public void tick() {
        for (Device device : devices.findByStatus(DeviceStatus.ACTIVE)) {
            if (!device.isSimulated()) continue;
            VirtualDevice vd = registry.getOrCreate(device);
            if (vd.isPaused()) continue; // simulação de perda de conexão
            Measurement m = vd.step(intervalSeconds);
            if (!transport.isReady()) continue;
            try {
                transport.publish(device, m);
            } catch (Exception e) {
                log.debug("Falha ao publicar medição virtual de {}: {}", device.getDeviceKey(), e.getMessage());
            }
        }
    }
}
