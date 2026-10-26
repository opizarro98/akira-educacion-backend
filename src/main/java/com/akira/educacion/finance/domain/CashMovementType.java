package com.akira.educacion.finance.domain;

import java.math.BigDecimal;

/**
 * Tipos de movimiento de caja derivados de operaciones confirmadas.
 *
 * <p>
 * El movimiento es un registro derivado y auditable: no se crea a mano. Cada
 * operación genera como máximo un movimiento del tipo que corresponde.
 */
public enum CashMovementType {
    /** Entrada por un pago confirmado. */
    PAYMENT_IN(true),
    /** Salida por un egreso confirmado. */
    EXPENSE_OUT(false),
    /** Reversión negativa de una entrada al anular un pago confirmado. */
    PAYMENT_VOID_REVERSAL(false),
    /** Reversión positiva de una salida al anular un egreso confirmado. */
    EXPENSE_VOID_REVERSAL(true);

    private final boolean inflow;

    CashMovementType(boolean inflow) {
        this.inflow = inflow;
    }

    public boolean isInflow() {
        return inflow;
    }

    /** Importe con signo para sumar saldos sin ambigüedad. */
    public BigDecimal signed(BigDecimal amount) {
        return inflow ? amount : amount.negate();
    }
}
