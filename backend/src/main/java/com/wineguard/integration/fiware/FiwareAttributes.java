package com.wineguard.integration.fiware;

/**
 * Contrato de atributos entre firmware (UltraLight), IoT Agent, Orion e STH-Comet.
 * <pre>
 * UL object_id | atributo NGSI | tipo
 * t            | temperature   | Number
 * h            | humidity      | Number
 * l            | luminosity    | Number
 * a            | alertState    | Text    ("none" ou "temp,hum,lux")
 * k            | ledState      | Text    ("on"/"off")
 * d            | demoMode      | Text    ("off" ou "temp:+9")
 * u            | uptime        | Number  (segundos desde o boot; muda a cada envio)
 * </pre>
 * Exemplo de payload publicado em /TEF/wg001/attrs:
 * {@code t|13.4|h|66.0|l|4.0|a|none|k|off|d|off|u|1234}
 */
public final class FiwareAttributes {

    public static final String TEMPERATURE = "temperature";
    public static final String HUMIDITY = "humidity";
    public static final String LUMINOSITY = "luminosity";
    public static final String ALERT_STATE = "alertState";
    public static final String LED_STATE = "ledState";
    public static final String DEMO_MODE = "demoMode";
    public static final String UPTIME = "uptime";
    public static final String TIME_INSTANT = "TimeInstant";
    /** Atributo built-in do Orion: precisa ser pedido explicitamente em ?attrs= */
    public static final String DATE_MODIFIED = "dateModified";

    /** Lista usada no ?attrs= da consulta ao Orion. */
    public static final String QUERY_ATTRS = String.join(",",
            TEMPERATURE, HUMIDITY, LUMINOSITY, ALERT_STATE, LED_STATE, DEMO_MODE, UPTIME, TIME_INSTANT, DATE_MODIFIED);

    private FiwareAttributes() {
    }
}
