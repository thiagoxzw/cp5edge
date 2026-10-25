package com.wineguard.domain;

/**
 * Variáveis monitoradas. {@code attribute} é o nome do atributo NGSI no Orion/STH-Comet,
 * {@code objectId} é a chave curta usada no payload UltraLight 2.0 enviado pelo ESP32.
 */
public enum Variable {
    TEMPERATURE("temperature", "t", "°C", "Temperatura", SensorType.TEMPERATURE, "temp"),
    HUMIDITY("humidity", "h", "%", "Umidade", SensorType.HUMIDITY, "hum"),
    LUMINOSITY("luminosity", "l", "%", "Luminosidade", SensorType.LUMINOSITY, "lux");

    private final String attribute;
    private final String objectId;
    private final String unit;
    private final String label;
    private final SensorType sensor;
    /** Código curto usado nos comandos (alertStop|temp) e no atributo alertState. */
    private final String code;

    Variable(String attribute, String objectId, String unit, String label, SensorType sensor, String code) {
        this.attribute = attribute;
        this.objectId = objectId;
        this.unit = unit;
        this.label = label;
        this.sensor = sensor;
        this.code = code;
    }

    public String attribute() { return attribute; }
    public String objectId() { return objectId; }
    public String unit() { return unit; }
    public String label() { return label; }
    public SensorType sensor() { return sensor; }
    public String code() { return code; }

    public static Variable fromCode(String code) {
        for (Variable v : values()) {
            if (v.code.equalsIgnoreCase(code) || v.attribute.equalsIgnoreCase(code) || v.name().equalsIgnoreCase(code)) {
                return v;
            }
        }
        throw new IllegalArgumentException("Variável desconhecida: " + code);
    }
}
