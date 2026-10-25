package com.wineguard.service;

import com.wineguard.config.WineGuardProperties;
import com.wineguard.domain.*;
import com.wineguard.dto.CommandRequest;
import com.wineguard.dto.DeviceRequest;
import com.wineguard.dto.DeviceResponse;
import com.wineguard.entity.Device;
import com.wineguard.entity.Incident;
import com.wineguard.exception.BusinessException;
import com.wineguard.exception.NotFoundException;
import com.wineguard.monitoring.AlertActuationService;
import com.wineguard.monitoring.DeviceStateRegistry;
import com.wineguard.repository.DeviceRepository;
import com.wineguard.repository.IncidentRepository;
import com.wineguard.simulation.VirtualDeviceRegistry;
import com.wineguard.telemetry.DeviceProvisioner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** CRUD de dispositivos + integração com o provisionamento FIWARE. */
@Service
public class DeviceService {

    private static final Logger log = LoggerFactory.getLogger(DeviceService.class);

    private final DeviceRepository repository;
    private final IncidentRepository incidentRepository;
    private final IncidentService incidents;
    private final DeviceProvisioner provisioner;
    private final DeviceViewMapper mapper;
    private final EventService events;
    private final CommandService commands;
    private final DeviceStateRegistry states;
    private final VirtualDeviceRegistry virtualDevices;
    private final WineGuardProperties props;
    private final AlertActuationService actuation;

    public DeviceService(DeviceRepository repository, IncidentRepository incidentRepository, IncidentService incidents,
                         DeviceProvisioner provisioner, DeviceViewMapper mapper, EventService events,
                         CommandService commands, DeviceStateRegistry states, VirtualDeviceRegistry virtualDevices,
                         WineGuardProperties props, AlertActuationService actuation) {
        this.repository = repository;
        this.incidentRepository = incidentRepository;
        this.incidents = incidents;
        this.provisioner = provisioner;
        this.mapper = mapper;
        this.events = events;
        this.commands = commands;
        this.states = states;
        this.virtualDevices = virtualDevices;
        this.props = props;
        this.actuation = actuation;
    }

