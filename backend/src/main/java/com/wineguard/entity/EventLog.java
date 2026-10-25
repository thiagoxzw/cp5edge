package com.wineguard.entity;

import com.wineguard.domain.EventLevel;
import com.wineguard.domain.EventType;
import jakarta.persistence.*;

import java.time.Instant;

/** Linha do tempo de eventos ("Últimos eventos" no dashboard). */
@Entity
@Table(name = "event_log", indexes = @Index(name = "idx_event_ts", columnList = "timestamp"))
public class EventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant timestamp;

    /** Referência fraca (sem FK) para que o histórico sobreviva à exclusão do dispositivo. */
    private Long deviceId;

    @Column(length = 80)
    private String deviceName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EventType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventLevel level;

    @Column(nullable = false, length = 500)
    private String message;

    protected EventLog() {
    }

    public EventLog(EventType type, EventLevel level, Long deviceId, String deviceName, String message) {
        this.timestamp = Instant.now();
        this.type = type;
        this.level = level;
        this.deviceId = deviceId;
        this.deviceName = deviceName;
        this.message = message.length() > 500 ? message.substring(0, 500) : message;
    }

    public Long getId() { return id; }
    public Instant getTimestamp() { return timestamp; }
    public Long getDeviceId() { return deviceId; }
    public String getDeviceName() { return deviceName; }
    public EventType getType() { return type; }
    public EventLevel getLevel() { return level; }
    public String getMessage() { return message; }
}
