package com.akira.educacion.enrollment.service;

import com.akira.educacion.audit.AuditService;
import com.akira.educacion.catalog.domain.AcademicPeriod;
import com.akira.educacion.catalog.domain.CatalogServiceItem;
import com.akira.educacion.catalog.domain.EducationalLevel;
import com.akira.educacion.catalog.domain.Plan;
import com.akira.educacion.catalog.service.CatalogService;
import com.akira.educacion.common.api.BadRequestException;
import com.akira.educacion.common.api.ConflictException;
import com.akira.educacion.common.api.NotFoundException;
import com.akira.educacion.common.api.PageResponse;
import com.akira.educacion.common.security.CurrentUser;
import com.akira.educacion.enrollment.api.EnrollmentHistoryResponse;
import com.akira.educacion.enrollment.api.EnrollmentRequest;
import com.akira.educacion.enrollment.api.EnrollmentResponse;
import com.akira.educacion.enrollment.domain.Enrollment;
import com.akira.educacion.enrollment.domain.EnrollmentHistory;
import com.akira.educacion.enrollment.domain.EnrollmentServiceLine;
import com.akira.educacion.enrollment.domain.EnrollmentStatus;
import com.akira.educacion.enrollment.repository.EnrollmentHistoryRepository;
import com.akira.educacion.enrollment.repository.EnrollmentRepository;
import com.akira.educacion.institution.domain.Institution;
import com.akira.educacion.students.domain.Student;
import com.akira.educacion.students.service.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Matrículas. Solo las matrículas en borrador son editables; confirmar congela
 * el precio acordado y la tasa de IVA vigente como información histórica.
 *
 * <p>
 * No se generan obligaciones ni pagos: {@link InitialChargeGenerator} queda
 * como
 * punto de extensión deliberadamente sin reglas financieras.
 */
