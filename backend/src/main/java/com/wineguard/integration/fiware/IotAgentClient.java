package com.wineguard.integration.fiware;

import com.wineguard.exception.FiwareIntegrationException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * Cliente da API de provisionamento (north port 4041) do IoT Agent UltraLight 2.0.
 * <ul>
 *   <li>GET    /iot/about</li>
 *   <li>POST   /iot/services   (service group / apikey)</li>
 *   <li>POST   /iot/devices    (provisionamento de dispositivo + comandos)</li>
 *   <li>DELETE /iot/devices/{device_id}</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "wineguard.mode", havingValue = "fiware", matchIfMissing = true)
public class IotAgentClient {

    private static final String COMPONENT = "IoT Agent";
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};

    /** Resultado de um POST de provisionamento. */
    public enum CreateResult { CREATED, ALREADY_EXISTS }

    private final RestClient client;

    public IotAgentClient(@Qualifier("iotAgentRestClient") RestClient client) {
        this.client = client;
    }

    public Map<String, Object> about() {
        try {
            return client.get().uri("/iot/about").retrieve().body(MAP);
        } catch (RestClientException e) {
            throw new FiwareIntegrationException(COMPONENT, "indisponível (" + e.getMessage() + ")", e);
        }
    }

    public CreateResult createServiceGroup(Map<String, Object> body) {
        return post("/iot/services", body, "service group");
    }

    public CreateResult createDevice(Map<String, Object> body) {
        return post("/iot/devices", body, "dispositivo");
    }

    public void deleteDevice(String deviceId) {
        try {
            client.delete().uri("/iot/devices/{id}", deviceId).retrieve().toBodilessEntity();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() != HttpStatus.NOT_FOUND.value()) {
                throw new FiwareIntegrationException(COMPONENT, "falha ao remover dispositivo: " + e.getResponseBodyAsString(), e);
            }
        } catch (RestClientException e) {
            throw new FiwareIntegrationException(COMPONENT, "falha ao remover dispositivo (" + e.getMessage() + ")", e);
        }
    }

    private CreateResult post(String path, Map<String, Object> body, String what) {
        try {
            client.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity();
            return CreateResult.CREATED;
        } catch (HttpClientErrorException e) {
            // 409 = DUPLICATE_GROUP / DUPLICATE_DEVICE_ID: já existe, o que é aceitável (idempotente)
            if (e.getStatusCode().value() == HttpStatus.CONFLICT.value()) {
                return CreateResult.ALREADY_EXISTS;
            }
            throw new FiwareIntegrationException(COMPONENT,
                    what + " rejeitado (" + e.getStatusCode().value() + "): " + e.getResponseBodyAsString(), e);
        } catch (RestClientException e) {
            throw new FiwareIntegrationException(COMPONENT, "falha ao criar " + what + " (" + e.getMessage() + ")", e);
        }
    }
}
