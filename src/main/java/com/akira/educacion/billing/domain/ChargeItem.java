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
 * Detalle inmutable de una obligación. Guarda una instantánea del concepto, la
 * base imponible, la tasa de IVA aplicada y el importe resultante, de modo que
 * los cambios posteriores del catálogo no alteren el documento.
 */
@Entity
@Table(name = "charge_item")
@Getter
@Setter
@NoArgsConstructor
public class ChargeItem extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "charge_id", nullable = false)
    private Charge charge;

    /** Referencia al concepto; puede ser nula para conceptos históricos. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concept_id")
    private BillingConcept concept;

    @Column(name = "concept_code", length = 40)
    private String conceptCode;

    @Column(name = "concept_name", nullable = false, length = 150)
    private String conceptName;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "quantity", nullable = false)
    private int quantity = 1;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @Column(name = "discount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(name = "taxable_base", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxableBase = BigDecimal.ZERO;

    @Column(name = "vat_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal vatRate = BigDecimal.ZERO;

    @Column(name = "vat_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal vatAmount = BigDecimal.ZERO;

    @Column(name = "total", nullable = false, precision = 12, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    public BigDecimal grossAmount() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