    @Transactional(readOnly = true)
    public List<DeviceResponse> list() {
        Map<Long, List<Incident>> open = incidents.allOpen().stream()
                .collect(Collectors.groupingBy(i -> i.getDevice().getId()));
        return repository.findAllByOrderByZoneAscNameAsc().stream()
                .map(d -> mapper.toResponse(d, open.getOrDefault(d.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public DeviceResponse get(Long id) {
        Device d = find(id);
        return mapper.toResponse(d, incidents.openFor(id));
    }

    public Device find(Long id) {
        return repository.findById(id).orElseThrow(() -> NotFoundException.device(id));
    }

    @Transactional
    public DeviceResponse create(DeviceRequest req) {
        String key = req.deviceKey().trim();
        if (repository.existsByDeviceKey(key)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Já existe um dispositivo com o identificador '" + key + "'");
        }
        Device d = new Device();
        d.setDeviceKey(key);
        d.setEntityId("urn:ngsi-ld:" + props.fiware().entityType() + ":" + key);
        apply(d, req);
        d.setProvisioning(provisioner.isRequired() ? ProvisioningStatus.PENDING : ProvisioningStatus.NOT_REQUIRED);
        d = repository.save(d);
        events.record(EventType.DEVICE_CREATED, EventLevel.INFO, d,
                "Dispositivo cadastrado em " + d.getZone().label() + (d.isSimulated() ? " (virtual)" : ""));
        provisionQuietly(d);
        return mapper.toResponse(d, List.of());
    }

    @Transactional
    public DeviceResponse update(Long id, DeviceRequest req) {
        Device d = find(id);
        if (!d.getDeviceKey().equals(req.deviceKey().trim())) {
            throw new BusinessException("O identificador (device_id) não pode ser alterado: ele está provisionado no "
                    + "IoT Agent e gravado no firmware. Exclua e cadastre novamente.");
        }
        DeviceStatus before = d.getStatus();
        apply(d, req);
        d = repository.save(d);
        if (before == DeviceStatus.ACTIVE && d.getStatus() == DeviceStatus.INACTIVE) {
            onDeactivated(d);
        }
        events.record(EventType.DEVICE_UPDATED, EventLevel.INFO, d, "Cadastro atualizado");
        return mapper.toResponse(d, incidents.openFor(id));
    }

    @Transactional
    public DeviceResponse changeStatus(Long id, DeviceStatus status) {
        Device d = find(id);
        if (d.getStatus() == status) return mapper.toResponse(d, incidents.openFor(id));
        d.setStatus(status);
        d = repository.save(d);
        if (status == DeviceStatus.INACTIVE) onDeactivated(d);
        events.record(EventType.DEVICE_UPDATED, EventLevel.INFO, d,
                status == DeviceStatus.ACTIVE ? "Dispositivo reativado" : "Dispositivo desativado");
        return mapper.toResponse(d, incidents.openFor(id));
    }

    /** Ao desativar: encerra incidentes abertos e desliga LED/buzzer (best effort). */
    private void onDeactivated(Device d) {
        Instant now = Instant.now();
        for (Incident i : incidents.openFor(d.getId())) incidents.resolve(i, i.getLastValue(), now);
        try {
            commands.send(d, DeviceCommand.ALERT_STOP, "all", "dispositivo desativado");
            commands.send(d, DeviceCommand.LED_OFF, "", "dispositivo desativado");
        } catch (RuntimeException e) {
            log.info("Não foi possível desligar atuadores de {}: {}", d.getDeviceKey(), e.getMessage());
        }
        states.remove(d.getId());
    }

    @Transactional
    public void delete(Long id) {
        Device d = find(id);
        if (provisioner.isRequired()) {
            try {
                provisioner.deprovision(d);
            } catch (RuntimeException e) {
                log.warn("Falha ao remover {} do FIWARE (continuando a exclusão): {}", d.getDeviceKey(), e.getMessage());
            }
        }
        incidentRepository.deleteByDeviceId(id);
        repository.delete(d);
        states.remove(id);
        virtualDevices.remove(d.getDeviceKey());
        events.record(EventType.DEVICE_DELETED, EventLevel.WARNING, null,
                "Dispositivo " + d.getName() + " (" + d.getDeviceKey() + ") excluído");
    }

    @Transactional
    public DeviceResponse provision(Long id) {
        Device d = find(id);
        if (!provisioner.isRequired()) {
            throw new BusinessException(HttpStatus.CONFLICT, "Provisionamento FIWARE desabilitado neste modo");
        }
        provisionQuietly(d);
        return mapper.toResponse(d, incidents.openFor(id));
    }

    /** Tenta provisionar e registra o resultado no próprio cadastro (não falha o CRUD se o FIWARE estiver fora). */
    @Transactional
    public void provisionQuietly(Device d) {
        if (!provisioner.isRequired()) return;
        try {
            String msg = provisioner.provision(d);
            d.setProvisioning(ProvisioningStatus.PROVISIONED);
            d.setProvisioningMessage(msg);
            events.record(EventType.DEVICE_PROVISIONED, EventLevel.SUCCESS, d, msg);
        } catch (RuntimeException e) {
            d.setProvisioning(ProvisioningStatus.FAILED);
            d.setProvisioningMessage(e.getMessage());
            log.warn("Provisionamento de {} falhou: {}", d.getDeviceKey(), e.getMessage());
        }
        repository.save(d);
    }

    /** Comando manual (painel do dispositivo / testes de atuadores). */
    public void sendCommand(Long id, CommandRequest req) {
        Device d = find(id);
        if (!d.isActive()) throw new BusinessException(HttpStatus.CONFLICT, "Dispositivo desativado");
        DeviceCommand command;
        try {
            command = DeviceCommand.fromWireName(req.command());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(e.getMessage());
        }
        commands.send(d, command, req.param(), "manual pelo dashboard");
        // Anota a intenção manual para a reconciliação não desfazer o comando (ver AlertActuationService).
        actuation.registerManual(states.get(d.getId()), command, req.param(), Instant.now());
    }

    /** Liga/desliga a "perda de conexão" simulada de um dispositivo virtual. */
    public void setVirtualPaused(Long id, boolean paused) {
        Device d = find(id);
        if (!d.isSimulated()) throw new BusinessException("Somente dispositivos virtuais podem simular perda de conexão");
        virtualDevices.getOrCreate(d).setPaused(paused);
        events.record(EventType.DEMO, EventLevel.INFO, d,
                paused ? "Simulação: transmissão interrompida" : "Simulação: transmissão retomada");
    }

    private void apply(Device d, DeviceRequest req) {
        d.setName(req.name().trim());
        d.setZone(req.zone());
        d.setDescription(req.description());
        d.setSensors(req.sensors());
        d.setStatus(req.status() != null ? req.status() : DeviceStatus.ACTIVE);
        d.setSimulated(req.simulated());
    }
}
