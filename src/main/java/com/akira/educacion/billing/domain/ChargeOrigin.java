package com.akira.educacion.billing.domain;

/**
 * Origen de la obligación. Permite distinguir obligaciones manuales, el cobro
 * inicial de una matrícula y la generación mensual asistida.
 */
public enum ChargeOrigin {
    MANUAL,
    ENROLLMENT,
    MONTHLY
}
