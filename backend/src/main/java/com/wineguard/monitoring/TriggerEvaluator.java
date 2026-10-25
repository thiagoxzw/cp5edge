package com.wineguard.monitoring;

import com.wineguard.domain.IncidentType;
import com.wineguard.domain.Variable;
import com.wineguard.entity.Device;
import com.wineguard.entity.EnvironmentConfig;
import com.wineguard.entity.Incident;
import com.wineguard.rules.TriggerRules;
import com.wineguard.service.IncidentService;
import com.wineguard.telemetry.TelemetryReading;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Máquina de estados dos triggers, por (dispositivo, variável):
 * <pre>
 *  NORMAL ──fora da faixa──► PENDENTE ──persistiu "tempo para disparo"──► INCIDENTE ABERTO
 *    ▲                          │ voltou ao normal                              │
 *    │                          ▼                                               │ normal por clearSeconds
 *    └──────────────────────── NORMAL ◄─────────── incidente RESOLVIDO ◄────────┘
 * </pre>
 * Enquanto o incidente está aberto, novas leituras só ATUALIZAM o mesmo registro (sem duplicar alertas).
 */
@Component
public class TriggerEvaluator {

    private final IncidentService incidents;

    public TriggerEvaluator(IncidentService incidents) {
        this.incidents = incidents;
    }

    public void evaluate(Device device, DeviceRuntimeState state, TelemetryReading reading, EnvironmentConfig config,
                         List<Incident> openIncidents, Instant now, boolean demo) {
        Set<Variable> pending = EnumSet.noneOf(Variable.class);

        for (Variable variable : Variable.values()) {
            if (!device.hasSensor(variable.sensor())) continue;
            Double value = reading.value(variable);
            if (value == null) continue;

            Incident open = openIncidents.stream()
                    .filter(i -> i.getType() != IncidentType.DEVICE_OFFLINE && i.getVariable() == variable)
                    .findFirst().orElse(null);
            Optional<TriggerRules.Violation> violation = TriggerRules.check(variable, value, config);

            if (violation.isPresent()) {
                TriggerRules.Violation v = violation.get();
                state.normalSince.remove(variable);
                Instant since = state.violationSince.computeIfAbsent(variable, k -> now);

                if (open != null && open.getType() != IncidentType.of(variable, v.high())) {
                    // Inverteu o lado (ex.: alta -> baixa): encerra um e abre o outro
                    incidents.resolve(open, value, now);
                    open = null;
                }
                if (open != null) {
                    incidents.update(open, v, config, now);
                } else if (Duration.between(since, now).toSeconds() >= TriggerRules.triggerSeconds(variable, config)) {
                    incidents.open(device, v, config, now, demo);
                } else {
                    pending.add(variable);
                }
            } else {
                state.violationSince.remove(variable);
                if (open != null) {
                    Instant normalSince = state.normalSince.computeIfAbsent(variable, k -> now);
                    if (Duration.between(normalSince, now).toSeconds() >= config.getClearSeconds()) {
                        incidents.resolve(open, value, now);
                        state.normalSince.remove(variable);
                    }
                }
            }
        }
        state.pending = pending;
    }

    /** Dispositivo offline: zera contadores (não sabemos o que aconteceu no período). */
    public void reset(DeviceRuntimeState state) {
        state.violationSince.clear();
        state.normalSince.clear();
        state.pending = EnumSet.noneOf(Variable.class);
    }
}
