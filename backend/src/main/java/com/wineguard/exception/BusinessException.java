package com.wineguard.exception;

import org.springframework.http.HttpStatus;

/** Violação de regra de negócio (ex.: identificador duplicado, limites inválidos). */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;

    public BusinessException(String message) {
        this(HttpStatus.BAD_REQUEST, message);
    }

    public BusinessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
