package com.wineguard.simulation;

import com.wineguard.domain.DeviceCommand;
import com.wineguard.entity.Device;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Estado de todos os dispositivos virtuais (indexados pelo device_id, ex.: wg002). */
@Component
public class VirtualDeviceRegistry {

    private static final Logger log = LoggerFactory.getLogger(VirtualDeviceRegistry.class);

    private final Map<String, VirtualDevice> devices = new ConcurrentHashMap<>();

    public VirtualDevice getOrCreate(Device device) {
        VirtualDevice vd = devices.computeIfAbsent(device.getDeviceKey(), k -> new VirtualDevice(k, device.getZone()));
        vd.setZone(device.getZone());
        return vd;
    }

    public Optional<VirtualDevice> find(String deviceKey) {
        return Optional.ofNullable(devices.get(deviceKey));
    }

    public void remove(String deviceKey) {
        devices.remove(deviceKey);
    }

    /**
     * Executa um comando recebido (via MQTT no modo fiware, ou direto no modo standalone).
     * @return resultado enviado no cmdexe, ou empty se o dispositivo não é virtual.
     */
    public Optional<String> handleCommand(String deviceKey, String commandName, String param) {
        VirtualDevice vd = devices.get(deviceKey);
        if (vd == null) return Optional.empty();
        try {
            String result = vd.apply(DeviceCommand.fromWireName(commandName), param);
            log.info("[virtual {}] comando {}|{} -> {}", deviceKey, commandName, param, result);
            return Optional.of(result);
        } catch (IllegalArgumentException e) {
            log.warn("[virtual {}] comando inválido {}|{}: {}", deviceKey, commandName, param, e.getMessage());
            return Optional.of("ERROR " + e.getMessage());
        }
    }
}
