package com.akira.educacion.billing.domain;

/**
 * Estado de una obligación de cobro.
 *
 * <p>
 * {@code PARTIAL} y {@code PAID} quedan preparados para la Fase 3B, cuando se
 * registren pagos. En esta fase las obligaciones nacen en {@code PENDING} y
 * pueden anularse pasando a {@code VOID}.
 */
public enum ChargeStatus {
    PENDING,
    PARTIAL,
    PAID,
    VOID
}
