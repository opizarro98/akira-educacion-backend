package com.akira.educacion.enrollment.api;

import com.akira.educacion.common.api.PageResponse;
import com.akira.educacion.enrollment.domain.EnrollmentStatus;
import com.akira.educacion.enrollment.service.EnrollmentService;
import com.akira.educacion.institution.service.InstitutionScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/enrollments")
@RequiredArgsConstructor
@Tag(name = "Matrículas", description = "Matrículas, confirmación, cancelación e historial")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;
    private final InstitutionScope institutionScope;

    @GetMapping
    @PreAuthorize("hasAuthority('ENROLLMENT_READ')")
    @Operation(summary = "Lista matrículas con filtros por estado, estudiante y período")
    public PageResponse<EnrollmentResponse> list(
            @RequestParam(required = false) UUID institutionId,
            @RequestParam(required = false) EnrollmentStatus status,
            @RequestParam(required = false) UUID studentId,
            @RequestParam(required = false) UUID academicPeriodId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return enrollmentService.list(institutionScope.resolve(institutionId), status, studentId,
                academicPeriodId, pageable);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ENROLLMENT_WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea una matrícula en estado DRAFT")
    public EnrollmentResponse create(@RequestParam(required = false) UUID institutionId,
            @Valid @RequestBody EnrollmentRequest request) {
        return enrollmentService.create(institutionScope.resolve(institutionId), request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ENROLLMENT_READ')")
    @Operation(summary = "Consulta una matrícula")
    public EnrollmentResponse get(@PathVariable UUID id, @RequestParam(required = false) UUID institutionId) {
        return enrollmentService.get(institutionScope.resolve(institutionId), id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ENROLLMENT_WRITE')")
    @Operation(summary = "Edita una matrícula en borrador")
    public EnrollmentResponse update(@PathVariable UUID id, @RequestParam(required = false) UUID institutionId,
            @Valid @RequestBody EnrollmentRequest request) {
        return enrollmentService.update(institutionScope.resolve(institutionId), id, request);
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAuthority('ENROLLMENT_WRITE')")
    @Operation(summary = "Confirma la matrícula y congela el IVA vigente")
    public EnrollmentResponse confirm(@PathVariable UUID id, @RequestParam(required = false) UUID institutionId) {
        return enrollmentService.confirm(institutionScope.resolve(institutionId), id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('ENROLLMENT_WRITE')")
    @Operation(summary = "Cancela la matrícula indicando el motivo")
    public EnrollmentResponse cancel(@PathVariable UUID id, @RequestParam(required = false) UUID institutionId,
            @Valid @RequestBody CancelEnrollmentRequest request) {
        return enrollmentService.cancel(institutionScope.resolve(institutionId), id, request.reason());
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('ENROLLMENT_WRITE')")
    @Operation(summary = "Marca como completada una matrícula confirmada")
    public EnrollmentResponse complete(@PathVariable UUID id, @RequestParam(required = false) UUID institutionId) {
        return enrollmentService.complete(institutionScope.resolve(institutionId), id);
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAuthority('ENROLLMENT_READ')")
    @Operation(summary = "Historial de cambios de la matrícula")
    public List<EnrollmentHistoryResponse> history(@PathVariable UUID id,
            @RequestParam(required = false) UUID institutionId) {
        return enrollmentService.history(institutionScope.resolve(institutionId), id);
    }
}