@Service
@RequiredArgsConstructor
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentHistoryRepository historyRepository;
    private final StudentService studentService;
    private final CatalogService catalogService;
    private final CurrentUser currentUser;
    private final AuditService auditService;

    @Transactional
    public EnrollmentResponse create(UUID institutionId, EnrollmentRequest request) {
        Student student = studentService.find(institutionId, request.studentId());
        EducationalLevel level = catalogService.findLevel(institutionId, request.educationalLevelId());
        Plan plan = request.planId() == null ? null : catalogService.findPlan(institutionId, request.planId());
        AcademicPeriod period = request.academicPeriodId() == null
                ? null
                : catalogService.findPeriod(institutionId, request.academicPeriodId());
        validateDates(request.startDate(), request.endDate());

        Enrollment enrollment = new Enrollment();
        enrollment.setInstitution(student.getInstitution());
        enrollment.setStudent(student);
        enrollment.setEducationalLevel(level);
        enrollment.setPlan(plan);
        enrollment.setAcademicPeriod(period);
        enrollment.setStartDate(request.startDate());
        enrollment.setEndDate(request.endDate());
        enrollment.setAgreedPrice(money(request.agreedPrice()));
        enrollment.setNotes(trimToNull(request.notes()));
        enrollment.setStatus(EnrollmentStatus.DRAFT);
        applyServices(enrollment, request.services(), institutionId);
        recalculate(enrollment);

        Enrollment saved = enrollmentRepository.save(enrollment);
        recordHistory(saved, "CREATED", null, EnrollmentStatus.DRAFT, null);
        auditService.record("ENROLLMENT_CREATED", "Enrollment", saved.getId(), institutionId,
                "studentId=" + student.getId() + "; status=" + saved.getStatus());
        return toResponse(saved);
    }

    /**
     * Edita una matrícula únicamente mientras está en estado DRAFT.
     */
    @Transactional
    public EnrollmentResponse update(UUID institutionId, UUID id, EnrollmentRequest request) {
        Enrollment enrollment = find(institutionId, id);
        requireDraft(enrollment, "Una matrícula confirmada no puede editarse libremente");

        EnrollmentStatus previousStatus = enrollment.getStatus();
        Student student = studentService.find(institutionId, request.studentId());
        EducationalLevel level = catalogService.findLevel(institutionId, request.educationalLevelId());
        Plan plan = request.planId() == null ? null : catalogService.findPlan(institutionId, request.planId());
        AcademicPeriod period = request.academicPeriodId() == null
                ? null
                : catalogService.findPeriod(institutionId, request.academicPeriodId());
        validateDates(request.startDate(), request.endDate());

        List<String> changed = new ArrayList<>();
        if (!enrollment.getStudent().getId().equals(student.getId())) {
            changed.add("student");
        }
        if (!enrollment.getEducationalLevel().getId().equals(level.getId())) {
            changed.add("educationalLevel");
        }
        changed.add("prices");

        enrollment.setStudent(student);
        enrollment.setEducationalLevel(level);
        enrollment.setPlan(plan);
        enrollment.setAcademicPeriod(period);
        enrollment.setStartDate(request.startDate());
        enrollment.setEndDate(request.endDate());
        enrollment.setAgreedPrice(money(request.agreedPrice()));
        enrollment.setNotes(trimToNull(request.notes()));
        applyServices(enrollment, request.services(), institutionId);
        recalculate(enrollment);

        Enrollment saved = enrollmentRepository.save(enrollment);
        recordHistory(saved, "UPDATED", previousStatus, saved.getStatus(), "fields=" + String.join(",", changed));
        auditService.record("ENROLLMENT_UPDATED", "Enrollment", saved.getId(), institutionId,
                "fields=" + String.join(",", changed));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public EnrollmentResponse get(UUID institutionId, UUID id) {
        return toResponse(find(institutionId, id));
    }

    @Transactional(readOnly = true)
    public PageResponse<EnrollmentResponse> list(UUID institutionId, EnrollmentStatus status, UUID studentId,
            UUID academicPeriodId, Pageable pageable) {
        Page<Enrollment> page = enrollmentRepository.search(institutionId,
                status != null, status == null ? "%" : status.name(),
                studentId != null, studentId,
                academicPeriodId != null, academicPeriodId,
                pageable);
        return PageResponse.from(page.map(this::toResponse));
    }

    /**
     * Confirma la matrícula: congela el IVA vigente de la institución y deja de
     * ser editable.
     */
    @Transactional
    public EnrollmentResponse confirm(UUID institutionId, UUID id) {
        Enrollment enrollment = find(institutionId, id);
        requireDraft(enrollment, "Solo una matrícula en borrador puede confirmarse");

        BigDecimal subtotal = subtotal(enrollment);
        BigDecimal vatRate = currentVatRate(enrollment.getInstitution());
        BigDecimal vatAmount = subtotal.multiply(vatRate)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        enrollment.setVatRate(vatRate);
        enrollment.setVatAmount(vatAmount);
        enrollment.setTotalAmount(subtotal.add(vatAmount));
        enrollment.setStatus(EnrollmentStatus.CONFIRMED);
        enrollment.setConfirmedAt(Instant.now());

        Enrollment saved = enrollmentRepository.save(enrollment);
        recordHistory(saved, "CONFIRMED", EnrollmentStatus.DRAFT, EnrollmentStatus.CONFIRMED,
                "vatRate=" + vatRate);
        auditService.record("ENROLLMENT_CONFIRMED", "Enrollment", saved.getId(), institutionId,
                "subtotal=" + subtotal + "; vatRate=" + vatRate + "; total=" + saved.getTotalAmount());
        return toResponse(saved);
    }

    @Transactional
    public EnrollmentResponse cancel(UUID institutionId, UUID id, String reason) {
        Enrollment enrollment = find(institutionId, id);
        if (enrollment.getStatus() == EnrollmentStatus.CANCELLED) {
            throw new ConflictException("La matrícula ya está cancelada");
        }
        if (enrollment.getStatus() == EnrollmentStatus.COMPLETED) {
            throw new ConflictException("Una matrícula completada no puede cancelarse");
        }
        EnrollmentStatus previous = enrollment.getStatus();
        enrollment.setStatus(EnrollmentStatus.CANCELLED);
        enrollment.setCancellationReason(reason.trim());
        enrollment.setCancelledAt(Instant.now());

        Enrollment saved = enrollmentRepository.save(enrollment);
        recordHistory(saved, "CANCELLED", previous, EnrollmentStatus.CANCELLED, reason.trim());
        auditService.record("ENROLLMENT_CANCELLED", "Enrollment", saved.getId(), institutionId,
                "reason=" + reason.trim());
        return toResponse(saved);
    }

    @Transactional
    public EnrollmentResponse complete(UUID institutionId, UUID id) {
        Enrollment enrollment = find(institutionId, id);
        if (enrollment.getStatus() != EnrollmentStatus.CONFIRMED) {
            throw new ConflictException("Solo una matrícula confirmada puede marcarse como completada");
        }
        enrollment.setStatus(EnrollmentStatus.COMPLETED);
        enrollment.setCompletedAt(Instant.now());

        Enrollment saved = enrollmentRepository.save(enrollment);
        recordHistory(saved, "COMPLETED", EnrollmentStatus.CONFIRMED, EnrollmentStatus.COMPLETED, null);
        auditService.record("ENROLLMENT_COMPLETED", "Enrollment", saved.getId(), institutionId, null);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<EnrollmentHistoryResponse> history(UUID institutionId, UUID id) {
        find(institutionId, id);
        return historyRepository.findByInstitutionIdAndEnrollmentIdOrderByOccurredAtAsc(institutionId, id).stream()
                .map(entry -> new EnrollmentHistoryResponse(entry.getId(), entry.getAction(), entry.getFromStatus(),
                        entry.getToStatus(), entry.getReason(), entry.getActorEmail(), entry.getOccurredAt()))
                .toList();
    }

    // ------------------------------------------------------------------
    // Apoyo interno
    // ------------------------------------------------------------------

    private Enrollment find(UUID institutionId, UUID id) {
        return enrollmentRepository.findByIdAndInstitutionId(id, institutionId)
                .orElseThrow(() -> new NotFoundException("La matrícula no existe en la institución"));
    }

    private void requireDraft(Enrollment enrollment, String message) {
        if (!enrollment.isEditable()) {
            throw new ConflictException(message + " (estado actual: " + enrollment.getStatus() + ")");
        }
    }

    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new BadRequestException("La fecha de finalización no puede ser anterior a la de inicio");
        }
    }

    private void applyServices(Enrollment enrollment, List<EnrollmentRequest.ServiceLineRequest> requested,
            UUID institutionId) {
        Map<UUID, EnrollmentRequest.ServiceLineRequest> desired = new LinkedHashMap<>();
        if (requested != null) {
            for (EnrollmentRequest.ServiceLineRequest line : requested) {
                if (desired.putIfAbsent(line.serviceId(), line) != null) {
                    throw new BadRequestException("El servicio está repetido en la matrícula");
                }
            }
        }

        enrollment.getServices().removeIf(line -> !desired.containsKey(line.getService().getId()));

        Map<UUID, EnrollmentServiceLine> existing = new LinkedHashMap<>();
        enrollment.getServices().forEach(line -> existing.put(line.getService().getId(), line));

        for (Map.Entry<UUID, EnrollmentRequest.ServiceLineRequest> entry : desired.entrySet()) {
            EnrollmentRequest.ServiceLineRequest request = entry.getValue();
            EnrollmentServiceLine line = existing.get(entry.getKey());
            if (line == null) {
                CatalogServiceItem service = catalogService.findService(institutionId, entry.getKey());
                line = new EnrollmentServiceLine();
                line.setInstitution(enrollment.getInstitution());
                line.setEnrollment(enrollment);
                line.setService(service);
                enrollment.getServices().add(line);
            }
            line.setAgreedPrice(money(request.agreedPrice()));
            line.setQuantity(request.quantity() == null ? 1 : request.quantity());
        }
    }

    private void recalculate(Enrollment enrollment) {
        BigDecimal subtotal = subtotal(enrollment);
        enrollment.setVatRate(null);
        enrollment.setVatAmount(null);
        enrollment.setTotalAmount(subtotal);
    }

    private BigDecimal subtotal(Enrollment enrollment) {
        BigDecimal total = enrollment.getAgreedPrice() == null ? BigDecimal.ZERO : enrollment.getAgreedPrice();
        for (EnrollmentServiceLine line : enrollment.getServices()) {
            total = total.add(line.lineTotal());
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Tasa vigente según la configuración institucional. Si el IVA está
     * deshabilitado o su vigencia aún no comienza, la tasa aplicable es 0.
     */
    private BigDecimal currentVatRate(Institution institution) {
        if (!institution.isVatEnabled() || institution.getVatRate() == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        LocalDate effectiveFrom = institution.getVatEffectiveFrom();
        if (effectiveFrom != null && effectiveFrom.isAfter(LocalDate.now())) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return institution.getVatRate().setScale(2, RoundingMode.HALF_UP);
    }

    private void recordHistory(Enrollment enrollment, String action, EnrollmentStatus from, EnrollmentStatus to,
            String reason) {
        EnrollmentHistory history = new EnrollmentHistory();
        history.setInstitutionId(enrollment.getInstitution().getId());
        history.setEnrollmentId(enrollment.getId());
        history.setAction(action);
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setReason(reason);
        history.setActorEmail(actorEmail());
        history.setOccurredAt(Instant.now());
        historyRepository.save(history);
    }

    private String actorEmail() {
        try {
            return currentUser.email();
        } catch (RuntimeException ex) {
            return "system";
        }
    }

    private EnrollmentResponse toResponse(Enrollment enrollment) {
        List<EnrollmentResponse.ServiceLine> lines = enrollment.getServices().stream()
                .map(line -> new EnrollmentResponse.ServiceLine(line.getId(), line.getService().getId(),
                        line.getService().getName(), line.getQuantity(), line.getAgreedPrice(), line.lineTotal()))
                .toList();
        BigDecimal servicesTotal = lines.stream()
                .map(EnrollmentResponse.ServiceLine::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        return new EnrollmentResponse(
                enrollment.getId(),
                enrollment.getStudent().getId(),
                enrollment.getStudent().fullName(),
                enrollment.getEducationalLevel().getId(),
                enrollment.getEducationalLevel().getName(),
                enrollment.getPlan() == null ? null : enrollment.getPlan().getId(),
                enrollment.getPlan() == null ? null : enrollment.getPlan().getName(),
                enrollment.getAcademicPeriod() == null ? null : enrollment.getAcademicPeriod().getId(),
                enrollment.getAcademicPeriod() == null ? null : enrollment.getAcademicPeriod().getName(),
                enrollment.getStartDate(),
                enrollment.getEndDate(),
                enrollment.getAgreedPrice(),
                servicesTotal,
                subtotal(enrollment),
                enrollment.getVatRate(),
                enrollment.getVatAmount(),
                enrollment.getTotalAmount(),
                enrollment.getStatus(),
                enrollment.getCancellationReason(),
                enrollment.getNotes(),
                enrollment.getConfirmedAt(),
                enrollment.getCancelledAt(),
                enrollment.getCompletedAt(),
                lines,
                enrollment.getCreatedAt(),
                enrollment.getUpdatedAt());
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
