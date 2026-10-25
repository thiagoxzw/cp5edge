package com.wineguard.demo;

import com.wineguard.domain.*;
import com.wineguard.entity.Device;
import com.wineguard.entity.EnvironmentConfig;
import com.wineguard.entity.Incident;
import com.wineguard.exception.BusinessException;
import com.wineguard.monitoring.DemoState;
import com.wineguard.monitoring.DeviceRuntimeState;
import com.wineguard.monitoring.DeviceStateRegistry;
import com.wineguard.repository.IncidentRepository;
import com.wineguard.rules.TriggerRules;
import com.wineguard.service.*;
import com.wineguard.telemetry.TelemetryReading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * <h1>Modo Demo</h1>
 * Roteiro automático para o hands-on. A anomalia é injetada pelo comando {@code demo} do próprio
 * firmware (ou do dispositivo virtual), que aplica um offset gradual à leitura. Todo o resto é o
 * fluxo REAL: FIWARE -> backend detecta -> incidente -> comandos -> LED/buzzer -> normalização.
 * <p>
 * Cada passo é marcado como concluído observando o estado real do sistema (não por timer).
 */
@Service
public class DemoService {

    private static final Logger log = LoggerFactory.getLogger(DemoService.class);
    private static final Duration BASELINE = Duration.ofSeconds(4);
    private static final Duration PHASE_TIMEOUT = Duration.ofSeconds(150);

    private final DeviceService devices;
    private final DeviceStateRegistry states;
    private final CommandService commands;
    private final EnvironmentConfigService configService;
    private final IncidentRepository incidentRepository;
    private final IncidentService incidentService;
    private final EventService events;
    private final DemoState demoState;

    private volatile DemoRun run;

    public DemoService(DeviceService devices, DeviceStateRegistry states, CommandService commands,
                       EnvironmentConfigService configService, IncidentRepository incidentRepository,
                       IncidentService incidentService, EventService events, DemoState demoState) {
        this.devices = devices;
        this.states = states;
        this.commands = commands;
        this.configService = configService;
        this.incidentRepository = incidentRepository;
        this.incidentService = incidentService;
        this.events = events;
        this.demoState = demoState;
    }

    /** Dados mutáveis de uma execução (acessados sob o lock do próprio objeto). */
    private static final class DemoRun {
        Device device;
        DemoScenario scenario;
        int holdSeconds;
        DemoPhase phase = DemoPhase.BASELINE;
        Instant startedAt, phaseAt, injectedAt, anomalyAt, incidentAt, scoreDropAt, commandAt, ledAt, buzzerAt,
                normalAt, resolvedAt, scoreRecoveredAt, finishedAt;
        Integer baselineScore, minScore;
        String injectedParam;
        Long incidentId;
        String message;
    }

