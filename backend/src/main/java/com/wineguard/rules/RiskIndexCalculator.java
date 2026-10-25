package com.wineguard.rules;

import com.wineguard.domain.Variable;
import com.wineguard.entity.EnvironmentConfig;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * <h1>WineGuard Intelligence — índice de risco de conservação (0–100)</h1>
 * Índice operacional e explicável do risco de o ambiente estar fora das condições configuradas. Não é
 * uma medida científica da qualidade do vinho nem afirma que houve dano: só traduz os triggers do
 * projeto (Configuração) em uma prioridade de atenção.
 *
 * <pre>
 * Para cada variável com leitura:
 *   dentro da faixa configurada (limites inclusive) -> risco = 0
 *   fora da faixa:
 *     severidade = min(1, desvio / desvio_de_emergência)       desvio em °C ou p.p. além do limite
 *     duração    = min(1, minutos_fora / minutos_de_escalonamento)
 *     risco      = 25 + 75 × (0,7 × severidade + 0,3 × duração)
 *
 * Índice = maior risco entre as variáveis (o pior fator manda; não há peso arbitrário entre variáveis).
 * Classificação: 0–24 baixo · 25–49 moderado · 50–74 alto · 75–100 crítico.
 * </pre>
 * Todos os limites vêm da Configuração (faixas, desvios de emergência, escalonamento). Sem leitura, ou com
 * leitura mais antiga que o timeout de offline, o índice é INDETERMINADO — nunca "baixo".
 */
public final class RiskIndexCalculator {

    static final double OUT_OF_RANGE_FLOOR = 25.0;
    static final double SEVERITY_WEIGHT = 0.7;
    static final double DURATION_WEIGHT = 0.3;

    public static final String DISCLAIMER = "Índice operacional baseado nos limites configurados; "
            + "não certifica a qualidade do vinho nem indica dano.";

    private RiskIndexCalculator() {
    }

    public enum Level { LOW, MODERATE, HIGH, CRITICAL, INDETERMINATE }

    public enum DataStatus { OK, STALE, NO_DATA }

    public record Factor(String variable, String label, Double value, String unit, String range, String status,
                         double deviation, long minutesOutOfRange, int risk, String explanation) {
    }

    public record Report(Integer index, Level level, DataStatus dataStatus, Instant lastReadingAt,
                         List<Factor> factors, List<String> recommendations, String summary, String disclaimer) {
    }

    /**
     * @param values          leitura atual por variável (só os sensores que o dispositivo tem; valor null = sem leitura)
     * @param outOfRangeSince desde quando cada variável está fora da faixa (ausente = acabou de sair ou está normal)
     * @param lastReadingAt   instante da última leitura recebida (null = nunca recebeu)
     */
    public static Report compute(Map<Variable, Double> values, EnvironmentConfig c,
                                 Map<Variable, Instant> outOfRangeSince, Instant lastReadingAt, Instant now) {
        if (lastReadingAt == null || values.values().stream().allMatch(v -> v == null)) {
            return indeterminate(DataStatus.NO_DATA, lastReadingAt, List.of(),
                    "Sem leituras do dispositivo: não é possível avaliar o risco.");
        }
        long ageSeconds = Duration.between(lastReadingAt, now).toSeconds();
        List<Factor> factors = new ArrayList<>();
        for (Variable v : Variable.values()) {
            if (values.containsKey(v)) factors.add(factor(v, values.get(v), c, outOfRangeSince.get(v), now));
        }
        if (ageSeconds > c.getOfflineTimeoutSeconds()) {
            return indeterminate(DataStatus.STALE, lastReadingAt, factors, String.format(Locale.ROOT,
                    "Última leitura há %d min: dados desatualizados, risco não avaliado.", Math.max(1, ageSeconds / 60)));
        }

        int index = factors.stream().mapToInt(Factor::risk).max().orElse(0);
        Level level = levelOf(index);
        List<String> recommendations = new ArrayList<>();
        for (Factor f : factors) {
            String r = recommendation(f);
            if (r != null) recommendations.add(r);
        }
        if (factors.stream().anyMatch(f -> f.value() == null)) {
            recommendations.add("Há sensor sem leitura: verifique a ligação do sensor no dispositivo.");
        }
        return new Report(index, level, DataStatus.OK, lastReadingAt, factors, recommendations,
                summary(index, level, factors), DISCLAIMER);
    }

    static Level levelOf(int index) {
        if (index >= 75) return Level.CRITICAL;
        if (index >= 50) return Level.HIGH;
        if (index >= 25) return Level.MODERATE;
        return Level.LOW;
    }

