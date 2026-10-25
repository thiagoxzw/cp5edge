package com.wineguard.domain;

/** Áreas da planta da vinheria. Cada área possui (ao menos) um dispositivo. */
public enum Zone {
    PREMIUM_CELLAR("Adega Premium"),
    RESERVE_CELLAR("Adega Reserva"),
    STOCK("Estoque"),
    TASTING_ROOM("Sala de Degustação");

    private final String label;

    Zone(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
