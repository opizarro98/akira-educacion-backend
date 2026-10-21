package com.akira.educacion.representatives.domain;

import com.akira.educacion.common.domain.AuditableEntity;
import com.akira.educacion.institution.domain.Institution;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Representante (madre, padre, tutor u otro) de uno o varios estudiantes.
 */
@Entity
@Table(name = "representative")
@Getter
@Setter
@NoArgsConstructor
public class Representative extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "document_number", length = 40)
    private String documentNumber;

    @Column(name = "phone", length = 40)
    private String phone;

    @Column(name = "email", length = 150)
    private String email;

    /** Relación declarada con el estudiante (madre, padre, tutor...). */
    @Column(name = "relationship", length = 60)
    private String relationship;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;
}
