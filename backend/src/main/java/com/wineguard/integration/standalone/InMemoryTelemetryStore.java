package com.wineguard.integration.standalone;

import com.wineguard.domain.Variable;
import com.wineguard.entity.Device;
import com.wineguard.simulation.Measurement;
import com.wineguard.simulation.SimulatorTransport;
import com.wineguard.telemetry.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Modo STANDALONE (sem FIWARE): substitui Orion + STH-Comet por memória.
 * O histórico contém apenas leituras realmente produzidas desde que o backend subiu —
 * nada é inventado para "preencher" o passado.
 */
@Component
@ConditionalOnProperty(name = "wineguard.mode", havingValue = "standalone")
public class InMemoryTelemetryStore implements TelemetrySource, HistorySource, SimulatorTransport {

    /** ~70h de dados a cada 5s por variável. */
    private static final int MAX_POINTS_PER_SERIES = 50_000;

    private final Map<String, TelemetryReading> latest = new ConcurrentHashMap<>();
    private final Map<String, Deque<HistoryPoint>> history = new ConcurrentHashMap<>();

    @Override
    public void publish(Device device, Measurement m) {
        Instant now = Instant.now();
        TelemetryReading reading = m.toReading(device.getEntityId(), now);
        latest.put(device.getEntityId(), reading);
        for (Variable v : Variable.values()) {
            Deque<HistoryPoint> series = history.computeIfAbsent(key(device.getEntityId(), v), k -> new ConcurrentLinkedDeque<>());
            series.addLast(new HistoryPoint(now, reading.value(v)));
            while (series.size() > MAX_POINTS_PER_SERIES) series.pollFirst();
        }
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public Map<String, TelemetryReading> fetchLatest(Collection<Device> devices) {
        return Map.copyOf(latest);
    }

    @Override
    public String name() {
        return "Memória (standalone)";
    }

    @Override
    public HistorySeries fetch(Device device, Variable variable, HistoryPeriod period) {
        Instant from = Instant.now().minus(period.duration());
        List<HistoryPoint> points = history.getOrDefault(key(device.getEntityId(), variable), new ArrayDeque<>())
                .stream().filter(p -> !p.timestamp().isBefore(from)).toList();
        return new HistorySeries(new ArrayList<>(points), "Memória do backend (modo standalone, desde o boot)", "raw")
                .downsample(360);
    }

    public void forget(String entityId) {
        latest.remove(entityId);
        for (Variable v : Variable.values()) history.remove(key(entityId, v));
    }

    private static String key(String entityId, Variable v) {
        return entityId + "#" + v.name();
    }
}
