package com.wineguard.simulation;

import com.wineguard.domain.DeviceCommand;
import com.wineguard.domain.Variable;
import com.wineguard.domain.Zone;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Gêmeo de software do firmware do ESP32: gera leituras realistas (passeio aleatório em torno
 * do perfil da área) e interpreta EXATAMENTE os mesmos comandos que o firmware.
 * Útil para demonstrar várias áreas da vinheria tendo apenas um ESP32 físico.
 */
public class VirtualDevice {

    /** Perfil "saudável" de cada área (valores típicos de adega). */
    private record Profile(double temperature, double humidity, double luminosity) {
    }

    private static final Map<Zone, Profile> PROFILES = Map.of(
            Zone.PREMIUM_CELLAR, new Profile(13.0, 68.0, 4.0),
            Zone.RESERVE_CELLAR, new Profile(14.0, 65.0, 7.0),
            Zone.STOCK, new Profile(15.0, 61.0, 12.0),
            Zone.TASTING_ROOM, new Profile(16.0, 58.0, 20.0));

    private final String deviceKey;
    private final Random random = new Random();
    private Profile profile;

    private double temperature;
    private double humidity;
    private double luminosity;
    private long uptime;

    private final EnumSet<Variable> activeAlerts = EnumSet.noneOf(Variable.class);
    private boolean ledBlinking;
    private boolean paused;

    /** Offset de demonstração desejado e aplicado (rampa gradual, como no firmware). */
    private final EnumMap<Variable, Double> demoTarget = new EnumMap<>(Variable.class);
    private final EnumMap<Variable, Double> demoApplied = new EnumMap<>(Variable.class);

    public VirtualDevice(String deviceKey, Zone zone) {
        this.deviceKey = deviceKey;
        setZone(zone);
        this.temperature = profile.temperature();
        this.humidity = profile.humidity();
        this.luminosity = profile.luminosity();
    }

    public synchronized void setZone(Zone zone) {
        this.profile = PROFILES.getOrDefault(zone, PROFILES.get(Zone.PREMIUM_CELLAR));
    }

    /** Avança a simulação um passo e devolve a medição a publicar. */
    public synchronized Measurement step(double elapsedSeconds) {
        uptime += Math.max(1, Math.round(elapsedSeconds));
        temperature = walk(temperature, profile.temperature(), 0.06);
        humidity = walk(humidity, profile.humidity(), 0.35);
        luminosity = Math.max(0, walk(luminosity, profile.luminosity(), 0.5));

        for (Variable v : Variable.values()) {
            double target = demoTarget.getOrDefault(v, 0.0);
            double applied = demoApplied.getOrDefault(v, 0.0);
            double step = Math.max(Math.abs(target - applied) / 3.0, 0.5);
            if (Math.abs(target - applied) <= step) applied = target;
            else applied += Math.signum(target - applied) * step;
            demoApplied.put(v, applied);
        }

        return new Measurement(
                temperature + demoApplied.get(Variable.TEMPERATURE),
                clamp(humidity + demoApplied.get(Variable.HUMIDITY), 0, 100),
                clamp(luminosity + demoApplied.get(Variable.LUMINOSITY), 0, 100),
                alertState(),
                ledBlinking ? "on" : "off",
                demoMode(),
                uptime);
    }

    /** Interpreta um comando (mesma semântica do firmware). @return resultado para o cmdexe. */
    public synchronized String apply(DeviceCommand command, String param) {
        switch (command) {
            case LED_ON -> ledBlinking = true;
            case LED_OFF -> ledBlinking = false;
            case ALERT_TEMP -> activeAlerts.add(Variable.TEMPERATURE);
            case ALERT_HUM -> activeAlerts.add(Variable.HUMIDITY);
            case ALERT_LUX -> activeAlerts.add(Variable.LUMINOSITY);
            case ALERT_STOP -> {
                if (param == null || param.isBlank() || param.equalsIgnoreCase("all")) activeAlerts.clear();
                else activeAlerts.remove(Variable.fromCode(param));
            }
            case DEMO -> applyDemo(param);
        }
        return "OK";
    }

    private void applyDemo(String param) {
        if (param == null || param.isBlank() || param.equalsIgnoreCase("off")) {
            demoTarget.clear();
            return;
        }
        String[] parts = param.split(":");
        if (parts.length != 2) throw new IllegalArgumentException("parâmetro demo inválido: " + param);
        demoTarget.put(Variable.fromCode(parts[0]), Double.parseDouble(parts[1].replace("+", "")));
    }

    private double walk(double current, double base, double sigma) {
        return current + (base - current) * 0.15 + random.nextGaussian() * sigma;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private String alertState() {
        if (activeAlerts.isEmpty()) return "none";
        return activeAlerts.stream().map(Variable::code).collect(Collectors.joining(","));
    }

    private String demoMode() {
        if (demoTarget.isEmpty()) return "off";
        return demoTarget.entrySet().stream()
                .map(e -> e.getKey().code() + ":" + (e.getValue() >= 0 ? "+" : "") + e.getValue().intValue())
                .collect(Collectors.joining(","));
    }

    public synchronized boolean isPaused() { return paused; }
    public synchronized void setPaused(boolean paused) { this.paused = paused; }
    public String getDeviceKey() { return deviceKey; }
}
