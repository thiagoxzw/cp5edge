package com.wineguard.integration.fiware;

import com.fasterxml.jackson.databind.JsonNode;
import com.wineguard.exception.FiwareIntegrationException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Cliente da API REST do STH-Comet (porta 8666).
 * <pre>
 * Raw:        GET /STH/v1/contextEntities/type/{type}/id/{id}/attributes/{attr}?hLimit=&amp;hOffset=&amp;dateFrom=&amp;dateTo=
 * Agregado:   GET /STH/v1/contextEntities/type/{type}/id/{id}/attributes/{attr}?aggrMethod=sum&amp;aggrPeriod=minute|hour&amp;dateFrom=&amp;dateTo=
 * </pre>
 * Headers obrigatórios: fiware-service e fiware-servicepath (já configurados no RestClient).
 */
@Component
@ConditionalOnProperty(name = "wineguard.mode", havingValue = "fiware", matchIfMissing = true)
public class SthCometClient {

    private static final String COMPONENT = "STH-Comet";
    private static final String PATH = "/STH/v1/contextEntities/type/{type}/id/{id}/attributes/{attr}";
    /** STH espera ISO 8601 com milissegundos, ex.: 2026-10-06T12:00:00.000Z */
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private final RestClient client;

    public SthCometClient(@Qualifier("sthRestClient") RestClient client) {
        this.client = client;
    }

    public Map<?, ?> version() {
        try {
            return client.get().uri("/version").retrieve().body(Map.class);
        } catch (RestClientException e) {
            throw new FiwareIntegrationException(COMPONENT, "indisponível (" + e.getMessage() + ")", e);
        }
    }

    /** Uma página de dados brutos (máx. hLimit valores, limitado pelo STH a 100 por padrão). */
    public JsonNode raw(String type, String entityId, String attr, Instant from, Instant to, int hLimit, int hOffset) {
        try {
            return client.get()
                    .uri(b -> b.path(PATH)
                            .queryParam("hLimit", hLimit)
                            .queryParam("hOffset", hOffset)
                            .queryParam("dateFrom", ISO.format(from))
                            .queryParam("dateTo", ISO.format(to))
                            .build(type, entityId, attr))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            throw new FiwareIntegrationException(COMPONENT, "falha na consulta raw (" + e.getMessage() + ")", e);
        }
    }

    /** Dados agregados: com aggrMethod=sum cada ponto traz "sum" e "samples" (média = sum/samples). */
    public JsonNode aggregated(String type, String entityId, String attr, String aggrPeriod, Instant from, Instant to) {
        try {
            return client.get()
                    .uri(b -> b.path(PATH)
                            .queryParam("aggrMethod", "sum")
                            .queryParam("aggrPeriod", aggrPeriod)
                            .queryParam("dateFrom", ISO.format(from))
                            .queryParam("dateTo", ISO.format(to))
                            .build(type, entityId, attr))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            throw new FiwareIntegrationException(COMPONENT, "falha na consulta agregada (" + e.getMessage() + ")", e);
        }
    }
}
