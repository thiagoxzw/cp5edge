package com.wineguard.web;

import com.wineguard.demo.DemoService;
import com.wineguard.demo.DemoStatus;
import com.wineguard.dto.DemoStartRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/demo")
@Tag(name = "Modo Demo", description = "Roteiro automático para apresentação")
public class DemoController {

    private final DemoService demo;

    public DemoController(DemoService demo) {
        this.demo = demo;
    }

    @GetMapping
    public DemoStatus status() {
        return demo.status();
    }

    @PostMapping("/start")
    @Operation(summary = "Inicia o roteiro: normal → anomalia → incidente → LED/buzzer → normalização")
    public DemoStatus start(@Valid @RequestBody DemoStartRequest req) {
        return demo.start(req.deviceId(), req.scenario(), req.holdSeconds());
    }

    @PostMapping("/recover")
    @Operation(summary = "Antecipa o retorno ao normal")
    public DemoStatus recover() {
        return demo.recoverNow();
    }

    @PostMapping("/stop")
    public DemoStatus stop() {
        return demo.stop();
    }

    @DeleteMapping("/data")
    @Operation(summary = "Remove incidentes ENCERRADOS gerados pelo Modo Demo")
    public Map<String, Integer> clear() {
        return Map.of("deleted", demo.clearDemoData());
    }
}
