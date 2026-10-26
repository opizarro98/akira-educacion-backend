package com.akira.educacion.studentimport.domain;

/**
 * Estado del lote de importación. La previsualización no crea estudiantes.
 */
public enum StudentImportStatus {
    PREVIEWED,
    IMPORTED,
    FAILED
}
