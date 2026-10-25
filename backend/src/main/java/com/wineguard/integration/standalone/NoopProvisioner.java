package com.wineguard.integration.standalone;

import com.wineguard.entity.Device;
import com.wineguard.telemetry.DeviceProvisioner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "wineguard.mode", havingValue = "standalone")
public class NoopProvisioner implements DeviceProvisioner {

    @Override
    public boolean ensureInfrastructure() {
        return true;
    }

    @Override
    public String provision(Device device) {
        return "Modo standalone: provisionamento FIWARE não necessário";
    }

    @Override
    public void deprovision(Device device) {
    }

    @Override
    public boolean isRequired() {
        return false;
    }
}
