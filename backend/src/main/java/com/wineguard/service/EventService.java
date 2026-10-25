package com.wineguard.service;

import com.wineguard.domain.EventLevel;
import com.wineguard.domain.EventType;
import com.wineguard.dto.EventResponse;
import com.wineguard.entity.Device;
import com.wineguard.entity.EventLog;
import com.wineguard.repository.EventLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Registro da linha do tempo de eventos (incidentes, comandos, conectividade, configuração, demo). */
@Service
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private final EventLogRepository repository;

    public EventService(EventLogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(EventType type, EventLevel level, Device device, String message) {
        repository.save(new EventLog(type, level,
                device != null ? device.getId() : null,
                device != null ? device.getName() : null,
                message));
        log.info("[{}] {}{}", type, device != null ? device.getName() + ": " : "", message);
    }

    @Transactional(readOnly = true)
    public List<EventResponse> recent(int limit) {
        return repository.findAllByOrderByTimestampDesc(PageRequest.of(0, clamp(limit)))
                .stream().map(EventResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<EventResponse> forDevice(Long deviceId, int limit) {
        return repository.findByDeviceIdOrderByTimestampDesc(deviceId, PageRequest.of(0, clamp(limit)))
                .stream().map(EventResponse::from).toList();
    }

    private static int clamp(int limit) {
        return Math.max(1, Math.min(limit, 200));
    }
}
