package com.akira.educacion.communication.api;

import com.akira.educacion.communication.service.MessageTemplateService;
import com.akira.educacion.institution.service.InstitutionScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/v1/communications/templates")
@RequiredArgsConstructor
@Tag(name = "Plantillas de mensajes", description = "Plantillas institucionales para comunicaciones asistidas")
public class MessageTemplateController {

    private final MessageTemplateService templateService;
    private final InstitutionScope institutionScope;

    @GetMapping
    @PreAuthorize("hasAuthority('MESSAGE_TEMPLATE_READ')")
    @Operation(summary = "Lista las plantillas de mensajes de la institución")
    public List<MessageTemplateResponse> list(@RequestParam(required = false) UUID institutionId,
            @RequestParam(required = false) Boolean active) {
        return templateService.list(institutionScope.resolve(institutionId), active);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MESSAGE_TEMPLATE_READ')")
    @Operation(summary = "Consulta una plantilla de mensaje")
    public MessageTemplateResponse get(@PathVariable UUID id,
            @RequestParam(required = false) UUID institutionId) {
        return templateService.get(institutionScope.resolve(institutionId), id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MESSAGE_TEMPLATE_WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea una plantilla de mensaje")
    public MessageTemplateResponse create(@RequestParam(required = false) UUID institutionId,
            @Valid @RequestBody MessageTemplateRequest request) {
        return templateService.create(institutionScope.resolve(institutionId), request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MESSAGE_TEMPLATE_WRITE')")
    @Operation(summary = "Actualiza o desactiva una plantilla de mensaje")
    public MessageTemplateResponse update(@PathVariable UUID id,
            @RequestParam(required = false) UUID institutionId,
            @Valid @RequestBody MessageTemplateRequest request) {
        return templateService.update(institutionScope.resolve(institutionId), id, request);
    }
}
