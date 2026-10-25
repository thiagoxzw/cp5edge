package com.wineguard.dto;

import com.wineguard.demo.DemoScenario;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * @param holdSeconds quanto tempo manter a anomalia ativa depois que o alerta disparar (padrão 20s)
 */
public record DemoStartRequest(@NotNull Long deviceId, @NotNull DemoScenario scenario,
                               @Min(5) @Max(300) Integer holdSeconds) {
}
