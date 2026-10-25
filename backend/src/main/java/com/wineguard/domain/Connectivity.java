package com.wineguard.domain;

/**
 * Estado de conectividade calculado pelo heartbeat.
 * ONLINE   -> último contato dentro do intervalo esperado
 * UNSTABLE -> mensagens atrasadas (acima de unstableAfterSeconds)
 * OFFLINE  -> sem contato há mais de offlineTimeoutSeconds
 * NEVER_SEEN -> nunca enviou dados
 */
public enum Connectivity {
    ONLINE, UNSTABLE, OFFLINE, NEVER_SEEN
}
