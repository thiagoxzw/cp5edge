package com.wineguard.telemetry;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Série histórica + metadados de origem (exibidos no gráfico para transparência).
 *
 * @param source     ex.: "STH-Comet :8666 (raw)"
 * @param resolution ex.: "raw", "minute", "hour"
 */
public record HistorySeries(List<HistoryPoint> points, String source, String resolution) {

    /** Reduz a série para no máximo {@code maxPoints} pontos calculando a média de cada balde de tempo. */
    public HistorySeries downsample(int maxPoints) {
        if (points.size() <= maxPoints) return this;
        Instant first = points.getFirst().timestamp();
        Instant last = points.getLast().timestamp();
        long span = Math.max(1, last.toEpochMilli() - first.toEpochMilli());
        long bucket = (long) Math.ceil((double) span / maxPoints);
        List<HistoryPoint> out = new ArrayList<>();
        long currentBucket = -1;
        double sum = 0;
        int count = 0;
        long tsSum = 0;
        for (HistoryPoint p : points) {
            long b = (p.timestamp().toEpochMilli() - first.toEpochMilli()) / bucket;
            if (b != currentBucket && count > 0) {
                out.add(new HistoryPoint(Instant.ofEpochMilli(tsSum / count), sum / count));
                sum = 0;
                count = 0;
                tsSum = 0;
            }
            currentBucket = b;
            sum += p.value();
            tsSum += p.timestamp().toEpochMilli();
            count++;
        }
        if (count > 0) out.add(new HistoryPoint(Instant.ofEpochMilli(tsSum / count), sum / count));
        return new HistorySeries(out, source, resolution + " (média por " + Math.max(1, bucket / 60000) + " min)");
    }
}
