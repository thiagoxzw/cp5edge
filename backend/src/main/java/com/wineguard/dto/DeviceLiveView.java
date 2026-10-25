package com.wineguard.dto;

import com.wineguard.domain.AlertLevel;
import com.wineguard.domain.Connectivity;
import com.wineguard.domain.HealthStatus;
import com.wineguard.rules.WineScore;

import java.time.Instant;
import java.util.List;

/**
 * Situação em tempo real de um dispositivo.
 *
 * @param offlineSeconds tempo desde o último contato (quando offline/instável)
 * @param pending        variáveis fora da faixa aguardando o "tempo para disparo"
 * @param ledActive      LED reportado pelo firmware (null se não reporta)
 * @param buzzerAlerts   alertas sonoros reportados pelo firmware
 */
public record DeviceLiveView(
        HealthStatus health,
        Connectivity connectivity,
        AlertLevel environmentLevel,
        Double temperature,
        Double humidity,
        Double luminosity,
        Instant lastSeenAt,
        Long secondsSinceLastSeen,
        Instant connectivitySince,
        List<String> pending,
        Integer score,
        String scoreLabel,
        List<WineScore.Component> scoreComponents,
        Boolean ledActive,
        List<String> buzzerAlerts,
        String demoMode,
        Long uptime,
        int activeIncidents
) {
}
