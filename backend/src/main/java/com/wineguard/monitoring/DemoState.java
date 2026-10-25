package com.wineguard.monitoring;

import org.springframework.stereotype.Component;

/** Qual dispositivo está em demonstração (incidentes abertos nele são marcados como demo). */
@Component
public class DemoState {

    private volatile Long activeDeviceId;

    public boolean isDemo(Long deviceId) {
        return deviceId != null && deviceId.equals(activeDeviceId);
    }

    public void activate(Long deviceId) {
        this.activeDeviceId = deviceId;
    }

    public void clear() {
        this.activeDeviceId = null;
    }
}
