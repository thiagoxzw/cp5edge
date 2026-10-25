package com.wineguard.domain;

public enum IncidentType {
    TEMPERATURE_HIGH("Temperatura alta"),
    TEMPERATURE_LOW("Temperatura baixa"),
    HUMIDITY_HIGH("Umidade alta"),
    HUMIDITY_LOW("Umidade baixa"),
    LUMINOSITY_HIGH("Luminosidade alta"),
    DEVICE_OFFLINE("Dispositivo offline");

    private final String label;

    IncidentType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static IncidentType of(Variable variable, boolean high) {
        return switch (variable) {
            case TEMPERATURE -> high ? TEMPERATURE_HIGH : TEMPERATURE_LOW;
            case HUMIDITY -> high ? HUMIDITY_HIGH : HUMIDITY_LOW;
            case LUMINOSITY -> LUMINOSITY_HIGH;
        };
    }
}
