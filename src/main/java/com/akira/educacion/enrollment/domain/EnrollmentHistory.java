package com.akira.educacion.enrollment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Historial de cambios de la matrícula. Es append-only: nunca se actualiza ni
 * se elimina, y conserva el estado anterior y posterior de cada operación.
 */
@Entity
@Table(name = "enrollment_history")
@Getter
@Setter
@NoArgsConstructor
public class EnrollmentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "institution_id", nullable = false)
    private UUID institutionId;

    @Column(name = "enrollment_id", nullable = false)
    private UUID enrollmentId;

    @Column(name = "action", nullable = false, length = 40)
    private String action;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private EnrollmentStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", length = 20)
    private EnrollmentStatus toStatus;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "actor_email", length = 150)
    private String actorEmail;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();
}
