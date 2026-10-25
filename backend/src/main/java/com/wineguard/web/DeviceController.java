package com.wineguard.web;

import com.wineguard.dto.*;
import com.wineguard.service.DeviceService;
import com.wineguard.service.IntelligenceService;
import com.wineguard.dto.IntelligenceResponse;
import com.wineguard.service.EventService;
import com.wineguard.service.HistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/devices")
@Tag(name = "Dispositivos", description = "Cadastro, gerenciamento, histórico e comandos")
public class DeviceController {

    private final DeviceService devices;
    private final IntelligenceService intelligence;
    private final HistoryService history;
    private final EventService events;

    public DeviceController(DeviceService devices, HistoryService history, EventService events,
                            IntelligenceService intelligence) {
        this.devices = devices;
        this.intelligence = intelligence;
        this.history = history;
        this.events = events;
    }

    @GetMapping
    @Operation(summary = "Lista dispositivos com status e leituras em tempo real")
    public List<DeviceResponse> list() {
        return devices.list();
    }

    @GetMapping("/{id}")
    public DeviceResponse get(@PathVariable Long id) {
        return devices.get(id);
    }

    @PostMapping
    @Operation(summary = "Cadastra e provisiona o dispositivo no IoT Agent")
    public ResponseEntity<DeviceResponse> create(@Valid @RequestBody DeviceRequest req) {
        DeviceResponse created = devices.create(req);
        return ResponseEntity.created(URI.create("/api/devices/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public DeviceResponse update(@PathVariable Long id, @Valid @RequestBody DeviceRequest req) {
        return devices.update(id, req);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Ativa ou desativa o dispositivo")
    public DeviceResponse changeStatus(@PathVariable Long id, @Valid @RequestBody StatusChangeRequest req) {
        return devices.changeStatus(id, req.status());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Exclui o dispositivo (remove também do IoT Agent e do Orion)")
    public void delete(@PathVariable Long id) {
        devices.delete(id);
    }

    @PostMapping("/{id}/provision")
    @Operation(summary = "Reprovisiona o dispositivo no IoT Agent")
    public DeviceResponse provision(@PathVariable Long id) {
        return devices.provision(id);
    }

    @PostMapping("/{id}/commands")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Envia comando ao ESP32 (ledOn, ledOff, alertTemp, alertHum, alertLux, alertStop, demo)")
    public Map<String, String> command(@PathVariable Long id, @Valid @RequestBody CommandRequest req) {
        devices.sendCommand(id, req);
        return Map.of("status", "sent", "command", req.command());
    }

    @GetMapping("/{id}/intelligence")
    @Operation(summary = "WineGuard Intelligence: índice de risco de conservação (0–100), fatores e recomendações")
    public IntelligenceResponse intelligence(@PathVariable Long id) {
        return intelligence.forDevice(id);
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Histórico via STH-Comet (:8666). variable=temperature|humidity|luminosity, period=1h|6h|24h|7d|30d")
    public HistoryResponse history(@PathVariable Long id,
                                   @RequestParam(defaultValue = "temperature") String variable,
                                   @RequestParam(defaultValue = "1h") String period) {
        return history.history(id, variable, period);
    }

    @GetMapping("/{id}/events")
    public List<EventResponse> events(@PathVariable Long id, @RequestParam(defaultValue = "20") int limit) {
        devices.find(id);
        return events.forDevice(id, limit);
    }

    @PostMapping("/{id}/simulation/connection")
    @Operation(summary = "Dispositivo virtual: interrompe (paused=true) ou retoma a transmissão para demonstrar o heartbeat")
    public Map<String, Object> simulateConnection(@PathVariable Long id, @RequestParam boolean paused) {
        devices.setVirtualPaused(id, paused);
        return Map.of("paused", paused);
    }
}
