package com.wineguard.domain;

/**
 * Status exibido no dashboard (combina conectividade + incidentes).
 * 🟢 ONLINE  🟡 UNSTABLE  🔴 ALERT  ⚫ OFFLINE  (INACTIVE = cadastro desativado)
 */
public enum HealthStatus {
    ONLINE, UNSTABLE, ALERT, OFFLINE, INACTIVE
}
