package com.wineguard.simulation;

import java.util.Locale;

/**
 * Codificação UltraLight 2.0 — o mesmo protocolo usado pelo firmware do ESP32.
 * <pre>
 * Medição (device -> IoT Agent):  t|13.4|h|66.0|l|4.0|a|none|k|off|d|off|u|1234
 * Comando (IoT Agent -> device):  wg001@ledOn|            ou  wg001@alertStop|temp
 * Resposta (device -> IoT Agent): wg001@ledOn|OK
 * </pre>
 */
public final class UltraLight {

    private UltraLight() {
    }

    public static String measurement(Measurement m) {
        return String.format(Locale.ROOT, "t|%.1f|h|%.1f|l|%.1f|a|%s|k|%s|d|%s|u|%d",
                m.temperature(), m.humidity(), m.luminosity(), m.alertState(), m.ledState(), m.demoMode(), m.uptime());
    }

    /** Comando recebido. {@code param} nunca é null (string vazia se ausente). */
    public record Command(String deviceKey, String name, String param) {
        public String ack(String result) {
            return deviceKey + "@" + name + "|" + result;
        }
    }

    public static Command parseCommand(String payload) {
        if (payload == null) throw new IllegalArgumentException("payload vazio");
        String p = payload.trim();
        int at = p.indexOf('@');
        if (at <= 0) throw new IllegalArgumentException("payload UL inválido: " + payload);
        String device = p.substring(0, at);
        String rest = p.substring(at + 1);
        int bar = rest.indexOf('|');
        String name = bar >= 0 ? rest.substring(0, bar) : rest;
        String param = bar >= 0 ? rest.substring(bar + 1) : "";
        return new Command(device, name, param.trim());
    }
}
