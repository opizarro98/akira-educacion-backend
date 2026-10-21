package com.akira.educacion.audit;

import com.akira.educacion.common.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Registra eventos de auditoría. El actor se obtiene de la sesión; si no hay
 * sesión (por ejemplo, arranque del sistema) se registra como "system".
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository repository;
    private final CurrentUser currentUser;

    public void record(String action, String entityType, UUID entityId, UUID institutionId, String details) {
        AuditEvent event = new AuditEvent();
        event.setAction(action);
        event.setEntityType(entityType);
        event.setEntityId(entityId == null ? null : entityId.toString());
        event.setInstitutionId(institutionId);
        event.setDetails(details);
        event.setOccurredAt(Instant.now());

        try {
            event.setActorUserId(currentUser.userId());
            event.setActorEmail(currentUser.email());
        } catch (AccessDeniedException | IllegalArgumentException ex) {
            event.setActorEmail("system");
        }

        repository.save(event);
    }
}
