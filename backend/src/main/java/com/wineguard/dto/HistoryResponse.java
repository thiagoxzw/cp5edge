package com.wineguard.dto;

import java.time.Instant;
import java.util.List;

/**
 * Série histórica pronta para o gráfico, com as linhas de limite configuradas.
 *
 * @param source origem real dos dados (ex.: "STH-Comet :8666 (agregado)")
 */
public record HistoryResponse(
        Long deviceId,
        String deviceName,
        String variable,
        String label,
        String unit,
        String period,
        String source,
        String resolution,
        Limits limits,
        Stats stats,
        List<Point> points
) {

    public record Limits(Double min, Double max) {
    }

    public record Stats(Double min, Double max, Double avg, int count) {
    }

    public record Point(Instant t, double v) {
    }
}
