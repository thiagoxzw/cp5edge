package com.wineguard.web;

import com.wineguard.domain.AlertLevel;
import com.wineguard.domain.IncidentType;
import com.wineguard.dto.IncidentResponse;
import com.wineguard.dto.PageResponse;
import com.wineguard.service.IncidentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/incidents")
@Tag(name = "Incidentes", description = "Central de incidentes")
public class IncidentController {

    private final IncidentService incidents;

    public IncidentController(IncidentService incidents) {
        this.incidents = incidents;
    }

    @GetMapping
    @Operation(summary = "Histórico de incidentes. status=open|ACTIVE|ACKNOWLEDGED|RESOLVED")
    public PageResponse<IncidentResponse> search(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) AlertLevel severity,
            @RequestParam(required = false) IncidentType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)),
                Sort.by(Sort.Direction.DESC, "startedAt"));
        return PageResponse.of(incidents.search(status, deviceId, severity, type, from, pageable));
    }

    @GetMapping("/{id}")
    public IncidentResponse get(@PathVariable Long id) {
        return incidents.get(id);
    }

    @PostMapping("/{id}/acknowledge")
    @Operation(summary = "Reconhece o incidente: silencia o buzzer (o LED segue piscando até normalizar)")
    public IncidentResponse acknowledge(@PathVariable Long id) {
        return incidents.acknowledge(id);
    }
}
