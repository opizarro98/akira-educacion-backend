package com.akira.educacion.enrollment.domain;

/**
 * Estados de una matrícula. Solo {@link #DRAFT} es editable libremente.
 */
public enum EnrollmentStatus {
    DRAFT,
    CONFIRMED,
    CANCELLED,
    COMPLETED
}
