package com.akira.educacion.billing.domain;

import com.akira.educacion.common.domain.AuditableEntity;
import com.akira.educacion.institution.domain.Institution;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Configuración de períodos de cobro de la institución. Define la frecuencia,
 * el día de emisión y los días de gracia para el vencimiento. No genera
 * obligaciones por sí sola: la generación mensual siempre es una acción
 * explícita de un usuario.
 */
@Entity
@Table(name = "billing_settings")
@Getter
@Setter
@NoArgsConstructor
public class BillingSettings extends AuditableEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "institution_id", nullable = false, unique = true)
    private Institution institution;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false, length = 20)
    private BillingFrequency frequency = BillingFrequency.MONTHLY;

    /** Día del mes en que se emite la obligación (1-28). */
    @Column(name = "issue_day", nullable = false)
    private int issueDay = 1;

    /** Días naturales entre la emisión y el vencimiento. */
    @Column(name = "due_days", nullable = false)
    private int dueDays = 10;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
