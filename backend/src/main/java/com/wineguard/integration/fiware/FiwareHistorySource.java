package com.wineguard.integration.fiware;

import com.fasterxml.jackson.databind.JsonNode;
import com.wineguard.config.WineGuardProperties;
import com.wineguard.domain.Variable;
import com.wineguard.entity.Device;
import com.wineguard.telemetry.HistoryPeriod;
import com.wineguard.telemetry.HistoryPoint;
import com.wineguard.telemetry.HistorySeries;
import com.wineguard.telemetry.HistorySource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Histórico a partir do STH-Comet (API :8666).
 * <ul>
 *   <li>Última hora: dados RAW paginados (hLimit=100, limite padrão do STH).</li>
 *   <li>6h/24h: agregação por minuto. 7d/30d: agregação por hora.</li>
 *   <li>Se a agregação vier vazia (ex.: valores não numéricos), cai para RAW paginado.</li>
 * </ul>
 * Nunca gera dados fictícios: se o STH estiver fora, a exceção sobe e o frontend mostra o erro.
 */
@Component
@ConditionalOnProperty(name = "wineguard.mode", havingValue = "fiware", matchIfMissing = true)
public class FiwareHistorySource implements HistorySource {

    private static final int PAGE_SIZE = 100;
    private static final int MAX_RAW_PAGES = 30;
    private static final int MAX_POINTS = 360;

    private final SthCometClient sth;
    private final WineGuardProperties props;

    public FiwareHistorySource(SthCometClient sth, WineGuardProperties props) {
        this.sth = sth;
        this.props = props;
    }

    @Override
    public HistorySeries fetch(Device device, Variable variable, HistoryPeriod period) {
        Instant to = Instant.now();
        Instant from = to.minus(period.duration());
        String type = props.fiware().entityType();

        if (period != HistoryPeriod.LAST_HOUR) {
            String aggrPeriod = period.duration().compareTo(Duration.ofHours(24)) <= 0 ? "minute" : "hour";
            List<HistoryPoint> points = parseAggregated(
                    sth.aggregated(type, device.getEntityId(), variable.attribute(), aggrPeriod, from, to), aggrPeriod, from, to);
            if (!points.isEmpty()) {
                return new HistorySeries(points, "STH-Comet :8666 (agregado)", aggrPeriod).downsample(MAX_POINTS);
            }
        }
        List<HistoryPoint> raw = fetchRaw(type, device.getEntityId(), variable.attribute(), from, to);
        return new HistorySeries(raw, "STH-Comet :8666 (raw)", "raw").downsample(MAX_POINTS);
    }

    private List<HistoryPoint> fetchRaw(String type, String entityId, String attr, Instant from, Instant to) {
        List<HistoryPoint> points = new ArrayList<>();
        for (int page = 0; page < MAX_RAW_PAGES; page++) {
            JsonNode values = valuesNode(sth.raw(type, entityId, attr, from, to, PAGE_SIZE, page * PAGE_SIZE));
            int count = 0;
            for (JsonNode v : values) {
                count++;
                Instant ts = parse(v.path("recvTime").asText(null));
                Double value = number(v.path("attrValue"));
                if (ts != null && value != null) points.add(new HistoryPoint(ts, value));
            }
            if (count < PAGE_SIZE) break;
        }
        points.sort(Comparator.comparing(HistoryPoint::timestamp));
        return points;
    }

    /**
     * Formato agregado do STH: cada item de "values" é um bloco com _id.origin (início do bloco)
     * e "points" com offset (minuto/hora dentro do bloco), samples e sum.
     */
    static List<HistoryPoint> parseAggregated(JsonNode body, String aggrPeriod, Instant from, Instant to) {
        List<HistoryPoint> points = new ArrayList<>();
        ChronoUnit unit = "hour".equals(aggrPeriod) ? ChronoUnit.HOURS : ChronoUnit.MINUTES;
        for (JsonNode block : valuesNode(body)) {
            Instant origin = parse(block.path("_id").path("origin").asText(null));
            if (origin == null) continue;
            for (JsonNode p : block.path("points")) {
                long samples = p.path("samples").asLong(0);
                if (samples <= 0) continue;
                Instant ts = origin.plus(p.path("offset").asLong(0), unit);
                if (ts.isBefore(from.minus(1, unit)) || ts.isAfter(to)) continue;
                points.add(new HistoryPoint(ts, p.path("sum").asDouble() / samples));
            }
        }
        points.sort(Comparator.comparing(HistoryPoint::timestamp));
        return points;
    }

    /** contextResponses[0].contextElement.attributes[0].values */
    static JsonNode valuesNode(JsonNode body) {
        if (body == null) return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
        return body.path("contextResponses").path(0).path("contextElement").path("attributes").path(0).path("values");
    }

    private static Double number(JsonNode n) {
        if (n.isNumber()) return n.asDouble();
        if (n.isTextual()) {
            try {
                return Double.parseDouble(n.asText().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Instant parse(String s) {
        if (s == null) return null;
        try {
            return Instant.parse(s);
        } catch (Exception e) {
            return null;
        }
    }
}
