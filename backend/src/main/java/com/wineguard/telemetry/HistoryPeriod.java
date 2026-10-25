package com.wineguard.telemetry;

import java.time.Duration;

/** Períodos disponíveis nos gráficos históricos. */
public enum HistoryPeriod {
    LAST_HOUR("1h", Duration.ofHours(1)),
    LAST_6_HOURS("6h", Duration.ofHours(6)),
    LAST_24_HOURS("24h", Duration.ofHours(24)),
    LAST_7_DAYS("7d", Duration.ofDays(7)),
    LAST_30_DAYS("30d", Duration.ofDays(30));

    private final String code;
    private final Duration duration;

    HistoryPeriod(String code, Duration duration) {
        this.code = code;
        this.duration = duration;
    }

    public String code() { return code; }
    public Duration duration() { return duration; }

    public static HistoryPeriod fromCode(String code) {
        for (HistoryPeriod p : values()) {
            if (p.code.equalsIgnoreCase(code) || p.name().equalsIgnoreCase(code)) return p;
        }
        throw new IllegalArgumentException("Período inválido: " + code + " (use 1h, 6h, 24h, 7d ou 30d)");
    }
}
