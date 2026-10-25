package com.wineguard.monitoring;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Mapa deviceId -> estado em tempo de execução. */
@Component
public class DeviceStateRegistry {

    private final Map<Long, DeviceRuntimeState> states = new ConcurrentHashMap<>();

    public DeviceRuntimeState get(Long deviceId) {
        return states.computeIfAbsent(deviceId, DeviceRuntimeState::new);
    }

    public DeviceRuntimeState peek(Long deviceId) {
        return states.get(deviceId);
    }

    public void remove(Long deviceId) {
        states.remove(deviceId);
    }

    public void retainOnly(Set<Long> ids) {
        states.keySet().retainAll(ids);
    }

    public Collection<DeviceRuntimeState> all() {
        return states.values();
    }
}
