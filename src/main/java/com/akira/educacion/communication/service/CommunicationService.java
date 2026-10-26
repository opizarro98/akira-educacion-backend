package com.akira.educacion.communication.service;

import com.akira.educacion.audit.AuditService;
import com.akira.educacion.common.api.BadRequestException;
import com.akira.educacion.common.api.ConflictException;
import com.akira.educacion.common.api.NotFoundException;
import com.akira.educacion.common.api.PageResponse;
import com.akira.educacion.common.security.CurrentUser;
import com.akira.educacion.communication.api.CommunicationPrepareRequest;
import com.akira.educacion.communication.api.CommunicationPreviewRequest;
import com.akira.educacion.communication.api.CommunicationPreviewResponse;
import com.akira.educacion.communication.api.CommunicationResponse;
import com.akira.educacion.communication.domain.Communication;
import com.akira.educacion.communication.domain.CommunicationStatus;
import com.akira.educacion.communication.domain.MessageTemplate;
import com.akira.educacion.communication.repository.CommunicationRepository;
import com.akira.educacion.institution.domain.Institution;
import com.akira.educacion.institution.repository.InstitutionRepository;
import com.akira.educacion.representatives.domain.Representative;
import com.akira.educacion.students.domain.Student;
import com.akira.educacion.students.domain.StudentRepresentative;
import com.akira.educacion.students.repository.StudentRepresentativeRepository;
import com.akira.educacion.students.service.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Comunicaciones asistidas mediante enlaces {@code wa.me}.
 *
 * <p>
 * El backend valida que el representante pertenezca a la institución, que el
 * vínculo esté autorizado para comunicaciones y que tenga un teléfono válido.
 * El estado {@link CommunicationStatus#OPENED} solo indica que el usuario abrió
 * el enlace; no es una confirmación de envío.
 */
@Service
@RequiredArgsConstructor
public class CommunicationService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String NO_DELIVERY_WARNING = "El enlace wa.me abre WhatsApp, pero no confirma el envío ni la entrega del mensaje.";

    private final CommunicationRepository communicationRepository;
    private final MessageTemplateService templateService;
    private final StudentService studentService;
    private final StudentRepresentativeRepository linkRepository;
    private final InstitutionRepository institutionRepository;
    private final CurrentUser currentUser;
    private final AuditService auditService;

    // ------------------------------------------------------------------
    // Vista previa y preparación
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public CommunicationPreviewResponse preview(UUID institutionId, CommunicationPreviewRequest request) {
        return prepare(institutionId, request.studentId(), request.representativeId(), request.templateId(),
                Boolean.TRUE.equals(request.includeFinancialInfo()), request.financialNote()).response();
    }

    @Transactional
    public CommunicationResponse create(UUID institutionId, CommunicationPrepareRequest request) {
        PreparedMessage prepared = prepare(institutionId, request.studentId(), request.representativeId(),
                request.templateId(), Boolean.TRUE.equals(request.includeFinancialInfo()), request.financialNote());

        Communication communication = new Communication();
        communication.setInstitution(prepared.institution());
        communication.setStudent(prepared.student());
        communication.setRepresentative(prepared.representative());
        communication.setTemplate(prepared.template());
        communication.setTemplateName(prepared.template().getName());
        communication.setPhone(prepared.phone());
        communication.setWaLink(prepared.response().waLink());
        communication.setMessageBody(prepared.response().messageBody());
        communication.setIncludeFinancialInfo(prepared.response().includeFinancialInfo());
        communication.setFinancialNote(prepared.response().financialNote());
        communication.setCreatedByUserId(currentUserIdOrNull());
        communication.setCreatedByEmail(actorEmail());
        boolean draft = Boolean.TRUE.equals(request.saveAsDraft());
        communication.setStatus(draft ? CommunicationStatus.DRAFT : CommunicationStatus.READY);

        Communication saved = communicationRepository.save(communication);
        auditService.record(draft ? "COMMUNICATION_DRAFT_SAVED" : "COMMUNICATION_PREPARED",
                "Communication", saved.getId(), institutionId,
                "studentId=" + prepared.student().getId() + "; representativeId="
                        + prepared.representative().getId() + "; template=" + prepared.template().getName()
                        + "; includeFinancialInfo=" + communication.isIncludeFinancialInfo());
        return toResponse(saved);
    }

    @Transactional
    public CommunicationResponse markOpened(UUID institutionId, UUID id) {
        Communication communication = lock(institutionId, id);
        if (communication.getStatus() == CommunicationStatus.OPENED) {
            return toResponse(communication);
        }
        if (communication.getStatus() == CommunicationStatus.FAILED) {
            throw new ConflictException("Una comunicación fallida no puede marcarse como abierta");
        }

        communication.setStatus(CommunicationStatus.OPENED);
        communication.setOpenedAt(Instant.now());
        Communication saved = communicationRepository.save(communication);
        auditService.record("COMMUNICATION_OPENED", "Communication", saved.getId(), institutionId,
                "waLink puede haber abierto WhatsApp; el envío no está confirmado");
        return toResponse(saved);
    }

    @Transactional
    public CommunicationResponse markFailed(UUID institutionId, UUID id, String reason) {
        Communication communication = lock(institutionId, id);
        if (communication.getStatus() == CommunicationStatus.OPENED) {
            throw new ConflictException("Una comunicación abierta no puede marcarse como fallida");
        }
        communication.setStatus(CommunicationStatus.FAILED);
        communication.setFailedReason(reason.trim());
        Communication saved = communicationRepository.save(communication);
        auditService.record("COMMUNICATION_FAILED", "Communication", saved.getId(), institutionId,
                "reason=" + reason.trim());
        return toResponse(saved);
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<CommunicationResponse> list(UUID institutionId, CommunicationStatus status, UUID studentId,
            Pageable pageable) {
        Page<Communication> page = communicationRepository.search(institutionId,
                status != null, status == null ? CommunicationStatus.DRAFT.name() : status.name(),
                studentId != null, studentId, pageable);
        return PageResponse.from(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public CommunicationResponse get(UUID institutionId, UUID id) {
        return toResponse(find(institutionId, id));
    }

    // ------------------------------------------------------------------
    // Apoyo interno
    // ------------------------------------------------------------------

    /**
     * Valida y renderiza el mensaje sin persistir nada. El mensaje devuelto es
     * exactamente el que se usará en el enlace {@code wa.me}.
     */
    private PreparedMessage prepare(UUID institutionId, UUID studentId, UUID representativeId, UUID templateId,
            boolean includeFinancialInfo, String financialNote) {
        Institution institution = institutionRepository.findById(institutionId)
                .orElseThrow(() -> new NotFoundException("La institución no existe"));
        Student student = studentService.find(institutionId, studentId);

        StudentRepresentative link = linkRepository
                .findByStudentIdAndRepresentativeId(studentId, representativeId)
                .orElseThrow(() -> new NotFoundException(
                        "El representante no está asociado al estudiante en esta institución"));
        if (!link.getInstitution().getId().equals(institutionId)
                || !link.getRepresentative().getInstitution().getId().equals(institutionId)) {
            throw new NotFoundException("El representante no está asociado al estudiante en esta institución");
        }
        if (!link.isAuthorizedForCommunications()) {
            throw new BadRequestException(
                    "El representante no está autorizado para comunicaciones de este estudiante");
        }

        Representative representative = link.getRepresentative();
        String waPhone = WhatsAppSupport.normalizePhone(representative.getPhone());
        MessageTemplate template = templateService.find(institutionId, templateId);
        if (!template.isActive()) {
            throw new BadRequestException("La plantilla está inactiva");
        }

        String note = financialNote == null || financialNote.isBlank() ? null : financialNote.trim();
        if (includeFinancialInfo && note == null) {
            throw new BadRequestException(
                    "Debe indicar el detalle financiero y confirmarlo antes de incluirlo en el mensaje");
        }

        ZoneId zone = ZoneId.of(institution.getTimezone() == null ? "America/Guayaquil"
                : institution.getTimezone());
        Map<String, String> values = new HashMap<>();
        values.put("estudiante", student.fullName());
        values.put("representante", representative.getFullName());
        values.put("institucion", institution.getTradeName() == null
                ? institution.getLegalName()
                : institution.getTradeName());
        values.put("fecha", LocalDate.now(zone).format(DATE_FORMAT));
        values.put("detalle_financiero", includeFinancialInfo ? note : "");

        String messageBody = WhatsAppSupport.render(template.getBody(), values).trim();
        if (includeFinancialInfo && !hasFinancialPlaceholder(template.getBody())) {
            messageBody = messageBody + "\n\nDetalle financiero: " + note;
        }

        List<String> warnings = new ArrayList<>();
        warnings.add(NO_DELIVERY_WARNING);
        if (includeFinancialInfo) {
            warnings.add(
                    "El mensaje incluye información financiera. Revise la vista previa antes de abrir WhatsApp.");
        }

        CommunicationPreviewResponse response = new CommunicationPreviewResponse(
                student.getId(),
                student.fullName(),
                representative.getId(),
                representative.getFullName(),
                template.getId(),
                template.getName(),
                representative.getPhone(),
                waPhone,
                messageBody,
                WhatsAppSupport.waLink(waPhone, messageBody),
                includeFinancialInfo,
                includeFinancialInfo ? note : null,
                List.copyOf(warnings));

        return new PreparedMessage(institution, student, representative, template, representative.getPhone(),
                response);
    }

    private boolean hasFinancialPlaceholder(String body) {
        return body.toLowerCase(java.util.Locale.ROOT).contains("detalle_financiero");
    }

    private Communication find(UUID institutionId, UUID id) {
        return communicationRepository.findByIdAndInstitutionId(id, institutionId)
                .orElseThrow(() -> new NotFoundException("La comunicación no existe en la institución"));
    }

    private Communication lock(UUID institutionId, UUID id) {
        return communicationRepository.lockByIdAndInstitutionId(id, institutionId)
                .orElseThrow(() -> new NotFoundException("La comunicación no existe en la institución"));
    }

    public CommunicationResponse toResponse(Communication communication) {
        return new CommunicationResponse(
                communication.getId(),
                communication.getStudent().getId(),
                communication.getStudent().fullName(),
                communication.getRepresentative().getId(),
                communication.getRepresentative().getFullName(),
                communication.getTemplate() == null ? null : communication.getTemplate().getId(),
                communication.getTemplateName(),
                communication.getPhone(),
                WhatsAppSupport.normalizePhone(communication.getPhone()),
                communication.getMessageBody(),
                communication.getWaLink(),
                communication.getStatus(),
                communication.isIncludeFinancialInfo(),
                communication.getFinancialNote(),
                communication.getOpenedAt(),
                communication.getFailedReason(),
                communication.getCreatedByEmail(),
                communication.getCreatedAt(),
                communication.getUpdatedAt());
    }

    private UUID currentUserIdOrNull() {
        try {
            return currentUser.userId();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String actorEmail() {
        try {
            return currentUser.email();
        } catch (RuntimeException ex) {
            return "system";
        }
    }

    private record PreparedMessage(
            Institution institution,
            Student student,
            Representative representative,
            MessageTemplate template,
            String phone,
            CommunicationPreviewResponse response) {
    }
}
