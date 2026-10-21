package com.akira.educacion.catalog.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record AcademicPeriodRequest(
        @NotBlank(message = "El nombre del período es obligatorio") @Size(max = 120) String name,
        @NotNull(message = "La fecha de inicio es obligatoria") LocalDate startDate,
        @NotNull(message = "La fecha de fin es obligatoria") LocalDate endDate,
        Boolean active) {
}
