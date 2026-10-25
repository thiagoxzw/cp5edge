package com.wineguard.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Tarefas periódicas (monitoramento, simulador, demo, provisionamento). Desligável nos testes. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "wineguard.scheduling-enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
