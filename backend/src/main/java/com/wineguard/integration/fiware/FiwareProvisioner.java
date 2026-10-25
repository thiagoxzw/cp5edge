package com.wineguard.integration.fiware;

import com.wineguard.config.WineGuardProperties;
import com.wineguard.domain.DeviceCommand;
import com.wineguard.entity.Device;
import com.wineguard.exception.FiwareIntegrationException;
import com.wineguard.telemetry.DeviceProvisioner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Provisionamento FIWARE, seguindo o fluxo padrão do IoT Agent UltraLight:
 * <ol>
 *   <li>Service group (apikey) em POST /iot/services</li>
 *   <li>Dispositivo em POST /iot/devices, com atributos (object_id -> nome NGSI) e comandos</li>
 *   <li>Subscription no Orion notificando o STH-Comet em formato "legacy" (único aceito pelo STH)</li>
 * </ol>
 * Todas as operações são idempotentes (409 = já existe).
 */
@Component
@ConditionalOnProperty(name = "wineguard.mode", havingValue = "fiware", matchIfMissing = true)
public class FiwareProvisioner implements DeviceProvisioner {

    private static final Logger log = LoggerFactory.getLogger(FiwareProvisioner.class);
    // Orion recusa caracteres como '>' e '=' na description (BadRequest "forbidden characters")
    static final String SUBSCRIPTION_DESCRIPTION = "WineGuard - Orion para STH-Comet";

    private final IotAgentClient iota;
    private final OrionClient orion;
    private final WineGuardProperties props;

    public FiwareProvisioner(IotAgentClient iota, OrionClient orion, WineGuardProperties props) {
        this.iota = iota;
        this.orion = orion;
        this.props = props;
    }

    @Override
    public boolean isRequired() {
        return props.fiware().autoProvision();
    }

    @Override
    public boolean ensureInfrastructure() {
        var f = props.fiware();
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("apikey", f.apiKey());
        group.put("cbroker", f.iotaCbrokerUrl());
        group.put("entity_type", f.entityType());
        group.put("resource", "/iot/d");
        IotAgentClient.CreateResult r = iota.createServiceGroup(Map.of("services", List.of(group)));
        log.info("Service group apikey={} -> {}", f.apiKey(), r);

        ensureSthSubscription();
        return true;
    }

    private void ensureSthSubscription() {
        var f = props.fiware();
        boolean exists = orion.listSubscriptions().stream().anyMatch(s -> {
            Object url = Optional.ofNullable((Map<?, ?>) s.get("notification"))
                    .map(n -> (Map<?, ?>) n.get("http")).map(h -> h.get("url")).orElse(null);
            return SUBSCRIPTION_DESCRIPTION.equals(s.get("description")) && f.sthNotifyUrl().equals(url);
        });
        if (exists) {
            log.info("Subscription Orion -> STH-Comet já existe");
            return;
        }
        // Sem "condition.attrs": qualquer atualização da entidade notifica (o "uptime" muda a cada envio,
        // então cada medição gera uma notificação e o STH registra os três valores).
        Map<String, Object> subscription = Map.of(
                "description", SUBSCRIPTION_DESCRIPTION,
                "subject", Map.of("entities", List.of(Map.of("idPattern", ".*", "type", f.entityType()))),
                "notification", Map.of(
                        "http", Map.of("url", f.sthNotifyUrl()),
                        "attrs", List.of(FiwareAttributes.TEMPERATURE, FiwareAttributes.HUMIDITY, FiwareAttributes.LUMINOSITY),
                        "attrsFormat", "legacy"));
        orion.createSubscription(subscription);
        log.info("Subscription Orion -> STH-Comet criada ({})", f.sthNotifyUrl());
    }

    @Override
    public String provision(Device device) {
        Map<String, Object> body = Map.of("devices", List.of(deviceDefinition(device, true)));
        IotAgentClient.CreateResult result;
        try {
            result = iota.createDevice(body);
        } catch (FiwareIntegrationException e) {
            // Algumas versões do IoT Agent rejeitam o campo legado "protocol": tenta de novo sem ele.
            if (e.getMessage() != null && e.getMessage().contains("protocol")) {
                result = iota.createDevice(Map.of("devices", List.of(deviceDefinition(device, false))));
            } else {
                throw e;
            }
        }
        return result == IotAgentClient.CreateResult.CREATED
                ? "Provisionado no IoT Agent"
                : "Já existia no IoT Agent (mantido)";
    }

    @Override
    public void deprovision(Device device) {
        iota.deleteDevice(device.getDeviceKey());
        orion.deleteEntity(device.getEntityId(), props.fiware().entityType());
    }

    Map<String, Object> deviceDefinition(Device device, boolean withProtocol) {
        var f = props.fiware();
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("device_id", device.getDeviceKey());
        d.put("entity_name", device.getEntityId());
        d.put("entity_type", f.entityType());
        if (withProtocol) d.put("protocol", "PDI-IoTA-UltraLight");
        d.put("transport", "MQTT");
        d.put("apikey", f.apiKey());
        d.put("attributes", List.of(
                attr("t", FiwareAttributes.TEMPERATURE, "Number"),
                attr("h", FiwareAttributes.HUMIDITY, "Number"),
                attr("l", FiwareAttributes.LUMINOSITY, "Number"),
                attr("a", FiwareAttributes.ALERT_STATE, "Text"),
                attr("k", FiwareAttributes.LED_STATE, "Text"),
                attr("d", FiwareAttributes.DEMO_MODE, "Text"),
                attr("u", FiwareAttributes.UPTIME, "Number")));
        List<Map<String, Object>> commands = new ArrayList<>();
        for (DeviceCommand c : DeviceCommand.values()) {
            commands.add(Map.of("name", c.wireName(), "type", "command"));
        }
        d.put("commands", commands);
        d.put("static_attributes", List.of(
                Map.of("name", "zone", "type", "Text", "value", device.getZone().name())));
        return d;
    }

    private static Map<String, Object> attr(String objectId, String name, String type) {
        return Map.of("object_id", objectId, "name", name, "type", type);
    }
}
