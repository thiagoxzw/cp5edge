package com.wineguard.rules;

import com.wineguard.domain.Variable;
import com.wineguard.entity.EnvironmentConfig;
import com.wineguard.rules.RiskIndexCalculator.DataStatus;
import com.wineguard.rules.RiskIndexCalculator.Level;
import com.wineguard.rules.RiskIndexCalculator.Report;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Configuração padrão (faixa de adega): 10–18 °C, 50–80 %, luz ≤ 30 %; emergência +5 °C, ±15 p.p., +30 p.p.;
 * escalonamento 10 min; offline após 60 s.
 */
class RiskIndexCalculatorTest {

    private static final Instant NOW = Instant.parse("2026-10-25T12:00:00Z");
    private final EnvironmentConfig config = new EnvironmentConfig();

    private static Map<Variable, Double> values(Double t, Double h, Double l) {
        Map<Variable, Double> m = new EnumMap<>(Variable.class);
        m.put(Variable.TEMPERATURE, t);
        m.put(Variable.HUMIDITY, h);
        m.put(Variable.LUMINOSITY, l);
        return m;
    }

    private Report compute(Map<Variable, Double> v, Map<Variable, Instant> since) {
        return RiskIndexCalculator.compute(v, config, since, NOW.minusSeconds(5), NOW);
    }

    @Test
    void normalValuesGiveZeroAndLowRisk() {
        Report r = compute(values(14.0, 65.0, 10.0), Map.of());
        assertThat(r.index()).isZero();
        assertThat(r.level()).isEqualTo(Level.LOW);
        assertThat(r.dataStatus()).isEqualTo(DataStatus.OK);
        assertThat(r.recommendations()).isEmpty();
        assertThat(r.factors()).allMatch(f -> f.status().equals("NORMAL"));
    }

    @Test
    void exactLimitsAreInsideTheRange() {
        Report r = compute(values(18.0, 50.0, 30.0), Map.of());
        assertThat(r.index()).isZero();
        assertThat(r.level()).isEqualTo(Level.LOW);
    }

    @Test
    void riskGrowsGraduallyWithDeviation() {
        int small = compute(values(18.5, 65.0, 10.0), Map.of()).index();   // +0,5 °C
        int medium = compute(values(20.0, 65.0, 10.0), Map.of()).index();  // +2 °C (desvio crítico)
        int large = compute(values(23.0, 65.0, 10.0), Map.of()).index();   // +5 °C (desvio de emergência)

        assertThat(small).isEqualTo(30);    // 25 + 75 × 0,7 × 0,1
        assertThat(medium).isEqualTo(46);   // 25 + 75 × 0,7 × 0,4
        assertThat(large).isEqualTo(78);    // 25 + 75 × 0,7 × 1,0
        assertThat(small).isLessThan(medium).isLessThan(large);
        assertThat(RiskIndexCalculator.levelOf(small)).isEqualTo(Level.MODERATE);
        assertThat(RiskIndexCalculator.levelOf(large)).isEqualTo(Level.CRITICAL);
    }

    @Test
    void persistenceRaisesTheRisk() {
        Report now = compute(values(20.0, 65.0, 10.0), Map.of());
        Report tenMin = compute(values(20.0, 65.0, 10.0), Map.of(Variable.TEMPERATURE, NOW.minusSeconds(600)));
        assertThat(now.index()).isEqualTo(46);
        assertThat(tenMin.index()).isEqualTo(69);                          // + 75 × 0,3 × 1,0
        assertThat(tenMin.level()).isEqualTo(Level.HIGH);
    }

    @Test
    void criticalConditionExplainsAndRecommends() {
        Report r = compute(values(25.0, 65.0, 10.0), Map.of(Variable.TEMPERATURE, NOW.minusSeconds(1800)));
        assertThat(r.index()).isEqualTo(100);
        assertThat(r.level()).isEqualTo(Level.CRITICAL);
        assertThat(r.factors()).anySatisfy(f -> {
            assertThat(f.variable()).isEqualTo("temperature");
            assertThat(f.status()).isEqualTo("ACIMA");
            assertThat(f.deviation()).isEqualTo(7.0);
            assertThat(f.minutesOutOfRange()).isEqualTo(30);
        });
        assertThat(r.recommendations()).anySatisfy(s -> assertThat(s).contains("climatização").contains("30 min"));
        assertThat(r.summary()).contains("temperatura");
    }

    @Test
    void lowHumidityIsDetectedBelowTheRange() {
        Report r = compute(values(14.0, 40.0, 10.0), Map.of());
        assertThat(r.index()).isEqualTo(60);                               // 25 + 75 × 0,7 × (10/15)
        assertThat(r.level()).isEqualTo(Level.HIGH);
        assertThat(r.factors()).anySatisfy(f -> assertThat(f.status()).isEqualTo("ABAIXO"));
        assertThat(r.recommendations()).anySatisfy(s -> assertThat(s).contains("rolhas"));
    }

    @Test
    void worstFactorDrivesTheIndex() {
        Report r = compute(values(20.0, 40.0, 10.0), Map.of());           // temp 46, umidade 60
        assertThat(r.index()).isEqualTo(60);
        assertThat(r.summary()).contains("umidade");
    }

    @Test
    void neverReceivedDataIsIndeterminateNotLow() {
        Report r = RiskIndexCalculator.compute(values(null, null, null), config, Map.of(), null, NOW);
        assertThat(r.index()).isNull();
        assertThat(r.level()).isEqualTo(Level.INDETERMINATE);
        assertThat(r.dataStatus()).isEqualTo(DataStatus.NO_DATA);
    }

    @Test
    void staleReadingIsIndeterminate() {
        Report r = RiskIndexCalculator.compute(values(25.0, 65.0, 10.0), config, Map.of(),
                NOW.minusSeconds(300), NOW);                               // 5 min > 60 s
        assertThat(r.index()).isNull();
        assertThat(r.level()).isEqualTo(Level.INDETERMINATE);
        assertThat(r.dataStatus()).isEqualTo(DataStatus.STALE);
        assertThat(r.summary()).contains("desatualizados");
        assertThat(r.factors()).hasSize(3);                                // última leitura continua visível
    }

    @Test
    void missingSensorIsReportedAndExcluded() {
        Map<Variable, Double> v = new HashMap<>(values(20.0, null, 10.0));
        Report r = compute(v, Map.of());
        assertThat(r.index()).isEqualTo(46);
        assertThat(r.factors()).anySatisfy(f -> assertThat(f.status()).isEqualTo("SEM_LEITURA"));
        assertThat(r.recommendations()).anySatisfy(s -> assertThat(s).contains("sem leitura"));
    }

    @Test
    void longDurationsAreReadable() {
        assertThat(RiskIndexCalculator.duration(45)).isEqualTo("45 min");
        assertThat(RiskIndexCalculator.duration(180)).isEqualTo("3 h");
        assertThat(RiskIndexCalculator.duration(4402)).isEqualTo("3 dias");
    }

    @Test
    void presentationPresetUsesConfiguredRanges() {
        config.setTemperatureMin(15);
        config.setTemperatureMax(30);
        config.setHumidityMin(30);
        config.setHumidityMax(85);
        config.setLuminosityMax(85);
        Report r = compute(values(28.4, 34.0, 41.8), Map.of());            // leitura real do ESP32 em sala
        assertThat(r.index()).isZero();
        assertThat(r.level()).isEqualTo(Level.LOW);
    }
}
