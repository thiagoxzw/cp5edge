package com.wineguard.simulation;

import com.wineguard.config.WineGuardProperties;
import com.wineguard.entity.Device;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Transporte MQTT dos dispositivos virtuais no modo fiware. Eles se comportam como o ESP32:
 * <ul>
 *   <li>publicam medições em  /{apikey}/{device_id}/attrs</li>
 *   <li>assinam comandos em    /{apikey}/+/cmd  (e /ul/{apikey}/+/cmd, formato novo do IoT Agent)</li>
 *   <li>confirmam em           /{apikey}/{device_id}/cmdexe</li>
 * </ul>
 * Assim a demonstração com dispositivos virtuais passa pelo FIWARE real (Orion, IoT Agent, STH).
 */
@Component
@ConditionalOnProperty(name = "wineguard.mode", havingValue = "fiware", matchIfMissing = true)
public class MqttSimulatorTransport implements SimulatorTransport, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(MqttSimulatorTransport.class);

    private final WineGuardProperties props;
    private final VirtualDeviceRegistry registry;
    /** Comandos são tratados fora da thread de callback do Paho (publicar dentro dela pode travar). */
    private final ExecutorService commandExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "mqtt-cmd"));
    private final ScheduledExecutorService connector = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "mqtt-connect"));
    private volatile MqttClient client;

    public MqttSimulatorTransport(WineGuardProperties props, VirtualDeviceRegistry registry) {
        this.props = props;
        this.registry = registry;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        connector.scheduleWithFixedDelay(this::tryConnect, 0, 15, TimeUnit.SECONDS);
    }

    private void tryConnect() {
        if (client != null && client.isConnected()) return;
        try {
            if (client == null) {
                client = new MqttClient(props.mqtt().brokerUrl(), "wineguard-sim-" + UUID.randomUUID().toString().substring(0, 8),
                        new MemoryPersistence());
                client.setCallback(new Callback());
            }
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setConnectionTimeout(5);
            if (props.mqtt().username() != null && !props.mqtt().username().isBlank()) {
                options.setUserName(props.mqtt().username());
                options.setPassword(props.mqtt().password() == null ? new char[0] : props.mqtt().password().toCharArray());
            }
            client.connect(options);
        } catch (MqttException e) {
            log.warn("MQTT {} indisponível para os dispositivos virtuais ({}). Nova tentativa em 15s.",
                    props.mqtt().brokerUrl(), e.getMessage());
        }
    }

    @Override
    public boolean isReady() {
        return client != null && client.isConnected();
    }

    @Override
    public void publish(Device device, Measurement measurement) {
        String topic = "/" + props.fiware().apiKey() + "/" + device.getDeviceKey() + "/attrs";
        publish(topic, UltraLight.measurement(measurement));
    }

    private void publish(String topic, String payload) {
        try {
            MqttMessage msg = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
            msg.setQos(0);
            client.publish(topic, msg);
        } catch (MqttException e) {
            throw new IllegalStateException("Falha ao publicar em " + topic + ": " + e.getMessage(), e);
        }
    }

    private void handleCommand(String topic, String payload) {
        try {
            UltraLight.Command cmd = UltraLight.parseCommand(payload);
            registry.handleCommand(cmd.deviceKey(), cmd.name(), cmd.param()).ifPresent(result ->
                    publish("/" + props.fiware().apiKey() + "/" + cmd.deviceKey() + "/cmdexe", cmd.ack(result)));
        } catch (Exception e) {
            log.debug("Mensagem ignorada em {}: {} ({})", topic, payload, e.getMessage());
        }
    }

    private class Callback implements MqttCallbackExtended {
        @Override
        public void connectComplete(boolean reconnect, String serverURI) {
            String apiKey = props.fiware().apiKey();
            try {
                client.subscribe(new String[]{"/" + apiKey + "/+/cmd", "/ul/" + apiKey + "/+/cmd"}, new int[]{0, 0});
                log.info("MQTT conectado em {} (dispositivos virtuais prontos)", serverURI);
            } catch (MqttException e) {
                log.warn("Falha ao assinar tópicos de comando: {}", e.getMessage());
            }
        }

        @Override
        public void connectionLost(Throwable cause) {
            log.warn("Conexão MQTT perdida: {}", cause == null ? "?" : cause.getMessage());
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) {
            String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
            commandExecutor.submit(() -> handleCommand(topic, payload));
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
        }
    }

    @Override
    public void destroy() {
        connector.shutdownNow();
        commandExecutor.shutdownNow();
        try {
            if (client != null && client.isConnected()) client.disconnect();
            if (client != null) client.close();
        } catch (MqttException ignored) {
            // encerrando
        }
    }
}
