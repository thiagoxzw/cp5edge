package com.wineguard.service;

import com.wineguard.domain.EventLevel;
import com.wineguard.domain.EventType;
import com.wineguard.domain.ProvisioningStatus;
import com.wineguard.entity.Device;
import com.wineguard.repository.DeviceRepository;
import com.wineguard.telemetry.DeviceProvisioner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Garante a infraestrutura FIWARE (service group + subscription STH) e provisiona dispositivos
 * pendentes. Roda no boot e a cada 30s até conseguir — o backend pode subir antes do FIWARE.
 */
@Service
public class ProvisioningBootstrap {

    private static final Logger log = LoggerFactory.getLogger(ProvisioningBootstrap.class);

    private final DeviceProvisioner provisioner;
    private final DeviceRepository devices;
    private final DeviceService deviceService;
    private final EventService events;
    private volatile boolean infrastructureReady;
    private volatile String lastError;

    public ProvisioningBootstrap(DeviceProvisioner provisioner, DeviceRepository devices,
                                 DeviceService deviceService, EventService events) {
        this.provisioner = provisioner;
        this.devices = devices;
        this.deviceService = deviceService;
        this.events = events;
    }

    @Scheduled(initialDelay = 1500, fixedDelay = 30_000)
    public void run() {
        if (!provisioner.isRequired()) {
            infrastructureReady = true;
            return;
        }
        boolean revalidateAll = false;
        if (!infrastructureReady) {
            try {
                provisioner.ensureInfrastructure();
                infrastructureReady = true;
                // O banco pode dizer PROVISIONED para um FIWARE diferente do atual (outra EC2, volume
                // novo). O provisionamento é idempotente ("já existia" = mantido), então revalidamos todos.
                revalidateAll = true;
                lastError = null;
                events.record(EventType.SYSTEM, EventLevel.SUCCESS, null,
                        "FIWARE pronto: service group e subscription Orion → STH-Comet verificados");
            } catch (RuntimeException e) {
                if (lastError == null) log.warn("FIWARE ainda indisponível para provisionamento: {}", e.getMessage());
                lastError = e.getMessage();
                return;
            }
        }
        for (Device d : devices.findAll()) {
            if ((revalidateAll && d.isActive())
                    || d.getProvisioning() == ProvisioningStatus.PENDING || d.getProvisioning() == ProvisioningStatus.FAILED) {
                deviceService.provisionQuietly(d);
            }
        }
    }

    /** Força nova verificação completa (botão no frontend). */
    public void reset() {
        infrastructureReady = false;
        run();
    }

    public boolean isInfrastructureReady() { return infrastructureReady; }
    public String getLastError() { return lastError; }
}
