package com.wineguard.rules;

import com.wineguard.domain.AlertLevel;
import com.wineguard.domain.Variable;
import com.wineguard.entity.EnvironmentConfig;

import java.util.Optional;

/**
 * Regras de trigger (funções puras, sem estado — fáceis de testar).
 *
 * <h2>Violação</h2>
 * Temperatura e umidade: fora de [mínimo, máximo]. Luminosidade: acima do máximo.
 *
 * <h2>Severidade (derivada do desvio além do limite)</h2>
 * <pre>
 *   desvio &lt; criticalDelta                  -> ATTENTION  🟡
 *   criticalDelta &lt;= desvio &lt; emergencyDelta -> CRITICAL   🟠
 *   desvio &gt;= emergencyDelta                -> EMERGENCY  🔴
 *   CRITICAL há mais de escalationMinutes     -> EMERGENCY  🔴 (escalonamento por tempo)
 * </pre>
 * Valores padrão: temperatura 2/5 °C, umidade 5/15 pp, luminosidade 10/30 pp.
 */
public final class TriggerRules {

    private TriggerRules() {
    }

    /** @param high true se acima do máximo, false se abaixo do mínimo */
    public record Violation(Variable variable, boolean high, double value, double limit) {
        public double deviation() {
            return Math.abs(value - limit);
        }
    }

    public static Optional<Violation> check(Variable variable, double value, EnvironmentConfig c) {
        return switch (variable) {
            case TEMPERATURE -> range(variable, value, c.getTemperatureMin(), c.getTemperatureMax());
            case HUMIDITY -> range(variable, value, c.getHumidityMin(), c.getHumidityMax());
            case LUMINOSITY -> value > c.getLuminosityMax()
                    ? Optional.of(new Violation(variable, true, value, c.getLuminosityMax()))
                    : Optional.empty();
        };
    }

    private static Optional<Violation> range(Variable v, double value, double min, double max) {
        if (value > max) return Optional.of(new Violation(v, true, value, max));
        if (value < min) return Optional.of(new Violation(v, false, value, min));
        return Optional.empty();
    }

    public static AlertLevel severity(Variable variable, double deviation, EnvironmentConfig c) {
        double critical = switch (variable) {
            case TEMPERATURE -> c.getTemperatureCriticalDelta();
            case HUMIDITY -> c.getHumidityCriticalDelta();
            case LUMINOSITY -> c.getLuminosityCriticalDelta();
        };
        double emergency = switch (variable) {
            case TEMPERATURE -> c.getTemperatureEmergencyDelta();
            case HUMIDITY -> c.getHumidityEmergencyDelta();
            case LUMINOSITY -> c.getLuminosityEmergencyDelta();
        };
        if (deviation >= emergency) return AlertLevel.EMERGENCY;
        if (deviation >= critical) return AlertLevel.CRITICAL;
        return AlertLevel.ATTENTION;
    }

    /** Severidade considerando também o escalonamento por tempo. */
    public static AlertLevel severity(Variable variable, double deviation, long minutesActive, EnvironmentConfig c) {
        AlertLevel level = severity(variable, deviation, c);
        if (level == AlertLevel.CRITICAL && minutesActive >= c.getEscalationMinutes()) {
            return AlertLevel.EMERGENCY;
        }
        return level;
    }

    /** "Tempo para disparo": a violação precisa persistir por este tempo antes de abrir incidente. */
    public static int triggerSeconds(Variable variable, EnvironmentConfig c) {
        return switch (variable) {
            case TEMPERATURE -> c.getTemperatureTriggerSeconds();
            case HUMIDITY -> c.getHumidityTriggerSeconds();
            case LUMINOSITY -> c.getLuminosityTriggerSeconds();
        };
    }
}
