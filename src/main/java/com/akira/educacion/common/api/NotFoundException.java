package com.akira.educacion.common.api;

/**
 * Recurso inexistente o no visible para el usuario autenticado.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
