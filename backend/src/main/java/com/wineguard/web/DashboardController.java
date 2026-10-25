package com.wineguard.web;

import com.wineguard.dto.IntelligenceResponse;
import com.wineguard.service.IntelligenceService;

import com.wineguard.dto.DashboardSummary;
import com.wineguard.dto.EventResponse;
import com.wineguard.dto.SystemStatus;
import com.wineguard.service.DashboardService;
import com.wineguard.service.EventService;
import com.wineguard.service.ProvisioningBootstrap;
import com.wineguard.service.SystemStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Dashboard", description = "Visão geral, eventos e saúde da plataforma")
public class DashboardController {

    private final DashboardService dashboard;
    private final IntelligenceService intelligence;
    private final EventService events;
    private final SystemStatusService system;
    private final ProvisioningBootstrap bootstrap;

    public DashboardController(DashboardService dashboard, EventService events, SystemStatusService system,
                               ProvisioningBootstrap bootstrap, IntelligenceService intelligence) {
        this.dashboard = dashboard;
        this.intelligence = intelligence;
        this.events = events;
        this.system = system;
        this.bootstrap = bootstrap;
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Resumo da página inicial: contadores, Wine Score geral, mapa, dispositivos, incidentes e eventos")
    public DashboardSummary summary() {
        return dashboard.summary();
    }

    @GetMapping("/events")
    @Operation(summary = "Últimos eventos (linha do tempo)")
    public List<EventResponse> events(@RequestParam(defaultValue = "30") int limit) {
        return events.recent(limit);
    }

    @GetMapping("/intelligence")
    @Operation(summary = "WineGuard Intelligence de todos os dispositivos ativos, do maior risco para o menor")
    public List<IntelligenceResponse> intelligence() {
        return intelligence.all();
    }

    @GetMapping("/system/status")
    @Operation(summary = "Saúde de Orion, IoT Agent, STH-Comet, ciclo de monitoramento e simulador")
    public SystemStatus status() {
        return system.status();
    }

    @PostMapping("/system/provisioning/retry")
    @Operation(summary = "Refaz service group, subscription Orion→STH e provisionamento pendente")
    public SystemStatus retryProvisioning() {
        bootstrap.reset();
        return system.status();
    }
}
