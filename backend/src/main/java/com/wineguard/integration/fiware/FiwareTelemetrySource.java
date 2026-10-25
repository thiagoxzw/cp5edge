package com.wineguard.integration.fiware;

import com.wineguard.config.WineGuardProperties;
import com.wineguard.entity.Device;
import com.wineguard.telemetry.TelemetryReading;
import com.wineguard.telemetry.TelemetrySource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lê os valores atuais de TODOS os dispositivos com uma única chamada ao Orion
 * (GET /v2/entities?type=WineSensor&amp;options=keyValues).
 * <p>
 * Optamos por polling em vez de subscription Orion->backend porque o backend pode rodar
 * fora da rede Docker do FIWARE (ex.: notebook + VM na nuvem), onde o Orion não alcança o backend.
 */
@Component
@ConditionalOnProperty(name = "wineguard.mode", havingValue = "fiware", matchIfMissing = true)
public class FiwareTelemetrySource implements TelemetrySource {

    private final OrionClient orion;
    private final WineGuardProperties props;

    public FiwareTelemetrySource(OrionClient orion, WineGuardProperties props) {
        this.orion = orion;
        this.props = props;
    }

    @Override
    public Map<String, TelemetryReading> fetchLatest(Collection<Device> devices) {
        List<Map<String, Object>> entities = orion.listEntities(props.fiware().entityType(), FiwareAttributes.QUERY_ATTRS);
        Map<String, TelemetryReading> result = new HashMap<>();
        for (Map<String, Object> e : entities) {
            String id = String.valueOf(e.get("id"));
            Instant time = parseInstant(e.get(FiwareAttributes.TIME_INSTANT));
            if (time == null) time = parseInstant(e.get(FiwareAttributes.DATE_MODIFIED));
            result.put(id, new TelemetryReading(
                    id,
                    toDouble(e.get(FiwareAttributes.TEMPERATURE)),
                    toDouble(e.get(FiwareAttributes.HUMIDITY)),
                    toDouble(e.get(FiwareAttributes.LUMINOSITY)),
                    toText(e.get(FiwareAttributes.ALERT_STATE)),
                    toText(e.get(FiwareAttributes.LED_STATE)),
                    toText(e.get(FiwareAttributes.DEMO_MODE)),
                    toLong(e.get(FiwareAttributes.UPTIME)),
                    time));
        }
        return result;
    }

    @Override
    public String name() {
        return "Orion Context Broker";
    }

    /** Com IOTA_AUTOCAST os números chegam como JSON number; sem ele, chegam como string. Aceitamos ambos. */
    static Double toDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s && !s.isBlank()) {
            try {
                return Double.parseDouble(s.trim().replace(',', '.'));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    static Long toLong(Object v) {
        Double d = toDouble(v);
        return d == null ? null : d.longValue();
    }

    static String toText(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() || s.equals(" ") ? null : s;
    }

    static Instant parseInstant(Object v) {
        if (!(v instanceof String s) || s.isBlank()) return null;
        try {
            return Instant.parse(s);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
