package com.akira.educacion.representatives.api;

import com.akira.educacion.common.api.PageResponse;
import com.akira.educacion.institution.service.InstitutionScope;
import com.akira.educacion.representatives.service.RepresentativeService;
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
@RequestMapping("/api/v1/representatives")
@RequiredArgsConstructor
@Tag(name = "Representantes", description = "Representantes y estudiantes asociados")
public class RepresentativeController {

    private final RepresentativeService representativeService;
    private final InstitutionScope institutionScope;

    @GetMapping
    @PreAuthorize("hasAuthority('REPRESENTATIVE_READ')")
    @Operation(summary = "Lista representantes de la institución")
    public PageResponse<RepresentativeResponse> list(
            @RequestParam(required = false) UUID institutionId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "fullName", direction = Sort.Direction.ASC) Pageable pageable) {
        return representativeService.list(institutionScope.resolve(institutionId), search, pageable);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('REPRESENTATIVE_WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea un representante")
    public RepresentativeResponse create(@RequestParam(required = false) UUID institutionId,
            @Valid @RequestBody CreateRepresentativeRequest request) {
        return representativeService.create(institutionScope.resolve(institutionId), request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('REPRESENTATIVE_READ')")
    @Operation(summary = "Consulta un representante")
    public RepresentativeResponse get(@PathVariable UUID id, @RequestParam(required = false) UUID institutionId) {
        return representativeService.toResponse(
                representativeService.find(institutionScope.resolve(institutionId), id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('REPRESENTATIVE_WRITE')")
    @Operation(summary = "Edita un representante")
    public RepresentativeResponse update(@PathVariable UUID id, @RequestParam(required = false) UUID institutionId,
            @Valid @RequestBody UpdateRepresentativeRequest request) {
        return representativeService.toResponse(
                representativeService.update(institutionScope.resolve(institutionId), id, request));
    }

    @GetMapping("/{id}/students")
    @PreAuthorize("hasAuthority('REPRESENTATIVE_READ')")
    @Operation(summary = "Lista los estudiantes asociados al representante")
    public List<RepresentativeStudentResponse> students(@PathVariable UUID id,
            @RequestParam(required = false) UUID institutionId) {
        return representativeService.listStudents(institutionScope.resolve(institutionId), id);
    }
}
