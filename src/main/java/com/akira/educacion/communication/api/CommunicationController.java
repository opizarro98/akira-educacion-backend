package com.akira.educacion.communication.api;

import com.akira.educacion.common.api.PageResponse;
import com.akira.educacion.communication.domain.CommunicationStatus;
import com.akira.educacion.communication.service.CommunicationService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/communications")
@RequiredArgsConstructor
@Tag(name = "Comunicaciones", description = "Comunicaciones asistidas mediante enlaces wa.me")
public class CommunicationController {

    private final CommunicationService communicationService;
    private final InstitutionScope institutionScope;

    @GetMapping
    @PreAuthorize("hasAuthority('COMMUNICATION_READ')")
    @Operation(summary = "Lista comunicaciones con filtros por estado y estudiante")
    public PageResponse<CommunicationResponse> list(
            @RequestParam(required = false) UUID institutionId,
            @RequestParam(required = false) CommunicationStatus status,
            @RequestParam(required = false) UUID studentId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return communicationService.list(institutionScope.resolve(institutionId), status, studentId, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('COMMUNICATION_READ')")
    @Operation(summary = "Consulta una comunicación registrada")
    public CommunicationResponse get(@PathVariable UUID id,
            @RequestParam(required = false) UUID institutionId) {
        return communicationService.get(institutionScope.resolve(institutionId), id);
    }

    @PostMapping("/preview")
    @PreAuthorize("hasAuthority('COMMUNICATION_WRITE')")
    @Operation(summary = "Genera la vista previa y el enlace wa.me sin persistir nada")
    public CommunicationPreviewResponse preview(@RequestParam(required = false) UUID institutionId,
            @Valid @RequestBody CommunicationPreviewRequest request) {
        return communicationService.preview(institutionScope.resolve(institutionId), request);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('COMMUNICATION_WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra una comunicación preparada o un borrador")
    public CommunicationResponse create(@RequestParam(required = false) UUID institutionId,
            @Valid @RequestBody CommunicationPrepareRequest request) {
        return communicationService.create(institutionScope.resolve(institutionId), request);
    }

    @PostMapping("/{id}/opened")
    @PreAuthorize("hasAuthority('COMMUNICATION_WRITE')")
    @Operation(summary = "Marca que el usuario abrió el enlace; no confirma el envío")
    public CommunicationResponse markOpened(@PathVariable UUID id,
            @RequestParam(required = false) UUID institutionId) {
        return communicationService.markOpened(institutionScope.resolve(institutionId), id);
    }

    @PostMapping("/{id}/failed")
    @PreAuthorize("hasAuthority('COMMUNICATION_WRITE')")
    @Operation(summary = "Registra que la comunicación no pudo abrirse o completarse")
    public CommunicationResponse markFailed(@PathVariable UUID id,
            @RequestParam(required = false) UUID institutionId,
            @Valid @RequestBody FailedCommunicationRequest request) {
        return communicationService.markFailed(institutionScope.resolve(institutionId), id, request.reason());
    }
}
