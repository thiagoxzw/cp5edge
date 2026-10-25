package com.wineguard.dto;

import com.wineguard.rules.RiskIndexCalculator;

/** WineGuard Intelligence de um dispositivo: índice de risco explicável + identificação do dispositivo. */
public record IntelligenceResponse(Long deviceId, String deviceKey, String deviceName, String zoneLabel,
                                   boolean simulated, RiskIndexCalculator.Report report) {
}