    public synchronized DemoStatus start(Long deviceId, DemoScenario scenario, Integer holdSeconds) {
        if (run != null && isActive(run.phase)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Já existe uma demonstração em andamento");
        }
        Device device = devices.find(deviceId);
        if (!device.isActive()) throw new BusinessException("Dispositivo desativado");
        if (!device.hasSensor(scenario.variable().sensor())) {
            throw new BusinessException("O dispositivo não possui sensor de " + scenario.variable().label().toLowerCase(Locale.ROOT));
        }
        DeviceRuntimeState state = states.get(device.getId());
        if (!state.isReachable() || state.getReading() == null) {
            throw new BusinessException(HttpStatus.CONFLICT, "O dispositivo precisa estar ONLINE para a demonstração");
        }
        boolean alreadyOpen = incidentService.openFor(device.getId()).stream()
                .anyMatch(i -> i.getVariable() == scenario.variable());
        if (alreadyOpen) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Já existe incidente aberto de " + scenario.variable().label().toLowerCase(Locale.ROOT)
                            + " neste dispositivo. Ajuste os triggers ou aguarde a normalização.");
        }

        DemoRun r = new DemoRun();
        r.device = device;
        r.scenario = scenario;
        r.holdSeconds = holdSeconds == null ? 10 : Math.max(5, Math.min(holdSeconds, 300));
        r.startedAt = Instant.now();
        r.phaseAt = r.startedAt;
        r.baselineScore = state.getScore() != null ? state.getScore().score() : null;
        r.minScore = r.baselineScore;
        r.message = "Registrando ambiente normal…";
        run = r;
        demoState.activate(device.getId());
        events.record(EventType.DEMO, EventLevel.INFO, device, "Modo Demo iniciado: " + scenario.description());
        return status();
    }

    /** Antecipa a normalização (botão "Normalizar agora"). */
    public synchronized DemoStatus recoverNow() {
        DemoRun r = run;
        if (r == null || !(r.phase == DemoPhase.INJECTING || r.phase == DemoPhase.HOLDING)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Nenhuma anomalia de demonstração ativa");
        }
        recover(r, Instant.now());
        return status();
    }

    public synchronized DemoStatus stop() {
        DemoRun r = run;
        if (r != null && isActive(r.phase)) {
            sendDemo(r, "off");
            r.phase = DemoPhase.ABORTED;
            r.finishedAt = Instant.now();
            r.message = "Demonstração interrompida pelo operador";
            demoState.clear();
            events.record(EventType.DEMO, EventLevel.INFO, r.device, "Modo Demo interrompido");
        }
        return status();
    }

    public int clearDemoData() {
        if (run != null && isActive(run.phase)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Encerre a demonstração antes de limpar os dados");
        }
        int n = incidentService.deleteResolvedDemoIncidents();
        events.record(EventType.DEMO, EventLevel.INFO, null, n + " incidentes de demonstração removidos");
        return n;
    }

    /** Máquina de estados do roteiro, avaliada a cada segundo. */
    @Scheduled(fixedDelay = 1000)
    public synchronized void tick() {
        DemoRun r = run;
        if (r == null || !isActive(r.phase)) return;
        Instant now = Instant.now();
        try {
            observe(r, now);
            switch (r.phase) {
                case BASELINE -> {
                    if (Duration.between(r.phaseAt, now).compareTo(BASELINE) >= 0) inject(r, now);
                }
                case INJECTING -> {
                    if (r.incidentAt != null && r.commandAt != null) {
                        r.phase = DemoPhase.HOLDING;
                        r.phaseAt = now;
                        r.message = "Anomalia ativa: observe o LED azul e o buzzer";
                    } else if (Duration.between(r.phaseAt, now).compareTo(PHASE_TIMEOUT) > 0) {
                        r.message = "Tempo esgotado aguardando o incidente — verifique triggers e conectividade";
                        recover(r, now);
                    }
                }
                case HOLDING -> {
                    if (Duration.between(r.phaseAt, now).toSeconds() >= r.holdSeconds) recover(r, now);
                }
                case RECOVERING -> {
                    if (r.resolvedAt != null && (r.scoreRecoveredAt != null
                            || Duration.between(r.resolvedAt, now).toSeconds() > 8)) {
                        finish(r, now, "Demonstração concluída: ambiente normalizado");
                    } else if (Duration.between(r.phaseAt, now).compareTo(PHASE_TIMEOUT) > 0) {
                        finish(r, now, "Normalização não detectada a tempo — verifique o dispositivo");
                    }
                }
                default -> { }
            }
        } catch (RuntimeException e) {
            log.warn("Falha no Modo Demo: {}", e.getMessage());
            r.message = "Erro: " + e.getMessage();
            if (r.phase == DemoPhase.BASELINE) {
                r.phase = DemoPhase.ABORTED;
                r.finishedAt = now;
                demoState.clear();
            }
        }
    }

    private void inject(DemoRun r, Instant now) {
        DeviceRuntimeState state = states.get(r.device.getId());
        EnvironmentConfig c = configService.current();
        Variable v = r.scenario.variable();
        Double current = state.getReading() != null ? state.getReading().value(v) : null;
        if (current == null) throw new BusinessException("Sem leitura atual de " + v.label());

        // Alvo: meio da faixa CRÍTICA (entre criticalDelta e emergencyDelta além do limite)
        double target = switch (v) {
            case TEMPERATURE -> c.getTemperatureMax() + (c.getTemperatureCriticalDelta() + c.getTemperatureEmergencyDelta()) / 2;
            case HUMIDITY -> c.getHumidityMin() - (c.getHumidityCriticalDelta() + c.getHumidityEmergencyDelta()) / 2;
            case LUMINOSITY -> c.getLuminosityMax() + (c.getLuminosityCriticalDelta() + c.getLuminosityEmergencyDelta()) / 2;
        };
        double offset = target - current;
        r.injectedParam = String.format(Locale.ROOT, "%s:%+.1f", v.code(), offset);
        sendDemo(r, r.injectedParam);
        r.injectedAt = now;
        r.phase = DemoPhase.INJECTING;
        r.phaseAt = now;
        r.message = String.format(Locale.ROOT, "Alterando %s para ~%.1f%s (aguarde o tempo para disparo de %ds)",
                v.label().toLowerCase(Locale.ROOT), target, v.unit(), TriggerRules.triggerSeconds(v, c));
    }

    private void recover(DemoRun r, Instant now) {
        sendDemo(r, "off");
        r.phase = DemoPhase.RECOVERING;
        r.phaseAt = now;
        if (r.message == null || !r.message.startsWith("Tempo esgotado")) {
            r.message = "Retornando ao normal…";
        }
    }

    private void finish(DemoRun r, Instant now, String message) {
        r.phase = DemoPhase.DONE;
        r.finishedAt = now;
        r.message = message;
        demoState.clear();
        events.record(EventType.DEMO, EventLevel.SUCCESS, r.device, message);
    }

    private void sendDemo(DemoRun r, String param) {
        commands.send(r.device, DeviceCommand.DEMO, param, "Modo Demo");
    }

    /** Observa o sistema real e marca os passos concluídos. */
    private void observe(DemoRun r, Instant now) {
        DeviceRuntimeState state = states.get(r.device.getId());
        Variable v = r.scenario.variable();
        Integer score = state.getScore() != null ? state.getScore().score() : null;
        if (r.baselineScore == null && score != null && r.injectedAt == null) r.baselineScore = score;
        if (score != null && r.injectedAt != null && (r.minScore == null || score < r.minScore)) r.minScore = score;

        if (r.injectedAt == null) return;

        if (r.anomalyAt == null && (state.getPending().contains(v) || r.incidentId != null)) r.anomalyAt = now;

        if (r.incidentId == null) {
            incidentService.openFor(r.device.getId()).stream()
                    .filter(i -> i.getVariable() == v && !i.getStartedAt().isBefore(r.startedAt))
                    .findFirst().ifPresent(i -> {
                        r.incidentId = i.getId();
                        r.incidentAt = i.getStartedAt();
                        if (r.anomalyAt == null) r.anomalyAt = i.getStartedAt();
                    });
        }
        if (r.scoreDropAt == null && score != null && r.baselineScore != null && score <= r.baselineScore - 5) {
            r.scoreDropAt = now;
        }
        if (r.commandAt == null && after(state.getLastAlertCommandAt(), r.injectedAt)) r.commandAt = state.getLastAlertCommandAt();

        TelemetryReading reading = state.getReading();
        if (r.ledAt == null && r.incidentId != null && reading != null) {
            Boolean led = reading.reportedLed();
            if (Boolean.TRUE.equals(led) || (led == null && after(state.getLastLedOnCommandAt(), r.injectedAt))) r.ledAt = now;
        }
        if (r.buzzerAt == null && r.incidentId != null && reading != null) {
            Set<Variable> alerts = reading.reportedAlerts();
            if ((alerts != null && alerts.contains(v)) || (alerts == null && r.commandAt != null)) r.buzzerAt = now;
        }
        if (r.phase == DemoPhase.RECOVERING && r.normalAt == null && reading != null && reading.value(v) != null
                && TriggerRules.check(v, reading.value(v), configService.current()).isEmpty()) {
            r.normalAt = now;
        }
        if (r.incidentId != null && r.resolvedAt == null) {
            Incident i = incidentRepository.findById(r.incidentId).orElse(null);
            if (i != null && i.getStatus() == IncidentStatus.RESOLVED) {
                r.resolvedAt = i.getNormalizedAt();
                if (r.normalAt == null) r.normalAt = i.getNormalizedAt();
            }
        }
        if (r.resolvedAt != null && r.scoreRecoveredAt == null && score != null && r.baselineScore != null
                && score >= r.baselineScore - 10 && (r.minScore == null || score > r.minScore)) {
            r.scoreRecoveredAt = now;
        }
    }

    public DemoStatus status() {
        DemoRun r = run;
        if (r == null) {
            return new DemoStatus(false, DemoPhase.IDLE, null, null, null, null, null, null, 0, null, null, null,
                    null, null, null, null, "Pronto para iniciar", steps(null));
        }
        DeviceRuntimeState state = states.peek(r.device.getId());
        Variable v = r.scenario.variable();
        Double value = state != null && state.getReading() != null ? state.getReading().value(v) : null;
        EnvironmentConfig c = configService.current();
        Double limit = switch (v) {
            case TEMPERATURE -> c.getTemperatureMax();
            case HUMIDITY -> c.getHumidityMin();
            case LUMINOSITY -> c.getLuminosityMax();
        };
        Integer score = state != null && state.getScore() != null ? state.getScore().score() : null;
        return new DemoStatus(isActive(r.phase), r.phase, r.device.getId(), r.device.getName(), r.scenario,
                v.attribute(), v.unit(), r.startedAt, r.holdSeconds, r.baselineScore, score, r.minScore, value, limit,
                r.injectedParam, r.incidentId, r.message, steps(r));
    }

    private List<DemoStatus.Step> steps(DemoRun r) {
        String var = r != null ? r.scenario.variable().label().toLowerCase(Locale.ROOT) : "temperatura";
        String pattern = r == null ? "" : switch (r.scenario.variable()) {
            case TEMPERATURE -> "padrão TEMPERATURA (bipe longo)";
            case HUMIDITY -> "padrão UMIDADE (bipe duplo)";
            case LUMINOSITY -> "padrão LUMINOSIDADE (bipe triplo agudo)";
        };
        List<DemoStatus.Step> s = new ArrayList<>();
        s.add(step(1, "normal", "Ambiente normal", r != null && r.baselineScore != null, r == null ? null : r.startedAt,
                r != null && r.baselineScore != null ? "Wine Score inicial " + r.baselineScore : null));
        s.add(step(2, "change", "Alteração de " + var, r != null && r.injectedAt != null, r == null ? null : r.injectedAt,
                r != null && r.injectedParam != null ? "comando demo|" + r.injectedParam : null));
        s.add(step(3, "detect", "Dashboard detecta a anomalia", r != null && r.anomalyAt != null, r == null ? null : r.anomalyAt, null));
        s.add(step(4, "incident", "Incidente criado", r != null && r.incidentAt != null, r == null ? null : r.incidentAt,
                r != null && r.incidentId != null ? "Incidente #" + r.incidentId : null));
        s.add(step(5, "scoreDrop", "Wine Score diminuindo", r != null && r.scoreDropAt != null, r == null ? null : r.scoreDropAt,
                r != null && r.minScore != null ? "mínimo " + r.minScore : null));
        s.add(step(6, "command", "Comando enviado ao ESP32", r != null && r.commandAt != null, r == null ? null : r.commandAt,
                "Backend → Orion → IoT Agent → MQTT"));
        s.add(step(7, "led", "LED azul piscando", r != null && r.ledAt != null, r == null ? null : r.ledAt, null));
        s.add(step(8, "buzzer", "Buzzer com " + (pattern.isEmpty() ? "padrão específico" : pattern),
                r != null && r.buzzerAt != null, r == null ? null : r.buzzerAt, null));
        s.add(step(9, "normalize", "Retorno ao normal", r != null && r.normalAt != null, r == null ? null : r.normalAt, null));
        s.add(step(10, "resolved", "Incidente encerrado", r != null && r.resolvedAt != null, r == null ? null : r.resolvedAt,
                r != null && r.resolvedAt != null && r.incidentAt != null
                        ? "duração " + IncidentService.formatDuration(Duration.between(r.incidentAt, r.resolvedAt)) : null));
        s.add(step(11, "scoreRecovered", "Wine Score recuperado", r != null && r.scoreRecoveredAt != null,
                r == null ? null : r.scoreRecoveredAt, null));
        return s;
    }

    private static DemoStatus.Step step(int n, String key, String label, boolean done, Instant at, String detail) {
        return new DemoStatus.Step(n, key, label, done, done ? at : null, detail);
    }

    private static boolean isActive(DemoPhase p) {
        return p == DemoPhase.BASELINE || p == DemoPhase.INJECTING || p == DemoPhase.HOLDING || p == DemoPhase.RECOVERING;
    }

    private static boolean after(Instant t, Instant ref) {
        return t != null && ref != null && !t.isBefore(ref);
    }
}
