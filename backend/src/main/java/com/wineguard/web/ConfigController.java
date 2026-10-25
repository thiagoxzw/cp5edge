package com.wineguard.web;

import com.wineguard.dto.EnvironmentConfigDto;
import com.wineguard.service.EnvironmentConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/config/environment")
@Tag(name = "Configuração", description = "Triggers do ambiente e processo de alerta")
public class ConfigController {

    private final EnvironmentConfigService config;

    public ConfigController(EnvironmentConfigService config) {
        this.config = config;
    }

    @GetMapping
    public EnvironmentConfigDto get() {
        return EnvironmentConfigDto.from(config.current());
    }

    @PutMapping
    @Operation(summary = "Atualiza triggers, faixas de severidade, atuadores e heartbeat (aplicado no próximo ciclo)")
    public EnvironmentConfigDto update(@Valid @RequestBody EnvironmentConfigDto dto) {
        return config.update(dto);
    }
}
