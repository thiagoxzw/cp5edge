package com.wineguard.entity;

import com.wineguard.domain.DeviceStatus;
import com.wineguard.domain.ProvisioningStatus;
import com.wineguard.domain.SensorType;
import com.wineguard.domain.Zone;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/** Dispositivo IoT cadastrado (um ESP32 físico/Wokwi ou um dispositivo virtual). */
@Entity
@Table(name = "devices")
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    /** device_id no IoT Agent e no tópico MQTT (ex.: wg001). */
    @Column(name = "device_key", nullable = false, unique = true, length = 40)
    private String deviceKey;

    /** id da entidade NGSI no Orion (ex.: urn:ngsi-ld:WineSensor:wg001). */
    @Column(nullable = false, unique = true, length = 120)
    private String entityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Zone zone;

    @Column(length = 500)
    private String description;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "device_sensors", joinColumns = @JoinColumn(name = "device_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "sensor", length = 20)
    private Set<SensorType> sensors = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeviceStatus status = DeviceStatus.ACTIVE;

    /** true = dispositivo virtual simulado pelo backend (gêmeo de software do ESP32). */
    @Column(nullable = false)
    private boolean simulated;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProvisioningStatus provisioning = ProvisioningStatus.PENDING;

    @Column(length = 500)
    private String provisioningMessage;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isActive() {
        return status == DeviceStatus.ACTIVE;
    }

    public boolean hasSensor(SensorType sensor) {
        return sensors.contains(sensor);
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDeviceKey() { return deviceKey; }
    public void setDeviceKey(String deviceKey) { this.deviceKey = deviceKey; }
    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }
    public Zone getZone() { return zone; }
    public void setZone(Zone zone) { this.zone = zone; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Set<SensorType> getSensors() { return sensors; }
    public void setSensors(Set<SensorType> sensors) {
        this.sensors = sensors == null || sensors.isEmpty() ? EnumSet.allOf(SensorType.class) : new HashSet<>(sensors);
    }
    public DeviceStatus getStatus() { return status; }
    public void setStatus(DeviceStatus status) { this.status = status; }
    public boolean isSimulated() { return simulated; }
    public void setSimulated(boolean simulated) { this.simulated = simulated; }
    public ProvisioningStatus getProvisioning() { return provisioning; }
    public void setProvisioning(ProvisioningStatus provisioning) { this.provisioning = provisioning; }
    public String getProvisioningMessage() { return provisioningMessage; }
    public void setProvisioningMessage(String provisioningMessage) { this.provisioningMessage = provisioningMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
