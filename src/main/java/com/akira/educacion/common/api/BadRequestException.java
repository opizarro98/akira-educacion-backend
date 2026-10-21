package com.akira.educacion.common.api;

/**
 * Petición sintácticamente válida pero con reglas de negocio incumplidas.
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
