package com.wineguard.config;

import com.wineguard.domain.DeviceStatus;
import com.wineguard.domain.ProvisioningStatus;
import com.wineguard.domain.SensorType;
import com.wineguard.domain.Zone;
import com.wineguard.entity.Device;
import com.wineguard.repository.DeviceRepository;
import com.wineguard.service.EnvironmentConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.EnumSet;

/**
 * Dados iniciais: configuração padrão dos triggers e (opcionalmente) um dispositivo por área.
 * wg001 é o ESP32 físico/Wokwi; wg002–wg004 são virtuais (simulados pelo backend).
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final DeviceRepository devices;
    private final EnvironmentConfigService config;
    private final WineGuardProperties props;

    public DataSeeder(DeviceRepository devices, EnvironmentConfigService config, WineGuardProperties props) {
        this.devices = devices;
        this.config = config;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        config.current();
        if (!props.seedDevices() || devices.count() > 0) return;

        save("Sensor Adega Premium", "wg001", Zone.PREMIUM_CELLAR, false,
                "ESP32 físico (ou Wokwi) com DHT + LDR + buzzer + LED azul");
        save("Sensor Adega Reserva", "wg002", Zone.RESERVE_CELLAR, true,
                "Dispositivo virtual simulado pelo backend (mesmo protocolo do ESP32)");
        save("Sensor Estoque", "wg003", Zone.STOCK, true,
                "Dispositivo virtual simulado pelo backend (mesmo protocolo do ESP32)");
        save("Sensor Sala de Degustação", "wg004", Zone.TASTING_ROOM, true,
                "Dispositivo virtual simulado pelo backend (mesmo protocolo do ESP32)");
        log.info("4 dispositivos de exemplo cadastrados (SEED_DEVICES=true)");
    }

    private void save(String name, String key, Zone zone, boolean simulated, String description) {
        Device d = new Device();
        d.setName(name);
        d.setDeviceKey(key);
        d.setEntityId("urn:ngsi-ld:" + props.fiware().entityType() + ":" + key);
        d.setZone(zone);
        d.setDescription(description);
        d.setSensors(EnumSet.allOf(SensorType.class));
        d.setStatus(DeviceStatus.ACTIVE);
        d.setSimulated(simulated);
        d.setProvisioning(props.isFiwareMode() && props.fiware().autoProvision()
                ? ProvisioningStatus.PENDING : ProvisioningStatus.NOT_REQUIRED);
        devices.save(d);
    }
}
