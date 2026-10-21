package com.akira.educacion.students.api;

import com.akira.educacion.common.api.PageResponse;
import com.akira.educacion.institution.service.InstitutionScope;
import com.akira.educacion.students.domain.StudentStatus;
import com.akira.educacion.students.service.StudentRepresentativeService;
import com.akira.educacion.students.service.StudentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/students")
@RequiredArgsConstructor
@Tag(name = "Estudiantes", description = "Gestión de estudiantes y sus representantes")
public class StudentController {

    private final StudentService studentService;
    private final StudentRepresentativeService studentRepresentativeService;
    private final InstitutionScope institutionScope;

    @GetMapping
    @PreAuthorize("hasAuthority('STUDENT_READ')")
    @Operation(summary = "Lista estudiantes con búsqueda y filtro por estado")
    public PageResponse<StudentResponse> list(
            @RequestParam(required = false) UUID institutionId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) StudentStatus status,
            @PageableDefault(size = 20, sort = "lastName", direction = Sort.Direction.ASC) Pageable pageable) {
        return studentService.list(institutionScope.resolve(institutionId), search, status, pageable);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('STUDENT_WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea un estudiante")
    public StudentResponse create(@RequestParam(required = false) UUID institutionId,
            @Valid @RequestBody CreateStudentRequest request) {
        return studentService.create(institutionScope.resolve(institutionId), request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('STUDENT_READ')")
    @Operation(summary = "Consulta un estudiante")
    public StudentResponse get(@PathVariable UUID id, @RequestParam(required = false) UUID institutionId) {
        return studentService.get(institutionScope.resolve(institutionId), id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('STUDENT_WRITE')")
    @Operation(summary = "Edita un estudiante")
    public StudentResponse update(@PathVariable UUID id, @RequestParam(required = false) UUID institutionId,
            @Valid @RequestBody UpdateStudentRequest request) {
        return studentService.update(institutionScope.resolve(institutionId), id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('STUDENT_WRITE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Elimina un estudiante sin historial de matrículas")
    public void delete(@PathVariable UUID id, @RequestParam(required = false) UUID institutionId) {
        studentService.delete(institutionScope.resolve(institutionId), id);
    }

    @GetMapping("/{id}/representatives")
    @PreAuthorize("hasAuthority('REPRESENTATIVE_READ')")
    @Operation(summary = "Lista los representantes asociados al estudiante")
    public List<StudentRepresentativeResponse> representatives(@PathVariable UUID id,
            @RequestParam(required = false) UUID institutionId) {
        return studentRepresentativeService.list(institutionScope.resolve(institutionId), id);
    }

    @PostMapping("/{id}/representatives")
    @PreAuthorize("hasAuthority('REPRESENTATIVE_WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Asocia un representante existente o crea uno nuevo para el estudiante")
    public StudentRepresentativeResponse linkRepresentative(@PathVariable UUID id,
            @RequestParam(required = false) UUID institutionId,
            @Valid @RequestBody LinkRepresentativeRequest request) {
        return studentRepresentativeService.link(institutionScope.resolve(institutionId), id, request);
    }

    @PutMapping("/{id}/representatives/{representativeId}")
    @PreAuthorize("hasAuthority('REPRESENTATIVE_WRITE')")
    @Operation(summary = "Actualiza las responsabilidades del vínculo")
    public StudentRepresentativeResponse updateLink(@PathVariable UUID id, @PathVariable UUID representativeId,
            @RequestParam(required = false) UUID institutionId,
            @Valid @RequestBody LinkRepresentativeRequest request) {
        return studentRepresentativeService.updateLink(institutionScope.resolve(institutionId), id,
                representativeId, request);
    }

    @DeleteMapping("/{id}/representatives/{representativeId}")
    @PreAuthorize("hasAuthority('REPRESENTATIVE_WRITE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Quita el vínculo entre estudiante y representante")
    public void unlinkRepresentative(@PathVariable UUID id, @PathVariable UUID representativeId,
            @RequestParam(required = false) UUID institutionId) {
        studentRepresentativeService.unlink(institutionScope.resolve(institutionId), id, representativeId);
    }
}
