package com.akira.educacion.finance.domain;

/**
 * Ciclo de vida de un egreso.
 *
 * <p>
 * {@link #DRAFT} es editable y no afecta la caja. {@link #CONFIRMED} genera una
 * salida de caja y no puede editarse. {@link #VOID} conserva el documento y, si
 * había sido confirmado, genera una reversión de caja.
 */
public enum ExpenseStatus {
    DRAFT,
    CONFIRMED,
    VOID
}
