package com.wineguard.demo;

public enum DemoPhase {
    /** Nenhuma demonstração em andamento. */
    IDLE,
    /** Registrando a linha de base (ambiente normal). */
    BASELINE,
    /** Offset enviado; aguardando detecção + incidente + atuação. */
    INJECTING,
    /** Anomalia ativa: plateia vê LED piscando e buzzer. */
    HOLDING,
    /** Offset removido; aguardando normalização e encerramento do incidente. */
    RECOVERING,
    DONE,
    ABORTED
}
