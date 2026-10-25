package com.wineguard.telemetry;

import com.wineguard.domain.Variable;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Leitura mais recente de um dispositivo, como está no Orion (ou no store em memória).
 *
 * @param alertState estado de alerta REPORTADO pelo firmware ("none" ou "temp,lux")
 * @param ledState   estado do LED reportado ("on"/"off")
 * @param demoMode   offset de demonstração ativo no firmware ("off" ou "temp:+9")
 * @param uptime     contador do firmware: muda a cada envio e garante detecção do heartbeat
 * @param sourceTime TimeInstant/dateModified da entidade (relógio do servidor FIWARE)
 */
public record TelemetryReading(
        String entityId,
        Double temperature,
        Double humidity,
        Double luminosity,
        String alertState,
        String ledState,
        String demoMode,
        Long uptime,
        Instant sourceTime
) {

    public Double value(Variable variable) {
        return switch (variable) {
            case TEMPERATURE -> temperature;
            case HUMIDITY -> humidity;
            case LUMINOSITY -> luminosity;
        };
    }

    /**
     * Marcador usado pelo heartbeat: se mudou desde o último ciclo, o dispositivo publicou algo novo.
     * Usamos o relógio do backend para "último contato", evitando problemas de clock skew com a VM FIWARE.
     */
    public String changeMarker() {
        // O firmware incrementa o uptime a cada publicação: é o sinal confiável de "mensagem nova".
        // O TimeInstant sozinho não serve: o IoT Agent também o atualiza quando o backend envia comandos,
        // o que faria um dispositivo parado parecer online (e a ressincronização se repetir para sempre).
        if (uptime != null) return "u" + uptime + "|" + temperature + "|" + humidity + "|" + luminosity;
        return sourceTime + "|" + temperature + "|" + humidity + "|" + luminosity;
    }

    /** Alertas que o firmware informou estarem ativos. null = firmware não reporta. */
    public Set<Variable> reportedAlerts() {
        if (alertState == null || alertState.isBlank()) {
            return null;
        }
        Set<Variable> set = EnumSet.noneOf(Variable.class);
        for (String code : alertState.toLowerCase(Locale.ROOT).split("[,;+ ]")) {
            if (code.isBlank() || code.equals("none") || code.equals("off")) continue;
            try {
                set.add(Variable.fromCode(code.trim()));
            } catch (IllegalArgumentException ignored) {
                // código desconhecido: ignora
            }
        }
        return set;
    }

    /** LED reportado pelo firmware. null = não reporta. */
    public Boolean reportedLed() {
        if (ledState == null || ledState.isBlank()) return null;
        String s = ledState.trim().toLowerCase(Locale.ROOT);
        return s.equals("on") || s.equals("1") || s.equals("blink") || s.equals("true");
    }
}
