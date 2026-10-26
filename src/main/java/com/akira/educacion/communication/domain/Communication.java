package com.akira.educacion.communication.domain;

import com.akira.educacion.common.domain.AuditableEntity;
import com.akira.educacion.institution.domain.Institution;
import com.akira.educacion.representatives.domain.Representative;
import com.akira.educacion.students.domain.Student;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Comunicación asistida preparada para abrirse mediante un enlace
 * {@code wa.me}.
 * Conserva la instantánea del mensaje, del teléfono y de la plantilla usada.
 */
@Entity
@Table(name = "communication")
@Getter
@Setter
@NoArgsConstructor
public class Communication extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "representative_id", nullable = false)
    private Representative representative;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private MessageTemplate template;

    @Column(name = "template_name", nullable = false, length = 150)
    private String templateName;

    @Column(name = "phone", nullable = false, length = 40)
    private String phone;

    @Column(name = "wa_link", nullable = false, columnDefinition = "text")
    private String waLink;

    @Column(name = "message_body", nullable = false, columnDefinition = "text")
    private String messageBody;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CommunicationStatus status = CommunicationStatus.DRAFT;

    @Column(name = "include_financial_info", nullable = false)
    private boolean includeFinancialInfo;

    @Column(name = "financial_note", length = 500)
    private String financialNote;

    @Column(name = "opened_at")
    private Instant openedAt;

    @Column(name = "failed_reason", length = 500)
    private String failedReason;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Column(name = "created_by_email", length = 150)
    private String createdByEmail;
}
