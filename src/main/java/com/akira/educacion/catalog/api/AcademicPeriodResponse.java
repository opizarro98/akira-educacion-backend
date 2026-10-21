package com.akira.educacion.catalog.api;

import java.time.LocalDate;
import java.util.UUID;

public record AcademicPeriodResponse(
        UUID id,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        boolean active) {
}
