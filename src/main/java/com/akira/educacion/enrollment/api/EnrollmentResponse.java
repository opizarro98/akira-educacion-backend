package com.akira.educacion.enrollment.api;

import com.akira.educacion.enrollment.domain.EnrollmentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record EnrollmentResponse(
        UUID id,
        UUID studentId,
        String studentName,
        UUID educationalLevelId,
        String educationalLevelName,
        UUID planId,
        String planName,
        UUID academicPeriodId,
        String academicPeriodName,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal agreedPrice,
        BigDecimal servicesTotal,
        BigDecimal subtotal,
        BigDecimal vatRate,
        BigDecimal vatAmount,
        BigDecimal totalAmount,
        EnrollmentStatus status,
        String cancellationReason,
        String notes,
        Instant confirmedAt,
        Instant cancelledAt,
        Instant completedAt,
        List<ServiceLine> services,
        Instant createdAt,
        Instant updatedAt) {

    public record ServiceLine(
            UUID id,
            UUID serviceId,
            String serviceName,
            int quantity,
            BigDecimal agreedPrice,
            BigDecimal lineTotal) {
    }
}
