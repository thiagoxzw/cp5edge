package com.wineguard.telemetry;

import java.time.Instant;

public record HistoryPoint(Instant timestamp, double value) {
}
