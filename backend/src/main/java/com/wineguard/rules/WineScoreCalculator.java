package com.wineguard.rules;

import com.wineguard.domain.AlertLevel;
import com.wineguard.domain.IncidentType;
import com.wineguard.domain.Variable;
import com.wineguard.entity.EnvironmentConfig;
import com.wineguard.entity.Incident;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * <h1>Wine Score (0–100)</h1>
 * Índice de qualidade do ambiente de armazenamento. Começa em 100 e desconta penalidades:
 *
 * <pre>
 * Score = 100 − P_temperatura − P_umidade − P_luminosidade − P_exposição − P_ocorrências
 *
 * 1) Variáveis (valor ATUAL):
 *    Dentro da faixa  -> penalidade de conforto, quadrática pela distância ao centro da faixa:
 *                        P = Pmax_conforto × (|valor − centro| / meia_faixa)²
 *                        (luminosidade: P = 4 × (valor / máximo)²)
 *    Fora da faixa    -> P = Pmax_conforto + peso × desvio, limitada a um teto.
 *
 *                      Pmax_conforto  peso por unidade fora   teto
 *    Temperatura            6            8 pts / °C           40
 *    Umidade                4            2 pts / p.p.         30
 *    Luminosidade           4            1 pt  / p.p.         25
 *
 * 2) Exposição: minutos fora da faixa nas últimas 24h (soma dos incidentes ambientais)
 *    P = minutos / 8, teto 15  (2 horas fora da faixa = −15)
 *
 * 3) Ocorrências recentes: incidentes ambientais iniciados nas últimas 24h,
 *    ponderados pela severidade máxima e com decaimento linear no tempo:
 *    P = Σ peso × (1 − idade/24h), teto 15   (ATENÇÃO=2, CRÍTICO=4, EMERGÊNCIA=6)
 *
 * Classificação: 90–100 Excelente | 75–89 Bom | 50–74 Atenção | 0–49 Crítico
 * </pre>
 * Incidentes de conectividade (offline) NÃO entram no score: ele mede o ambiente, não a rede.
 */
public final class WineScoreCalculator {

    static final double EXPOSURE_MINUTES_PER_POINT = 8.0;
    static final double EXPOSURE_CAP = 15.0;
    static final double RECENT_CAP = 15.0;

    private WineScoreCalculator() {
    }

    /**
     * @param values    valores atuais apenas dos sensores que o dispositivo possui
     * @param incidents incidentes do dispositivo que se sobrepõem às últimas 24h
     */
    public static WineScore compute(Map<Variable, Double> values, EnvironmentConfig c, List<Incident> incidents, Instant now) {
        List<WineScore.Component> components = new ArrayList<>();

        Double t = values.get(Variable.TEMPERATURE);
        if (t != null) {
            components.add(rangeComponent("temperature", "Temperatura", t, c.getTemperatureMin(), c.getTemperatureMax(),
                    6, 8, 40, "°C"));
        }
        Double h = values.get(Variable.HUMIDITY);
        if (h != null) {
            components.add(rangeComponent("humidity", "Umidade", h, c.getHumidityMin(), c.getHumidityMax(),
                    4, 2, 30, "%"));
        }
        Double l = values.get(Variable.LUMINOSITY);
        if (l != null) {
            components.add(luminosityComponent(l, c.getLuminosityMax()));
        }

        Instant since = now.minus(Duration.ofHours(24));
        List<Incident> environmental = incidents.stream().filter(i -> i.getType() != IncidentType.DEVICE_OFFLINE).toList();

        // Exposição: minutos fora da faixa dentro da janela de 24h
        double exposureMinutes = 0;
        for (Incident i : environmental) {
            Instant start = i.getStartedAt().isBefore(since) ? since : i.getStartedAt();
            Instant end = i.getNormalizedAt() != null ? i.getNormalizedAt() : now;
            if (end.isAfter(start)) exposureMinutes += Duration.between(start, end).toSeconds() / 60.0;
        }
        double exposure = Math.min(EXPOSURE_CAP, exposureMinutes / EXPOSURE_MINUTES_PER_POINT);
        components.add(new WineScore.Component("exposure", "Tempo em condição inadequada (24h)", exposure, EXPOSURE_CAP,
                String.format(Locale.ROOT, "%.0f min fora da faixa", exposureMinutes)));

        // Ocorrências recentes com decaimento
        double recent = 0;
        int count = 0;
        for (Incident i : environmental) {
            if (i.getStartedAt().isBefore(since)) continue;
            count++;
            double ageHours = Duration.between(i.getStartedAt(), now).toMinutes() / 60.0;
            recent += weight(i.getSeverity()) * Math.max(0, 1 - ageHours / 24.0);
        }
        recent = Math.min(RECENT_CAP, recent);
        components.add(new WineScore.Component("recent", "Ocorrências recentes (24h)", recent, RECENT_CAP,
                count + (count == 1 ? " incidente" : " incidentes")));

        double total = components.stream().mapToDouble(WineScore.Component::penalty).sum();
        int score = (int) Math.round(Math.max(0, Math.min(100, 100 - total)));
        return new WineScore(score, ScoreClass.of(score), components.stream().map(WineScoreCalculator::rounded).toList());
    }

    static WineScore.Component rangeComponent(String key, String label, double value, double min, double max,
                                              double comfortMax, double weight, double cap, String unit) {
        double center = (min + max) / 2;
        double half = Math.max(0.0001, (max - min) / 2);
        double penalty;
        String detail;
        if (value > max || value < min) {
            double deviation = value > max ? value - max : min - value;
            penalty = Math.min(cap, comfortMax + weight * deviation);
            detail = String.format(Locale.ROOT, "%.1f%s fora da faixa (%.1f–%.1f)", deviation, unit, min, max);
        } else {
            double ratio = Math.abs(value - center) / half;
            penalty = comfortMax * ratio * ratio;
            detail = String.format(Locale.ROOT, "dentro da faixa, %.1f%s do ideal (%.1f)", Math.abs(value - center), unit, center);
        }
        return new WineScore.Component(key, label, penalty, cap, detail);
    }

    static WineScore.Component luminosityComponent(double value, double max) {
        double cap = 25;
        double penalty;
        String detail;
        if (value > max) {
            penalty = Math.min(cap, 4 + (value - max));
            detail = String.format(Locale.ROOT, "%.1f%% acima do máximo (%.0f%%)", value - max, max);
        } else {
            double ratio = max <= 0 ? 0 : value / max;
            penalty = 4 * ratio * ratio;
            detail = String.format(Locale.ROOT, "%.0f%% do limite de exposição à luz", ratio * 100);
        }
        return new WineScore.Component("luminosity", "Luminosidade", penalty, cap, detail);
    }

    static double weight(AlertLevel severity) {
        return switch (severity) {
            case EMERGENCY -> 6;
            case CRITICAL -> 4;
            case ATTENTION -> 2;
            case NORMAL -> 0;
        };
    }

    private static WineScore.Component rounded(WineScore.Component c) {
        return new WineScore.Component(c.key(), c.label(), Math.round(c.penalty() * 10) / 10.0, c.maxPenalty(), c.detail());
    }
}
