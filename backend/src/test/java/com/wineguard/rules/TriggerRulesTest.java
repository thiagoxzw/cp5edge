package com.wineguard.rules;

import com.wineguard.domain.AlertLevel;
import com.wineguard.domain.Variable;
import com.wineguard.entity.EnvironmentConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TriggerRulesTest {

    private final EnvironmentConfig config = new EnvironmentConfig(); // padrão: T 10–18, UR 50–80, luz <= 30

    @Test
    void insideRangeHasNoViolation() {
        assertThat(TriggerRules.check(Variable.TEMPERATURE, 14, config)).isEmpty();
        assertThat(TriggerRules.check(Variable.HUMIDITY, 65, config)).isEmpty();
        assertThat(TriggerRules.check(Variable.LUMINOSITY, 30, config)).isEmpty();
    }

    @Test
    void detectsHighAndLowViolations() {
        var high = TriggerRules.check(Variable.TEMPERATURE, 21.5, config).orElseThrow();
        assertThat(high.high()).isTrue();
        assertThat(high.limit()).isEqualTo(18);
        assertThat(high.deviation()).isEqualTo(3.5);

        var low = TriggerRules.check(Variable.HUMIDITY, 40, config).orElseThrow();
        assertThat(low.high()).isFalse();
        assertThat(low.limit()).isEqualTo(50);
    }

    @Test
    void luminosityOnlyHasMaximum() {
        assertThat(TriggerRules.check(Variable.LUMINOSITY, 0, config)).isEmpty();
        assertThat(TriggerRules.check(Variable.LUMINOSITY, 31, config)).isPresent();
    }

    @Test
    void severityBands() {
        assertThat(TriggerRules.severity(Variable.TEMPERATURE, 1.0, config)).isEqualTo(AlertLevel.ATTENTION);
        assertThat(TriggerRules.severity(Variable.TEMPERATURE, 3.5, config)).isEqualTo(AlertLevel.CRITICAL);
        assertThat(TriggerRules.severity(Variable.TEMPERATURE, 5.0, config)).isEqualTo(AlertLevel.EMERGENCY);
    }

    @Test
    void criticalEscalatesToEmergencyOverTime() {
        assertThat(TriggerRules.severity(Variable.TEMPERATURE, 3.0, 9, config)).isEqualTo(AlertLevel.CRITICAL);
        assertThat(TriggerRules.severity(Variable.TEMPERATURE, 3.0, 10, config)).isEqualTo(AlertLevel.EMERGENCY);
        // ATENÇÃO não escala por tempo
        assertThat(TriggerRules.severity(Variable.TEMPERATURE, 1.0, 60, config)).isEqualTo(AlertLevel.ATTENTION);
    }
}
