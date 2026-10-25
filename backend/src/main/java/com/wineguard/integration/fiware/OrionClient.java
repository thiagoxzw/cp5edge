package com.wineguard.integration.fiware;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wineguard.config.WineGuardProperties;
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

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Cliente do Orion Context Broker (NGSI v2, porta 1026).
 * Endpoints usados (todos documentados na API NGSI v2):
 * <ul>
 *   <li>GET  /version</li>
 *   <li>GET  /v2/entities?type=..&amp;options=keyValues&amp;attrs=..   (valores atuais)</li>
 *   <li>PATCH /v2/entities/{id}/attrs?type=..                         (comandos)</li>
 *   <li>DELETE /v2/entities/{id}?type=..</li>
 *   <li>GET/POST /v2/subscriptions                                    (Orion -> STH-Comet)</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "wineguard.mode", havingValue = "fiware", matchIfMissing = true)
public class OrionClient {

    private static final String COMPONENT = "Orion";
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_OF_MAPS = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};

    private final RestClient client;
    private final WineGuardProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http;
    private final String baseUrl;
    private final Duration timeout;

    public OrionClient(@Qualifier("orionRestClient") RestClient client, WineGuardProperties props, ObjectMapper mapper) {
        this.client = client;
        this.props = props;
        this.mapper = mapper;
        this.baseUrl = props.fiware().orionUrl();
        this.timeout = Duration.ofMillis(props.fiware().timeoutMs());
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    public Map<String, Object> version() {
        try {
            return client.get().uri("/version").retrieve().body(MAP);
        } catch (RestClientException e) {
            throw new FiwareIntegrationException(COMPONENT, "indisponível (" + e.getMessage() + ")", e);
        }
    }

    /** Lista as entidades de um tipo no formato keyValues, incluindo o atributo built-in dateModified. */
    public List<Map<String, Object>> listEntities(String type, String attrs) {
        try {
            List<Map<String, Object>> body = client.get()
                    .uri(b -> b.path("/v2/entities")
                            .queryParam("type", type)
                            .queryParam("options", "keyValues")
                            .queryParam("attrs", attrs)
                            .queryParam("limit", 1000)
                            .build())
                    .retrieve()
                    .body(LIST_OF_MAPS);
            return body != null ? body : List.of();
        } catch (RestClientException e) {
            throw new FiwareIntegrationException(COMPONENT, "falha ao listar entidades (" + e.getMessage() + ")", e);
        }
    }

    /**
     * Envia um comando. O Orion encaminha ao IoT Agent (registro criado no provisionamento),
     * que publica no MQTT em /{apikey}/{device_id}/cmd o payload UltraLight "device@cmd|param".
     */
    public void sendCommand(String entityId, String entityType, String command, String param) {
        Map<String, Object> body = Map.of(command, Map.of("type", "command", "value", param == null ? "" : param));
        // PATCH via java.net.http: o HttpURLConnection (SimpleClientHttpRequestFactory) não suporta PATCH
        // ("Invalid HTTP method: PATCH"). Corpo em bytes garante Content-Length para o Orion.
        try {
            byte[] payload = mapper.writeValueAsBytes(body);
            String query = "?type=" + URLEncoder.encode(entityType, StandardCharsets.UTF_8);
            String path = "/v2/entities/" + URLEncoder.encode(entityId, StandardCharsets.UTF_8) + "/attrs" + query;
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(timeout)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .header("fiware-service", props.fiware().service())
                    .header("fiware-servicepath", props.fiware().servicePath())
                    .method("PATCH", HttpRequest.BodyPublishers.ofByteArray(payload))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new FiwareIntegrationException(COMPONENT,
                        "comando '" + command + "' rejeitado (" + response.statusCode() + "): " + response.body()
                                + " — o dispositivo está provisionado no IoT Agent?");
            }
        } catch (IOException e) {
            throw new FiwareIntegrationException(COMPONENT, "falha ao enviar comando (" + e.getMessage() + ")", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FiwareIntegrationException(COMPONENT, "envio de comando interrompido", e);
        }
    }

    public void deleteEntity(String entityId, String entityType) {
        try {
            client.delete()
                    .uri(b -> b.path("/v2/entities/{id}").queryParam("type", entityType).build(entityId))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() != HttpStatus.NOT_FOUND.value()) {
                throw new FiwareIntegrationException(COMPONENT, "falha ao remover entidade: " + e.getResponseBodyAsString(), e);
            }
        } catch (RestClientException e) {
            throw new FiwareIntegrationException(COMPONENT, "falha ao remover entidade (" + e.getMessage() + ")", e);
        }
    }

    public List<Map<String, Object>> listSubscriptions() {
        try {
            List<Map<String, Object>> body = client.get()
                    .uri(b -> b.path("/v2/subscriptions").queryParam("limit", 1000).build())
                    .retrieve()
                    .body(LIST_OF_MAPS);
            return body != null ? body : List.of();
        } catch (RestClientException e) {
            throw new FiwareIntegrationException(COMPONENT, "falha ao listar subscriptions (" + e.getMessage() + ")", e);
        }
    }

    /**
     * Envia o POST com corpo de tamanho fixo (Content-Length). O RestClient (Spring 6.2)
     * manda o corpo em chunked, e o Orion 3.x recusa com "ContentLengthRequired".
     */
    public void createSubscription(Map<String, Object> subscription) {
        try {
            byte[] body = mapper.writeValueAsBytes(subscription);
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/v2/subscriptions"))
                    .timeout(timeout)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .header("fiware-service", props.fiware().service())
                    .header("fiware-servicepath", props.fiware().servicePath())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new FiwareIntegrationException(COMPONENT, "subscription rejeitada: " + response.body());
            }
        } catch (IOException e) {
            throw new FiwareIntegrationException(COMPONENT, "falha ao criar subscription (" + e.getMessage() + ")", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FiwareIntegrationException(COMPONENT, "criação de subscription interrompida", e);
        }
    }
}
