package com.akira.educacion.billing.domain;

/**
 * Estado de un pago.
 *
 * <p>
 * Solo {@code CONFIRMED} representa dinero recibido y puede aplicarse a
 * obligaciones. Un pago anulado se conserva para auditoría.
 */
public enum PaymentStatus {
    PENDING_VERIFICATION,
    CONFIRMED,
    VOID
}
