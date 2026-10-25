package com.wineguard.exception;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    public static NotFoundException device(Long id) {
        return new NotFoundException("Dispositivo " + id + " não encontrado");
    }
}
