package com.wineguard.telemetry;

import com.wineguard.domain.Variable;
import com.wineguard.entity.Device;

/**
 * Fonte dos dados HISTÓRICOS.
 * FIWARE: STH-Comet (API :8666). Standalone: buffer em memória com leituras reais desde o boot.
 * Nenhuma implementação inventa dados passados.
 */
public interface HistorySource {

    HistorySeries fetch(Device device, Variable variable, HistoryPeriod period);
}
