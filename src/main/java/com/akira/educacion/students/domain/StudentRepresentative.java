package com.akira.educacion.students.domain;

import com.akira.educacion.common.domain.AuditableEntity;
import com.akira.educacion.institution.domain.Institution;
import com.akira.educacion.representatives.domain.Representative;
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
 * Vínculo entre estudiante y representante. Las responsabilidades no son
 * homogéneas: contacto principal, autorizado para comunicaciones y autorizado
 * para asuntos de cobro se registran por separado para cada vínculo.
 */
@Entity
@Table(name = "student_representative")
@Getter
@Setter
@NoArgsConstructor
public class StudentRepresentative extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "representative_id", nullable = false)
    private Representative representative;

    @Column(name = "primary_contact", nullable = false)
    private boolean primaryContact;

    @Column(name = "authorized_for_communications", nullable = false)
    private boolean authorizedForCommunications = true;

    @Column(name = "authorized_for_billing", nullable = false)
    private boolean authorizedForBilling;
}
