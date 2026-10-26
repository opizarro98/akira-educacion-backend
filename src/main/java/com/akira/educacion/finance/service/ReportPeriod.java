package com.akira.educacion.finance.service;

import com.akira.educacion.common.api.BadRequestException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Período de un reporte expresado en la zona horaria de la institución.
 *
 * <p>
 * Los documentos con fecha de negocio ({@code DATE}) se filtran por fecha. Los
 * movimientos de caja guardados como {@code TIMESTAMPTZ} se filtran con un
 * rango semiabierto {@code [inicio del día, inicio del día siguiente)} en la
 * zona de la institución, de modo que el límite no dependa de UTC ni del huso
 * del servidor.
 */
public record ReportPeriod(LocalDate fromDate, LocalDate toDate, ZoneId zone, Instant startInclusive,
        Instant endExclusive) {

    public static final String DEFAULT_TIMEZONE = "America/Guayaquil";

    public static ZoneId zoneOf(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return ZoneId.of(DEFAULT_TIMEZONE);
        }
        try {
            return ZoneId.of(timezone);
        } catch (RuntimeException ex) {
            return ZoneId.of(DEFAULT_TIMEZONE);
        }
    }

    /**
     * Resuelve el período. Sin fechas, usa el mes en curso de la institución.
     */
    public static ReportPeriod resolve(String timezone, LocalDate from, LocalDate to) {
        ZoneId zone = zoneOf(timezone);
        LocalDate today = LocalDate.now(zone);
        LocalDate fromDate = from != null ? from : today.withDayOfMonth(1);
        LocalDate toDate = to != null ? to : today;
        if (fromDate.isAfter(toDate)) {
            throw new BadRequestException("La fecha inicial no puede ser posterior a la fecha final");
        }
        return new ReportPeriod(fromDate, toDate, zone,
                fromDate.atStartOfDay(zone).toInstant(),
                toDate.plusDays(1).atStartOfDay(zone).toInstant());
    }
}
