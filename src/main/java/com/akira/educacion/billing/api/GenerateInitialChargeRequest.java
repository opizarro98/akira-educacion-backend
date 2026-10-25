package com.akira.educacion.billing.api;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Generación del cobro inicial desde una matrícula confirmada. Las fechas son
 * opcionales; si faltan se usan la fecha actual y la configuración de cobro.
 */
public record GenerateInitialChargeRequest(
        UUID conceptId,
        LocalDate issueDate,
        LocalDate dueDate,
        String description) {
}
