package com.akira.educacion.finance.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Cabecera común de los reportes financieros.
 *
 * <p>
 * {@code accrued} es el total devengado (obligaciones emitidas en el período),
 * {@code collected} el dinero realmente cobrado y {@code difference} su
 * diferencia. Nunca se suman obligaciones como si fueran dinero recibido.
 */
public record ReportHeaderResponse(
        UUID institutionId,
        String institutionName,
        LocalDate fromDate,
        LocalDate toDate,
        String timezone,
        String currency,
        Instant generatedAt,
        boolean includesVoids,
        BigDecimal accrued,
        BigDecimal collected,
        BigDecimal difference) {
}
