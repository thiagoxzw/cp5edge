package com.wineguard.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Um RestClient por componente FIWARE. Todos enviam os headers de multi-tenancy
 * {@code fiware-service} e {@code fiware-servicepath}, que precisam ser IDÊNTICOS
 * no IoT Agent, no Orion e no STH-Comet — caso contrário os dados "somem".
 */
@Configuration
@ConditionalOnProperty(name = "wineguard.mode", havingValue = "fiware", matchIfMissing = true)
public class HttpClientConfig {

    @Bean
    RestClient orionRestClient(WineGuardProperties props) {
        return fiwareClient(props, props.fiware().orionUrl());
    }

    @Bean
    RestClient iotAgentRestClient(WineGuardProperties props) {
        return fiwareClient(props, props.fiware().iotaUrl());
    }

    @Bean
    RestClient sthRestClient(WineGuardProperties props) {
        return fiwareClient(props, props.fiware().sthUrl());
    }

    private RestClient fiwareClient(WineGuardProperties props, String baseUrl) {
        // Usado para GET/DELETE no Orion e no STH e para POSTs no IoT Agent (que aceita corpo chunked).
        // As escritas que o Orion 3.x exige com Content-Length (POST de subscription e PATCH de comando)
        // saem pelo java.net.http em OrionClient: o HttpURLConnection por trás desta factory envia o corpo
        // em chunked e não suporta PATCH. (setBufferRequestBody está obsoleto desde o Spring 6.1 e foi
        // removido daqui: não mudava esse comportamento.)
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.fiware().timeoutMs());
        factory.setReadTimeout(props.fiware().timeoutMs());
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("fiware-service", props.fiware().service())
                .defaultHeader("fiware-servicepath", props.fiware().servicePath())
                .build();
    }
}
