package com.akira.educacion.studentimport.api;

/**
 * Opción explícita para actualizar estudiantes existentes que coincidan con
 * una fila del archivo. Sin este permiso del usuario, las coincidencias se
 * omiten.
 */
public record ConfirmStudentImportRequest(Boolean allowUpdateExisting) {
}
