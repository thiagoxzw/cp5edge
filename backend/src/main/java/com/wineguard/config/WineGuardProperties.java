package com.wineguard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configuração tipada do WineGuard (prefixo {@code wineguard.*} no application.yml).
 * Nenhuma URL ou credencial fica no código: tudo é injetado por variável de ambiente.
 */
@ConfigurationProperties(prefix = "wineguard")
public record WineGuardProperties(
        Mode mode,
        List<String> corsAllowedOrigins,
        long telemetryPollIntervalMs,
        boolean seedDevices,
        int actuationResyncSeconds,
        Fiware fiware,
        Mqtt mqtt,
        Simulator simulator
) {

    public enum Mode { FIWARE, STANDALONE }

    public boolean isFiwareMode() {
        return mode == null || mode == Mode.FIWARE;
    }

    public record Fiware(
            String orionUrl,
            String sthUrl,
            String iotaUrl,
            String service,
            String servicePath,
            String apiKey,
            String entityType,
            String iotaCbrokerUrl,
            String sthNotifyUrl,
            boolean autoProvision,
            int timeoutMs
    ) {
    }

    public record Mqtt(String brokerUrl, String username, String password) {
    }

    public record Simulator(long publishIntervalMs) {
    }
}
