package com.wineguard.dto;

import com.wineguard.domain.EventLevel;
import com.wineguard.domain.EventType;
import com.wineguard.entity.EventLog;

import java.time.Instant;

public record EventResponse(Long id, Instant timestamp, Long deviceId, String deviceName,
                            EventType type, EventLevel level, String message) {

    public static EventResponse from(EventLog e) {
        return new EventResponse(e.getId(), e.getTimestamp(), e.getDeviceId(), e.getDeviceName(),
                e.getType(), e.getLevel(), e.getMessage());
    }
}
