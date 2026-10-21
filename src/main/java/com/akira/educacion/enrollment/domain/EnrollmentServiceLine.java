package com.akira.educacion.enrollment.domain;

import com.akira.educacion.catalog.domain.CatalogServiceItem;
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
 * Servicio adicional de una matrícula, con el precio acordado copiado desde la
 * tarifa vigente en el momento de la operación.
 */
@Entity
@Table(name = "enrollment_service")
@Getter
@Setter
@NoArgsConstructor
public class EnrollmentServiceLine extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enrollment_id", nullable = false)
    private Enrollment enrollment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private CatalogServiceItem service;

    @Column(name = "quantity", nullable = false)
    private int quantity = 1;

    @Column(name = "agreed_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal agreedPrice = BigDecimal.ZERO;

    public BigDecimal lineTotal() {
        return agreedPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
