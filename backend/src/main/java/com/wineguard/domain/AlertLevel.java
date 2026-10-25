package com.wineguard.domain;

/** Níveis de alerta: 🟢 NORMAL 🟡 ATTENTION 🟠 CRITICAL 🔴 EMERGENCY. */
public enum AlertLevel {
    NORMAL, ATTENTION, CRITICAL, EMERGENCY;

    public AlertLevel max(AlertLevel other) {
        return other != null && other.ordinal() > ordinal() ? other : this;
    }

    public boolean isAtLeast(AlertLevel other) {
        return ordinal() >= other.ordinal();
    }
}
