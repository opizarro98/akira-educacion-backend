package com.akira.educacion.enrollment.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Datos para crear o editar una matrícula. El período lectivo es opcional; el
 * precio acordado se conserva aunque el catálogo cambie después.
 */
public record EnrollmentRequest(
        @NotNull(message = "El estudiante es obligatorio") UUID studentId,
        @NotNull(message = "El nivel educativo es obligatorio") UUID educationalLevelId,
        UUID planId,
        UUID academicPeriodId,
        @NotNull(message = "La fecha de inicio es obligatoria") LocalDate startDate,
        LocalDate endDate,
        @NotNull(message = "El precio acordado es obligatorio") @DecimalMin(value = "0.00", message = "El precio acordado no puede ser negativo") @Digits(integer = 10, fraction = 2) BigDecimal agreedPrice,
        @Size(max = 500) String notes,
        List<@Valid ServiceLineRequest> services) {

    public record ServiceLineRequest(
            @NotNull(message = "El servicio es obligatorio") UUID serviceId,
            @NotNull(message = "El precio acordado del servicio es obligatorio") @DecimalMin(value = "0.00", message = "El precio del servicio no puede ser negativo") @Digits(integer = 10, fraction = 2) BigDecimal agreedPrice,
            @Min(value = 1, message = "La cantidad debe ser mayor que cero") Integer quantity) {
    }
}
