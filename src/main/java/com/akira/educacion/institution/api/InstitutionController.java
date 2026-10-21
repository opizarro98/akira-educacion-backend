package com.akira.educacion.institution.api;

import com.akira.educacion.common.api.PageResponse;
import com.akira.educacion.institution.service.InstitutionService;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/institutions")
@RequiredArgsConstructor
@Tag(name = "Instituciones", description = "Administración y configuración institucional")
public class InstitutionController {

    private final InstitutionService institutionService;

    @GetMapping
    @PreAuthorize("hasAuthority('PLATFORM_INSTITUTION_MANAGE')")
    @Operation(summary = "Lista todas las instituciones (solo PLATFORM_ADMIN)")
    public PageResponse<InstitutionResponse> list(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "legalName", direction = Sort.Direction.ASC) Pageable pageable) {
        return institutionService.list(search, pageable);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PLATFORM_INSTITUTION_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea una institución y, opcionalmente, su administrador")
    public InstitutionResponse create(@Valid @RequestBody CreateInstitutionRequest request) {
        return institutionService.create(request);
    }

    @GetMapping("/me")
    @PreAuthorize("hasAuthority('INSTITUTION_READ')")
    @Operation(summary = "Devuelve la institución del usuario autenticado")
    public InstitutionResponse mine() {
        return institutionService.getMine();
    }

    @PutMapping("/me")
    @PreAuthorize("hasAuthority('INSTITUTION_WRITE')")
    @Operation(summary = "Edita la configuración de la institución del usuario autenticado")
    public InstitutionResponse updateMine(@Valid @RequestBody UpdateInstitutionRequest request) {
        return institutionService.update(institutionService.getMine().id(), request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('INSTITUTION_READ')")
    @Operation(summary = "Consulta una institución; el aislamiento se valida en el servicio")
    public InstitutionResponse getById(@PathVariable UUID id) {
        return institutionService.getById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PLATFORM_INSTITUTION_MANAGE')")
    @Operation(summary = "Edita cualquier institución (solo PLATFORM_ADMIN)")
    public InstitutionResponse update(@PathVariable UUID id,
            @Valid @RequestBody UpdateInstitutionRequest request) {
        return institutionService.update(id, request);
    }
}
