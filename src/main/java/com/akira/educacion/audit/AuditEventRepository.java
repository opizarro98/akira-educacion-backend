package com.akira.educacion.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    long countByEntityTypeAndEntityId(String entityType, String entityId);

    List<AuditEvent> findByEntityTypeAndEntityIdOrderByOccurredAtDesc(String entityType, String entityId);

    List<AuditEvent> findAllByOrderByOccurredAtDesc();
}