    private static Factor factor(Variable v, Double value, EnvironmentConfig c, Instant since, Instant now) {
        Double min = switch (v) {
            case TEMPERATURE -> c.getTemperatureMin();
            case HUMIDITY -> c.getHumidityMin();
            case LUMINOSITY -> null;   // luz só tem máximo
        };
        double max = switch (v) {
            case TEMPERATURE -> c.getTemperatureMax();
            case HUMIDITY -> c.getHumidityMax();
            case LUMINOSITY -> c.getLuminosityMax();
        };
        double emergency = switch (v) {
            case TEMPERATURE -> c.getTemperatureEmergencyDelta();
            case HUMIDITY -> c.getHumidityEmergencyDelta();
            case LUMINOSITY -> c.getLuminosityEmergencyDelta();
        };
        String unit = v.unit();
        String range = min == null ? fmt("≤ %.0f %s", max, unit) : fmt("%.0f–%.0f %s", min, max, unit);

        if (value == null) {
            return new Factor(v.attribute(), v.label(), null, unit, range, "SEM_LEITURA", 0, 0, 0,
                    v.label() + ": sem leitura, não entra no índice.");
        }
        double deviation = 0;
        String direction = null;
        if (min != null && value < min) {
            deviation = min - value;
            direction = "abaixo";
        } else if (value > max) {
            deviation = value - max;
            direction = "acima";
        }
        if (deviation <= 0) {
            return new Factor(v.attribute(), v.label(), value, unit, range, "NORMAL", 0, 0, 0,
                    fmt("%s %.1f %s dentro da faixa (%s).", v.label(), value, unit, range));
        }
        long minutes = since == null ? 0 : Math.max(0, Duration.between(since, now).toMinutes());
        double severity = emergency > 0 ? Math.min(1.0, deviation / emergency) : 1.0;
        double duration = c.getEscalationMinutes() > 0 ? Math.min(1.0, minutes / (double) c.getEscalationMinutes()) : 1.0;
        int risk = (int) Math.round(OUT_OF_RANGE_FLOOR
                + (100 - OUT_OF_RANGE_FLOOR) * (SEVERITY_WEIGHT * severity + DURATION_WEIGHT * duration));
        String explanation = fmt("%s %.1f %s está %.1f %s %s do limite (faixa %s), há %s.",
                v.label(), value, unit, deviation, unit, direction, range, duration(minutes));
        return new Factor(v.attribute(), v.label(), value, unit, range,
                direction.equals("acima") ? "ACIMA" : "ABAIXO", round1(deviation), minutes, Math.min(100, risk), explanation);
    }

    private static String recommendation(Factor f) {
        String base = switch (f.status()) {
            case "ACIMA" -> switch (f.variable()) {
                case "temperature" -> "Temperatura acima da faixa: verifique a climatização/refrigeração e se a porta está fechada.";
                case "humidity" -> "Umidade acima da faixa (favorece mofo): verifique ventilação e desumidificação.";
                default -> "Luz acima do limite: reduza a exposição (apague luzes, feche cortinas, proteja as garrafas).";
            };
            case "ABAIXO" -> switch (f.variable()) {
                case "temperature" -> "Temperatura abaixo da faixa: verifique se a climatização está regulada fria demais.";
                default -> "Umidade abaixo da faixa (resseca rolhas): verifique umidificação e vedação do ambiente.";
            };
            default -> null;
        };
        if (base != null && f.minutesOutOfRange() >= 10) {
            base += " Condição persiste há " + duration(f.minutesOutOfRange()) + ": priorize este ambiente.";
        }
        return base;
    }

    private static String summary(int index, Level level, List<Factor> factors) {
        if (index == 0) return "Todas as variáveis com leitura estão dentro da faixa configurada.";
        Factor worst = factors.stream().filter(f -> f.risk() == index).findFirst().orElse(null);
        String label = switch (level) {
            case LOW -> "Risco baixo";
            case MODERATE -> "Risco moderado";
            case HIGH -> "Risco alto";
            case CRITICAL -> "Risco crítico";
            case INDETERMINATE -> "Risco indeterminado";
        };
        return worst == null ? label : label + ", principal fator: " + worst.label().toLowerCase(Locale.ROOT) + ".";
    }

    private static Report indeterminate(DataStatus status, Instant lastReadingAt, List<Factor> factors, String summary) {
        List<String> recs = List.of("Verifique a conexão do dispositivo (Wi-Fi, broker MQTT) e se ele está ligado.");
        return new Report(null, Level.INDETERMINATE, status, lastReadingAt, factors, recs, summary, DISCLAIMER);
    }

    /** 45 min · 3 h · 2 dias (para leitura humana). */
    static String duration(long minutes) {
        if (minutes < 120) return minutes + " min";
        if (minutes < 2880) return (minutes / 60) + " h";
        return (minutes / 1440) + " dias";
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    private static String fmt(String f, Object... args) {
        return String.format(Locale.ROOT, f, args);
    }
}
