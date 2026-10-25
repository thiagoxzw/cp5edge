package com.wineguard.rules;

import com.wineguard.domain.AlertLevel;
import com.wineguard.domain.IncidentStatus;
import com.wineguard.domain.IncidentType;
import com.wineguard.domain.Variable;
import com.wineguard.entity.EnvironmentConfig;
import com.wineguard.entity.Incident;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WineScoreCalculatorTest {

    private final EnvironmentConfig config = new EnvironmentConfig();
    private final Instant now = Instant.parse("2026-10-06T12:00:00Z");

    @Test
    void idealCellarIsExcellent() {
        WineScore s = WineScoreCalculator.compute(
                Map.of(Variable.TEMPERATURE, 14.0, Variable.HUMIDITY, 65.0, Variable.LUMINOSITY, 2.0),
                config, List.of(), now);
        assertThat(s.score()).isGreaterThanOrEqualTo(99);
        assertThat(s.classification()).isEqualTo(ScoreClass.EXCELLENT);
    }

    @Test
    void edgeOfRangeCostsComfortPoints() {
        WineScore s = WineScoreCalculator.compute(
                Map.of(Variable.TEMPERATURE, 17.5, Variable.HUMIDITY, 55.0, Variable.LUMINOSITY, 25.0),
                config, List.of(), now);
        assertThat(s.score()).isBetween(88, 95);
    }

    @Test
    void criticalTemperatureDropsScoreToAttention() {
        // 21.5°C = 3.5°C acima do máximo -> 6 + 8*3.5 = 34 pontos
        WineScore s = WineScoreCalculator.compute(
                Map.of(Variable.TEMPERATURE, 21.5, Variable.HUMIDITY, 65.0, Variable.LUMINOSITY, 3.0),
                config, List.of(), now);
        assertThat(s.score()).isBetween(60, 70);
        assertThat(s.classification()).isEqualTo(ScoreClass.ATTENTION);
    }

    @Test
    void severeMultiVariableProblemIsCritical() {
        WineScore s = WineScoreCalculator.compute(
                Map.of(Variable.TEMPERATURE, 26.0, Variable.HUMIDITY, 30.0, Variable.LUMINOSITY, 70.0),
                config, List.of(), now);
        assertThat(s.classification()).isEqualTo(ScoreClass.CRITICAL);
    }

    @Test
    void recentIncidentsAndExposureReduceScoreAndDecay() {
        Incident twoHours = incident(now.minus(Duration.ofHours(3)), now.minus(Duration.ofHours(1)), AlertLevel.EMERGENCY);
        WineScore withHistory = WineScoreCalculator.compute(Map.of(Variable.TEMPERATURE, 14.0), config, List.of(twoHours), now);
        WineScore clean = WineScoreCalculator.compute(Map.of(Variable.TEMPERATURE, 14.0), config, List.of(), now);

        assertThat(withHistory.score()).isLessThan(clean.score());
        var exposure = withHistory.components().stream().filter(c -> c.key().equals("exposure")).findFirst().orElseThrow();
        assertThat(exposure.penalty()).isEqualTo(15.0); // 120 min / 8 = 15 (teto)

        // Um dia depois a penalidade de ocorrência recente desaparece
        WineScore later = WineScoreCalculator.compute(Map.of(Variable.TEMPERATURE, 14.0), config, List.of(twoHours),
                now.plus(Duration.ofHours(24)));
        var recent = later.components().stream().filter(c -> c.key().equals("recent")).findFirst().orElseThrow();
        assertThat(recent.penalty()).isZero();
    }

    @Test
    void offlineIncidentsDoNotAffectEnvironmentScore() {
        Incident offline = incident(now.minus(Duration.ofHours(1)), null, AlertLevel.CRITICAL);
        offline.setType(IncidentType.DEVICE_OFFLINE);
        offline.setVariable(null);
        WineScore s = WineScoreCalculator.compute(Map.of(Variable.TEMPERATURE, 14.0), config, List.of(offline), now);
        assertThat(s.score()).isEqualTo(100);
    }

    @Test
    void classificationBands() {
        assertThat(ScoreClass.of(100)).isEqualTo(ScoreClass.EXCELLENT);
        assertThat(ScoreClass.of(90)).isEqualTo(ScoreClass.EXCELLENT);
        assertThat(ScoreClass.of(89)).isEqualTo(ScoreClass.GOOD);
        assertThat(ScoreClass.of(75)).isEqualTo(ScoreClass.GOOD);
        assertThat(ScoreClass.of(74)).isEqualTo(ScoreClass.ATTENTION);
        assertThat(ScoreClass.of(50)).isEqualTo(ScoreClass.ATTENTION);
        assertThat(ScoreClass.of(49)).isEqualTo(ScoreClass.CRITICAL);
    }

    private Incident incident(Instant start, Instant end, AlertLevel severity) {
        Incident i = new Incident();
        i.setType(IncidentType.TEMPERATURE_HIGH);
        i.setVariable(Variable.TEMPERATURE);
        i.setStartedAt(start);
        i.setNormalizedAt(end);
        i.setSeverity(severity);
        i.setCurrentSeverity(severity);
        i.setStatus(end == null ? IncidentStatus.ACTIVE : IncidentStatus.RESOLVED);
        return i;
    }
}
