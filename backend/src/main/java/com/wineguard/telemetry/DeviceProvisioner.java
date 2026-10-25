package com.wineguard.telemetry;

import com.wineguard.entity.Device;

/** Provisionamento do dispositivo na plataforma IoT (IoT Agent). No-op no modo standalone. */
public interface DeviceProvisioner {

    /** Garante service group + subscription Orion->STH. @return true se a infraestrutura está pronta. */
    boolean ensureInfrastructure();

    /** @return mensagem descritiva do resultado. Lança exceção em caso de falha. */
    String provision(Device device);

    void deprovision(Device device);

    boolean isRequired();
}
