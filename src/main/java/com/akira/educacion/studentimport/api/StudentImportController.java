package com.akira.educacion.studentimport.api;

import com.akira.educacion.common.api.PageResponse;
import com.akira.educacion.institution.service.InstitutionScope;
import com.akira.educacion.studentimport.service.StudentImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/imports/students")
@RequiredArgsConstructor
@Tag(name = "Importación de estudiantes", description = "Carga, previsualización y confirmación de Excel")
public class StudentImportController {

    private final StudentImportService importService;
    private final InstitutionScope institutionScope;

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('STUDENT_IMPORT_WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Valida un Excel y devuelve la vista previa sin crear estudiantes")
    public StudentImportResponse preview(@RequestParam(required = false) UUID institutionId,
            @RequestPart("file") MultipartFile file) {
        return importService.preview(institutionScope.resolve(institutionId), file);
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAuthority('STUDENT_IMPORT_WRITE')")
    @Operation(summary = "Confirma la importación; es idempotente al reintentar el mismo lote")
    public StudentImportResponse confirm(@PathVariable UUID id,
            @RequestParam(required = false) UUID institutionId,
            @RequestBody(required = false) ConfirmStudentImportRequest request) {
        boolean allowUpdate = request != null && Boolean.TRUE.equals(request.allowUpdateExisting());
        return importService.confirm(institutionScope.resolve(institutionId), id, allowUpdate);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('STUDENT_IMPORT_READ')")
    @Operation(summary = "Lista los lotes de importación de la institución")
    public PageResponse<StudentImportSummaryResponse> list(
            @RequestParam(required = false) UUID institutionId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return importService.list(institutionScope.resolve(institutionId), pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('STUDENT_IMPORT_READ')")
    @Operation(summary = "Consulta el detalle y resultado de una importación")
    public StudentImportResponse get(@PathVariable UUID id,
            @RequestParam(required = false) UUID institutionId) {
        return importService.get(institutionScope.resolve(institutionId), id);
    }
}
