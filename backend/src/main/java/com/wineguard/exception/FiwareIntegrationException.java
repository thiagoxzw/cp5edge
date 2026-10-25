package com.wineguard.exception;

/** Falha de comunicação com Orion, IoT Agent ou STH-Comet. Mapeada para HTTP 502. */
public class FiwareIntegrationException extends RuntimeException {

    private final String component;

    public FiwareIntegrationException(String component, String message, Throwable cause) {
        super(component + ": " + message, cause);
        this.component = component;
    }

    public FiwareIntegrationException(String component, String message) {
        this(component, message, null);
    }

    public String getComponent() {
        return component;
    }
}
