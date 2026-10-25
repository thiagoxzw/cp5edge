package com.wineguard;

import com.wineguard.domain.*;
import com.wineguard.dto.EnvironmentConfigDto;
import com.wineguard.entity.Device;
import com.wineguard.entity.Incident;
import com.wineguard.monitoring.DeviceStateRegistry;
import com.wineguard.monitoring.MonitoringService;
import com.wineguard.repository.DeviceRepository;
import com.wineguard.service.CommandService;
import com.wineguard.service.DashboardService;
import com.wineguard.service.EnvironmentConfigService;
import com.wineguard.service.IncidentService;
import com.wineguard.simulation.VirtualDeviceSimulator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fluxo completo sem FIWARE (modo standalone), dirigido manualmente:
 * leitura normal -> anomalia -> incidente único -> comandos LED/buzzer -> normalização -> encerramento.
 */
@SpringBootTest(properties = {
        "wineguard.mode=standalone",
        "wineguard.scheduling-enabled=false",
        "spring.datasource.url=jdbc:h2:mem:wineguard-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
class StandaloneFlowIntegrationTest {

    @Autowired DeviceRepository devices;
    @Autowired VirtualDeviceSimulator simulator;
    @Autowired MonitoringService monitoring;
    @Autowired DeviceStateRegistry states;
    @Autowired IncidentService incidents;
    @Autowired CommandService commands;
    @Autowired EnvironmentConfigService config;
    @Autowired DashboardService dashboard;

    @Test
    void fullAlertLifecycle() {
        // Disparo e encerramento imediatos para o teste
        EnvironmentConfigDto c = EnvironmentConfigDto.from(config.current());
        config.update(new EnvironmentConfigDto(c.temperatureMin(), c.temperatureMax(), 0, c.humidityMin(), c.humidityMax(), 0,
                c.luminosityMax(), 0, c.temperatureCriticalDelta(), c.temperatureEmergencyDelta(), c.humidityCriticalDelta(),
                c.humidityEmergencyDelta(), c.luminosityCriticalDelta(), c.luminosityEmergencyDelta(), c.escalationMinutes(),
                0, true, true, c.unstableAfterSeconds(), c.offlineTimeoutSeconds(), null));

        Device virtual = devices.findByDeviceKey("wg002").orElseThrow();
        Device physical = devices.findByDeviceKey("wg001").orElseThrow();

        // 1) Ambiente normal
        cycle();
        assertThat(states.get(virtual.getId()).getConnectivity()).isEqualTo(Connectivity.ONLINE);
        assertThat(states.get(virtual.getId()).getScore().score()).isGreaterThanOrEqualTo(90);
        assertThat(incidents.openFor(virtual.getId())).isEmpty();
        // ESP32 físico não existe no modo standalone -> nunca visto
        assertThat(states.get(physical.getId()).getConnectivity()).isEqualTo(Connectivity.NEVER_SEEN);

        // 2) Anomalia (mesmo comando "demo" que o firmware entende)
        commands.send(virtual, DeviceCommand.DEMO, "temp:+9", "teste");
        for (int i = 0; i < 6; i++) cycle();

        List<Incident> open = incidents.openFor(virtual.getId());
        assertThat(open).hasSize(1); // ocorrência ÚNICA, mesmo após várias leituras
        Incident incident = open.getFirst();
        assertThat(incident.getType()).isEqualTo(IncidentType.TEMPERATURE_HIGH);
        assertThat(incident.getSeverity()).isEqualTo(AlertLevel.CRITICAL);

        // 3) Atuação: o dispositivo virtual reporta LED e buzzer de temperatura ativos
        cycle();
        var reading = states.get(virtual.getId()).getReading();
        assertThat(reading.reportedLed()).isTrue();
        assertThat(reading.reportedAlerts()).containsExactly(Variable.TEMPERATURE);
        assertThat(states.get(virtual.getId()).getScore().score()).isLessThan(75);
        assertThat(dashboard.summary().zones().stream()
                .filter(z -> z.zone() == Zone.RESERVE_CELLAR).findFirst().orElseThrow().status()).isEqualTo("critical");

        // 4) Normalização
        commands.send(virtual, DeviceCommand.DEMO, "off", "teste");
        for (int i = 0; i < 8; i++) cycle();
        assertThat(incidents.openFor(virtual.getId())).isEmpty();
        cycle();
        var after = states.get(virtual.getId()).getReading();
        assertThat(after.reportedLed()).isFalse();
        assertThat(after.reportedAlerts()).isEmpty();
        assertThat(incidents.get(incident.getId()).status()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(incidents.get(incident.getId()).normalizedAt()).isNotNull();
    }

    private void cycle() {
        simulator.tick();
        monitoring.runCycle(Instant.now());
    }
}
