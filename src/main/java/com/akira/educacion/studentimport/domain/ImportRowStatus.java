package com.akira.educacion.studentimport.domain;

/**
 * Resultado de validación de una fila del archivo.
 */
public enum ImportRowStatus {
    VALID,
    ERROR,
    DUPLICATE_FILE,
    DUPLICATE_EXISTING
}
