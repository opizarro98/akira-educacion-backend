package com.akira.educacion.communication.service;

import com.akira.educacion.audit.AuditService;
import com.akira.educacion.common.api.ConflictException;
import com.akira.educacion.common.api.NotFoundException;
import com.akira.educacion.communication.api.MessageTemplateRequest;
import com.akira.educacion.communication.api.MessageTemplateResponse;
import com.akira.educacion.communication.domain.MessageTemplate;
import com.akira.educacion.communication.repository.MessageTemplateRepository;
import com.akira.educacion.institution.domain.Institution;
import com.akira.educacion.institution.repository.InstitutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MessageTemplateService {

    private final MessageTemplateRepository repository;
    private final InstitutionRepository institutionRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<MessageTemplateResponse> list(UUID institutionId, Boolean active) {
        return repository.search(institutionId, active != null, active != null && active).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public MessageTemplateResponse get(UUID institutionId, UUID id) {
        return toResponse(find(institutionId, id));
    }

    @Transactional
    public MessageTemplateResponse create(UUID institutionId, MessageTemplateRequest request) {
        Institution institution = loadInstitution(institutionId);
        String name = request.name().trim();
        String code = trimToNull(request.code());
        WhatsAppSupport.validatePlaceholders(request.body());
        if (repository.existsByInstitutionIdAndNameIgnoreCase(institutionId, name)) {
            throw new ConflictException("Ya existe una plantilla con ese nombre");
        }
        if (code != null && repository.existsByInstitutionIdAndCodeIgnoreCase(institutionId, code)) {
            throw new ConflictException("Ya existe una plantilla con ese código");
        }

        MessageTemplate template = new MessageTemplate();
        template.setInstitution(institution);
        template.setName(name);
        template.setCode(code);
        template.setBody(request.body().trim());
        template.setActive(request.active() == null || request.active());

        MessageTemplate saved = repository.save(template);
        auditService.record("MESSAGE_TEMPLATE_CREATED", "MessageTemplate", saved.getId(), institutionId,
                "name=" + name);
        return toResponse(saved);
    }

    @Transactional
    public MessageTemplateResponse update(UUID institutionId, UUID id, MessageTemplateRequest request) {
        MessageTemplate template = find(institutionId, id);
        String name = request.name().trim();
        String code = trimToNull(request.code());
        WhatsAppSupport.validatePlaceholders(request.body());
        if (repository.existsByInstitutionIdAndNameIgnoreCaseAndIdNot(institutionId, name, id)) {
            throw new ConflictException("Ya existe una plantilla con ese nombre");
        }
        if (code != null && repository.existsByInstitutionIdAndCodeIgnoreCaseAndIdNot(institutionId, code, id)) {
            throw new ConflictException("Ya existe una plantilla con ese código");
        }

        template.setName(name);
        template.setCode(code);
        template.setBody(request.body().trim());
        if (request.active() != null) {
            template.setActive(request.active());
        }
        MessageTemplate saved = repository.save(template);
        auditService.record("MESSAGE_TEMPLATE_UPDATED", "MessageTemplate", saved.getId(), institutionId,
                "name=" + name);
        return toResponse(saved);
    }

    public MessageTemplate find(UUID institutionId, UUID id) {
        return repository.findByIdAndInstitutionId(id, institutionId)
                .orElseThrow(() -> new NotFoundException("La plantilla no existe en la institución"));
    }

    public MessageTemplateResponse toResponse(MessageTemplate template) {
        return new MessageTemplateResponse(
                template.getId(),
                template.getCode(),
                template.getName(),
                template.getBody(),
                template.isActive(),
                template.getCreatedAt(),
                template.getUpdatedAt());
    }

    private Institution loadInstitution(UUID institutionId) {
        return institutionRepository.findById(institutionId)
                .orElseThrow(() -> new NotFoundException("La institución no existe"));
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
