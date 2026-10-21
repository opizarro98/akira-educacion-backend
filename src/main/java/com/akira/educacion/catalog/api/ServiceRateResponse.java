package com.akira.educacion.catalog.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ServiceRateResponse(
        UUID id,
        UUID serviceId,
        String serviceName,
        BigDecimal amount,
        LocalDate validFrom,
        LocalDate validTo,
        boolean active) {
}
