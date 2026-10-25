package com.wineguard.monitoring;

import com.wineguard.domain.Connectivity;
import com.wineguard.entity.EnvironmentConfig;

import java.time.Duration;
import java.time.Instant;

/**
 * Regra de heartbeat (função pura).
 * <pre>
 *   sem contato nunca                         -> NEVER_SEEN
 *   tempo desde o último contato &lt;= unstableAfterSeconds  -> ONLINE   🟢
 *   tempo desde o último contato &lt;= offlineTimeoutSeconds -> UNSTABLE 🟡
 *   acima disso                                         -> OFFLINE  ⚫
 * </pre>
 * O ESP32 publica a cada 5s; os padrões (20s / 60s) toleram 3 e 11 mensagens perdidas.
 */
public final class Heartbeat {

    private Heartbeat() {
    }

    public static Connectivity evaluate(Instant lastSeenAt, Instant now, EnvironmentConfig c) {
        if (lastSeenAt == null) return Connectivity.NEVER_SEEN;
        long seconds = Duration.between(lastSeenAt, now).toSeconds();
        if (seconds <= c.getUnstableAfterSeconds()) return Connectivity.ONLINE;
        if (seconds <= c.getOfflineTimeoutSeconds()) return Connectivity.UNSTABLE;
        return Connectivity.OFFLINE;
    }
}
