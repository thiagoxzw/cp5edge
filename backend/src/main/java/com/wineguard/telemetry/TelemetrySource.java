package com.wineguard.telemetry;

import com.wineguard.entity.Device;

import java.util.Collection;
import java.util.Map;

/**
 * Fonte dos valores ATUAIS dos dispositivos.
 * FIWARE: Orion Context Broker (GET /v2/entities). Standalone: store em memória.
 */
public interface TelemetrySource {

    /**
     * @return leituras indexadas pelo entityId. Lança exceção se a fonte estiver indisponível
     * (o chamador NÃO deve interpretar isso como "todos offline").
     */
    Map<String, TelemetryReading> fetchLatest(Collection<Device> devices);

    String name();
}
