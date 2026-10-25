package com.wineguard.service;

import com.wineguard.config.WineGuardProperties;
import com.wineguard.dto.SystemStatus;
import com.wineguard.integration.fiware.IotAgentClient;
import com.wineguard.integration.fiware.OrionClient;
import com.wineguard.integration.fiware.SthCometClient;
import com.wineguard.monitoring.MonitoringService;
import com.wineguard.simulation.SimulatorTransport;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Verifica Orion, IoT Agent e STH-Comet (resultado em cache por 10s). */
@Service
public class SystemStatusService {

    private static final Duration CACHE = Duration.ofSeconds(10);

    private final WineGuardProperties props;
    private final MonitoringService monitoring;
    private final ProvisioningBootstrap bootstrap;
    private final SimulatorTransport simulator;
    private final ObjectProvider<OrionClient> orion;
    private final ObjectProvider<IotAgentClient> iota;
    private final ObjectProvider<SthCometClient> sth;

    private volatile List<SystemStatus.Component> cachedComponents = List.of();
    private volatile Instant cachedAt = Instant.EPOCH;

    public SystemStatusService(WineGuardProperties props, MonitoringService monitoring, ProvisioningBootstrap bootstrap,
                               SimulatorTransport simulator, ObjectProvider<OrionClient> orion,
                               ObjectProvider<IotAgentClient> iota, ObjectProvider<SthCometClient> sth) {
        this.props = props;
        this.monitoring = monitoring;
        this.bootstrap = bootstrap;
        this.simulator = simulator;
        this.orion = orion;
        this.iota = iota;
        this.sth = sth;
    }

    public SystemStatus status() {
        var f = props.fiware();
        return new SystemStatus(
                props.isFiwareMode() ? "fiware" : "standalone",
                f.service(), f.servicePath(), f.entityType(),
                components(),
                monitoring.getLastCycleAt(),
                monitoring.isSourceAvailable(),
                monitoring.getSourceError(),
                bootstrap.isInfrastructureReady(),
                bootstrap.getLastError(),
                simulator.isReady());
    }

    private synchronized List<SystemStatus.Component> components() {
        if (!props.isFiwareMode()) return List.of();
        if (Duration.between(cachedAt, Instant.now()).compareTo(CACHE) < 0) return cachedComponents;
        var f = props.fiware();
        List<SystemStatus.Component> list = new ArrayList<>();
        list.add(check("Orion Context Broker", f.orionUrl(), () -> {
            Map<String, Object> v = orion.getObject().version();
            Object orionInfo = v != null ? v.get("orion") : null;
            return orionInfo instanceof Map<?, ?> m ? String.valueOf(m.get("version")) : "ok";
        }));
        list.add(check("IoT Agent UL", f.iotaUrl(), () -> {
            Map<String, Object> v = iota.getObject().about();
            return v != null ? String.valueOf(v.getOrDefault("version", "ok")) : "ok";
        }));
        list.add(check("STH-Comet", f.sthUrl(), () -> {
            Map<?, ?> v = sth.getObject().version();
            return v != null && v.get("version") != null ? String.valueOf(v.get("version")) : "ok";
        }));
        cachedComponents = list;
        cachedAt = Instant.now();
        return list;
    }

    private SystemStatus.Component check(String name, String url, Supplier<String> probe) {
        try {
            return new SystemStatus.Component(name, url, true, probe.get(), null);
        } catch (RuntimeException e) {
            return new SystemStatus.Component(name, url, false, null, e.getMessage());
        }
    }
}
