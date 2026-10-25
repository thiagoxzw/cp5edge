package com.wineguard.monitoring;

import com.wineguard.config.WineGuardProperties;
import com.wineguard.domain.DeviceCommand;
import com.wineguard.domain.IncidentStatus;
import com.wineguard.domain.IncidentType;
import com.wineguard.domain.Variable;
import com.wineguard.entity.Device;
import com.wineguard.entity.EnvironmentConfig;
import com.wineguard.entity.Incident;
import com.wineguard.service.CommandService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Atuação remota: mantém LED e buzzer do ESP32 coerentes com os incidentes abertos.
 *
 * <h2>Estado desejado</h2>
 * <ul>
 *   <li>Buzzer: um padrão por variável com incidente ATIVO (reconhecido = silenciado), se buzzer habilitado.</li>
 *   <li>LED azul piscando: enquanto existir QUALQUER incidente ambiental aberto, se LED habilitado.</li>
 *   <li>Mais os comandos manuais do dashboard ({@link #registerManual}): valem até um alertStop/ledOff
 *       manual ou até {@link #MANUAL_TTL}, independentemente dos incidentes. Encerrar um incidente
 *       automático não desliga um alerta manual da mesma variável.</li>
 * </ul>
 *
 * <h2>Envio</h2>
 * Só envia comandos quando o estado desejado MUDA (nada de spam a cada leitura).
 * Reconciliação: se o firmware reportar (alertState/ledState) algo diferente do desejado por mais de
 * {@code actuation-resync-seconds}, reenviamos — cobre comandos perdidos e reboots do ESP32.
 */
@Service
public class AlertActuationService {

    private static final Logger log = LoggerFactory.getLogger(AlertActuationService.class);

    /** Validade de um comando manual do dashboard sem novo comando (evita alerta "esquecido" ligado). */
    public static final Duration MANUAL_TTL = Duration.ofMinutes(10);

    private final CommandService commands;
    private final Duration resync;

    public AlertActuationService(CommandService commands, WineGuardProperties props) {
        this.commands = commands;
        this.resync = Duration.ofSeconds(Math.max(5, props.actuationResyncSeconds()));
    }

    public void reconcile(Device device, DeviceRuntimeState state, List<Incident> openIncidents,
                          EnvironmentConfig config, Instant now) {
        synchronized (state) {
            reconcileLocked(device, state, openIncidents, config, now);
        }
    }

    /**
     * Registra um comando manual do dashboard como intenção própria do dispositivo. O comando já foi
     * enviado; aqui só anotamos o que o operador pediu, para a reconciliação não desfazê-lo por não
     * haver incidente automático correspondente. Política: o override manual vale até um
     * {@code alertStop}/{@code ledOff} manual ou até {@link #MANUAL_TTL}.
     */
    public void registerManual(DeviceRuntimeState state, DeviceCommand command, String param, Instant now) {
        synchronized (state) {
            switch (command) {
                case ALERT_TEMP, ALERT_HUM, ALERT_LUX -> {
                    Variable v = variableOf(command);
                    state.manualAlerts.add(v);
                    if (state.sentAlerts != null) state.sentAlerts.add(v);
                }
                case LED_ON -> {
                    state.manualLed = true;
                    if (state.sentLed != null) state.sentLed = Boolean.TRUE;
                }
                case LED_OFF -> {
                    state.manualLed = false;
                    if (state.sentLed != null) state.sentLed = Boolean.FALSE;
                }
                case ALERT_STOP -> {
                    Variable v = stopTarget(param);
                    if (v == null) {
                        state.manualAlerts.clear();
                        if (state.sentAlerts != null) state.sentAlerts.clear();
                    } else {
                        state.manualAlerts.remove(v);
                        if (state.sentAlerts != null) state.sentAlerts.remove(v);
                    }
                }
                default -> { return; }   // demo e outros comandos não mudam a atuação
            }
            state.manualUntil = state.manualAlerts.isEmpty() && !state.manualLed ? null : now.plus(MANUAL_TTL);
            state.lastActuationAt = now;   // o resync conta a partir do envio manual
        }
    }

    private void reconcileLocked(Device device, DeviceRuntimeState state, List<Incident> openIncidents,
                                 EnvironmentConfig config, Instant now) {
        if (state.manualUntil != null && !now.isBefore(state.manualUntil)) {
            log.info("{}: comando manual expirou após {} min", device.getDeviceKey(), MANUAL_TTL.toMinutes());
            state.manualAlerts.clear();
            state.manualLed = false;
            state.manualUntil = null;
        }

        Set<Variable> desiredAlerts = EnumSet.noneOf(Variable.class);
        boolean anyEnvironmental = false;
        for (Incident i : openIncidents) {
            if (i.getType() == IncidentType.DEVICE_OFFLINE || i.getVariable() == null) continue;
            anyEnvironmental = true;
            if (i.getStatus() == IncidentStatus.ACTIVE && config.isBuzzerEnabled()) desiredAlerts.add(i.getVariable());
        }
        // Estado desejado = incidentes automáticos ∪ comandos manuais ainda válidos.
        desiredAlerts.addAll(state.manualAlerts);
        boolean desiredLed = (anyEnvironmental && config.isLedEnabled()) || state.manualLed;

        Set<Variable> reportedAlerts = state.reading != null ? state.reading.reportedAlerts() : null;
        Boolean reportedLed = state.reading != null ? state.reading.reportedLed() : null;

        // Primeiro ciclo após restart: assume o que o firmware reporta (ou "tudo desligado").
        if (state.sentAlerts == null) {
            state.sentAlerts = reportedAlerts != null ? EnumSet.copyOf(withNone(reportedAlerts)) : EnumSet.noneOf(Variable.class);
            state.sentLed = reportedLed != null ? reportedLed : Boolean.FALSE;
            state.lastActuationAt = now;
        }

        // Backoff após falha de envio (ex.: Orion fora do ar) para não inundar a linha do tempo.
        if (state.lastActuationFailureAt != null
                && Duration.between(state.lastActuationFailureAt, now).compareTo(resync) < 0) {
            return;
        }

        try {
            if (!desiredAlerts.equals(state.sentAlerts) || desiredLed != state.sentLed) {
                applyDiff(device, state, desiredAlerts, desiredLed);
                state.lastActuationAt = now;
                state.lastActuationFailureAt = null;
                return;
            }
            boolean diverged = (reportedAlerts != null && !reportedAlerts.equals(desiredAlerts))
                    || (reportedLed != null && reportedLed != desiredLed);
            if (diverged && Duration.between(state.lastActuationAt, now).compareTo(resync) >= 0) {
                log.info("{}: estado reportado ({} / led={}) diverge do desejado ({} / led={}), reenviando",
                        device.getDeviceKey(), reportedAlerts, reportedLed, desiredAlerts, desiredLed);
                fullSync(device, desiredAlerts, desiredLed);
                state.lastActuationAt = now;
            }
        } catch (RuntimeException e) {
            // Falha já registrada como evento COMMAND_FAILED; tenta de novo no próximo ciclo de resync.
            state.lastActuationAt = now;
            state.lastActuationFailureAt = now;
            log.warn("Falha na atuação de {}: {}", device.getDeviceKey(), e.getMessage());
        }
    }

    private void applyDiff(Device device, DeviceRuntimeState state, Set<Variable> desired, boolean desiredLed) {
        Set<Variable> removed = EnumSet.copyOf(withNone(state.sentAlerts));
        removed.removeAll(desired);
        Set<Variable> added = EnumSet.copyOf(withNone(desired));
        added.removeAll(state.sentAlerts);

        if (!removed.isEmpty()) {
            if (desired.isEmpty()) {
                commands.send(device, DeviceCommand.ALERT_STOP, "all", "condição normalizada/silenciada");
            } else {
                for (Variable v : removed) commands.send(device, DeviceCommand.ALERT_STOP, v.code(), v.label() + " normalizada");
            }
            state.sentAlerts.removeAll(removed);
        }
        for (Variable v : added) {
            commands.send(device, DeviceCommand.alertFor(v), "", v.label() + " fora do trigger");
            state.sentAlerts.add(v);
        }
        if (desiredLed != state.sentLed) {
            commands.send(device, desiredLed ? DeviceCommand.LED_ON : DeviceCommand.LED_OFF, "",
                    desiredLed ? "anomalia ativa" : "ambiente normalizado");
            state.sentLed = desiredLed;
        }
    }

    private void fullSync(Device device, Set<Variable> desired, boolean desiredLed) {
        commands.send(device, DeviceCommand.ALERT_STOP, "all", "ressincronização");
        for (Variable v : desired) commands.send(device, DeviceCommand.alertFor(v), "", "ressincronização");
        commands.send(device, desiredLed ? DeviceCommand.LED_ON : DeviceCommand.LED_OFF, "", "ressincronização");
    }

    /** Quando o dispositivo sai do ar o backend não sabe mais o estado real: força sincronização na volta. */
    public void forget(DeviceRuntimeState state) {
        state.sentAlerts = null;
        state.sentLed = null;
    }

    private static Set<Variable> withNone(Set<Variable> s) {
        return s.isEmpty() ? EnumSet.noneOf(Variable.class) : s;
    }

    private static Variable variableOf(DeviceCommand command) {
        return switch (command) {
            case ALERT_TEMP -> Variable.TEMPERATURE;
            case ALERT_HUM -> Variable.HUMIDITY;
            default -> Variable.LUMINOSITY;
        };
    }

    /** {@code alertStop|temp|hum|lux} → a variável; {@code all}, vazio ou desconhecido → null (todas). */
    private static Variable stopTarget(String param) {
        if (param == null || param.isBlank() || param.equalsIgnoreCase("all")) return null;
        try {
            return Variable.fromCode(param.trim().toLowerCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
