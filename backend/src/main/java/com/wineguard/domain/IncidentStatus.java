package com.wineguard.domain;

/**
 * ACTIVE       -> anomalia em curso, buzzer + LED ligados
 * ACKNOWLEDGED -> operador reconheceu: buzzer silenciado, LED continua piscando
 * RESOLVED     -> condição normalizada, incidente encerrado
 */
public enum IncidentStatus {
    ACTIVE, ACKNOWLEDGED, RESOLVED
}
