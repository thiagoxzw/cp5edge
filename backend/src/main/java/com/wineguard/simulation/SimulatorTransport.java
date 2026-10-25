package com.wineguard.simulation;

import com.wineguard.entity.Device;

/** Para onde os dispositivos virtuais publicam suas medições. */
public interface SimulatorTransport {

    void publish(Device device, Measurement measurement);

    boolean isReady();
}
