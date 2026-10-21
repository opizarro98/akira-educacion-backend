package com.akira.educacion.common.api;

/**
 * Conflicto con el estado actual del recurso (por ejemplo, un correo
 * duplicado).
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
