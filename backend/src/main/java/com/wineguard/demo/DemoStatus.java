package com.wineguard.demo;

import java.time.Instant;
import java.util.List;

/** Estado da demonstração para a tela "Modo Demo" (atualizado a cada segundo). */
public record DemoStatus(
        boolean running,
        DemoPhase phase,
        Long deviceId,
        String deviceName,
        DemoScenario scenario,
        String variable,
        String unit,
        Instant startedAt,
        int holdSeconds,
        Integer baselineScore,
        Integer currentScore,
        Integer minScore,
        Double currentValue,
        Double limitValue,
        String injectedParam,
        Long incidentId,
        String message,
        List<Step> steps
) {

    /** Um dos 11 passos do roteiro. {@code done} é derivado do estado REAL do sistema. */
    public record Step(int number, String key, String label, boolean done, Instant at, String detail) {
    }
}
