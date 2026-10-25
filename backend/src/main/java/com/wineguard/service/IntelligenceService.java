package com.wineguard.service;

import com.wineguard.domain.IncidentType;
import com.wineguard.domain.Variable;
import com.wineguard.dto.IntelligenceResponse;
import com.wineguard.entity.Device;
import com.wineguard.entity.Incident;
import com.wineguard.monitoring.DeviceRuntimeState;
import com.wineguard.monitoring.DeviceStateRegistry;
import com.wineguard.repository.DeviceRepository;
import com.wineguard.rules.RiskIndexCalculator;
import com.wineguard.telemetry.TelemetryReading;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * WineGuard Intelligence: monta o índice de risco de cada dispositivo a partir dos dados reais em memória
 * (última leitura vinda do Orion, instante da última leitura e desde quando cada variável está fora da
 * faixa) e dos incidentes abertos. Não consulta o histórico do STH: avalia a condição atual e sua duração.
 */
@Service
public class IntelligenceService {

    private final DeviceService devices;
    private final DeviceRepository repository;
    private final DeviceStateRegistry states;
    private final EnvironmentConfigService config;
    private final IncidentService incidents;

    public IntelligenceService(DeviceService devices, DeviceRepository repository, DeviceStateRegistry states,
                               EnvironmentConfigService config, IncidentService incidents) {
        this.devices = devices;
        this.repository = repository;
        this.states = states;
        this.config = config;
        this.incidents = incidents;
    }

    @Transactional(readOnly = true)
    public IntelligenceResponse forDevice(Long id) {
        return build(devices.find(id), Instant.now());
    }

    /** Dispositivos ativos, do maior risco para o menor (indeterminados por último). */
    @Transactional(readOnly = true)
    public List<IntelligenceResponse> all() {
        Instant now = Instant.now();
        return repository.findAll().stream()
                .filter(Device::isActive)
                .map(d -> build(d, now))
                .sorted(Comparator.comparing((IntelligenceResponse r) -> r.report().index(),
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private IntelligenceResponse build(Device d, Instant now) {
        DeviceRuntimeState state = states.peek(d.getId());
        TelemetryReading reading = state != null ? state.getReading() : null;

        Map<Variable, Double> values = new EnumMap<>(Variable.class);
        for (Variable v : Variable.values()) {
            if (d.getSensors().contains(v.sensor())) values.put(v, reading != null ? reading.value(v) : null);
        }

        Map<Variable, Instant> since = state != null ? state.violationSinceSnapshot() : new EnumMap<>(Variable.class);
        for (Incident i : incidents.openFor(d.getId())) {
            if (i.getType() == IncidentType.DEVICE_OFFLINE || i.getVariable() == null) continue;
            since.merge(i.getVariable(), i.getStartedAt(), (a, b) -> a.isBefore(b) ? a : b);
        }

        RiskIndexCalculator.Report report = RiskIndexCalculator.compute(values, config.current(), since,
                state != null ? state.getLastSeenAt() : null, now);
        return new IntelligenceResponse(d.getId(), d.getDeviceKey(), d.getName(), d.getZone().label(),
                d.isSimulated(), report);
    }
}
