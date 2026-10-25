package com.wineguard.service;

import com.wineguard.domain.EventLevel;
import com.wineguard.domain.EventType;
import com.wineguard.dto.EnvironmentConfigDto;
import com.wineguard.entity.EnvironmentConfig;
import com.wineguard.repository.EnvironmentConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Configuração do ambiente persistida no banco. Mantém uma cópia em memória para o ciclo de
 * monitoramento (executado a cada 2s), atualizada sempre que o frontend salva.
 */
@Service
public class EnvironmentConfigService {

    private final EnvironmentConfigRepository repository;
    private final EventService events;
    private volatile EnvironmentConfig cached;

    public EnvironmentConfigService(EnvironmentConfigRepository repository, EventService events) {
        this.repository = repository;
        this.events = events;
    }

    /** Configuração atual (cria a padrão no primeiro uso). */
    @Transactional
    public EnvironmentConfig current() {
        EnvironmentConfig c = cached;
        if (c == null) {
            c = repository.findById(EnvironmentConfig.SINGLETON_ID).orElseGet(() -> repository.save(new EnvironmentConfig()));
            cached = c;
        }
        return c;
    }

    @Transactional
    public EnvironmentConfigDto update(EnvironmentConfigDto dto) {
        EnvironmentConfig c = repository.findById(EnvironmentConfig.SINGLETON_ID).orElseGet(EnvironmentConfig::new);
        dto.applyTo(c);
        c = repository.saveAndFlush(c);
        cached = c;
        events.record(EventType.CONFIG_UPDATED, EventLevel.INFO, null, String.format(Locale.ROOT,
                "Triggers atualizados: T %.1f–%.1f°C, UR %.0f–%.0f%%, Luz ≤ %.0f%%, buzzer %s, LED %s",
                c.getTemperatureMin(), c.getTemperatureMax(), c.getHumidityMin(), c.getHumidityMax(),
                c.getLuminosityMax(), c.isBuzzerEnabled() ? "on" : "off", c.isLedEnabled() ? "on" : "off"));
        return EnvironmentConfigDto.from(c);
    }
}
