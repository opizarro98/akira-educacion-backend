package com.akira.educacion.billing.domain;

/**
 * Medios de pago iniciales. El modelo queda preparado para incorporar nuevos
 * medios con su propia validación y verificación.
 */
public enum PaymentMethod {
    CASH,
    BANK_TRANSFER
}
