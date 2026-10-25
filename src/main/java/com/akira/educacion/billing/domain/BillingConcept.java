package com.akira.educacion.billing.domain;

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

import java.math.BigDecimal;

/**
 * Concepto de cobro del catálogo institucional (matrícula, pensión,
 * transporte, etc.). El importe por defecto solo se usa para generar
 * obligaciones mensuales asistidas; las obligaciones ya emitidas conservan su
 * propia instantánea.
 */
@Entity
@Table(name = "billing_concept")
@Getter
@Setter
@NoArgsConstructor
public class BillingConcept extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    @Column(name = "code", length = 40)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "default_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal defaultAmount = BigDecimal.ZERO;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
