package com.wineguard.domain;

/**
 * Comandos aceitos pelo firmware. O nome é exatamente o nome do atributo
 * "command" provisionado no IoT Agent e o texto recebido pelo ESP32 via MQTT
 * no formato UltraLight: {@code <deviceId>@<comando>|<parâmetro>}.
 */
public enum DeviceCommand {
    LED_ON("ledOn"),
    LED_OFF("ledOff"),
    ALERT_TEMP("alertTemp"),
    ALERT_HUM("alertHum"),
    ALERT_LUX("alertLux"),
    /** parâmetro: temp | hum | lux | all */
    ALERT_STOP("alertStop"),
    /** Modo demonstração: parâmetro "temp:+9", "hum:-22", "lux:+45" ou "off". */
    DEMO("demo");

    private final String wireName;

    DeviceCommand(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static DeviceCommand alertFor(Variable variable) {
        return switch (variable) {
            case TEMPERATURE -> ALERT_TEMP;
            case HUMIDITY -> ALERT_HUM;
            case LUMINOSITY -> ALERT_LUX;
        };
    }

    public static DeviceCommand fromWireName(String name) {
        for (DeviceCommand c : values()) {
            if (c.wireName.equalsIgnoreCase(name) || c.name().equalsIgnoreCase(name)) {
                return c;
            }
        }
        throw new IllegalArgumentException("Comando desconhecido: " + name);
    }
}
