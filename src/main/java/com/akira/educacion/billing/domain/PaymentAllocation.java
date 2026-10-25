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
import java.time.Instant;

/**
 * Aplicación de un pago confirmado a una obligación. Las aplicaciones no se
 * eliminan: se revierten y conservan el motivo y el actor.
 */
@Entity
@Table(name = "payment_allocation")
@Getter
@Setter
@NoArgsConstructor
public class PaymentAllocation extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "charge_id", nullable = false)
    private Charge charge;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    /** Clave de la operación de aplicación, para reintentos idempotentes. */
    @Column(name = "operation_key", length = 160)
    private String operationKey;

    @Column(name = "reversed", nullable = false)
    private boolean reversed;

    @Column(name = "reversed_at")
    private Instant reversedAt;

    @Column(name = "reversed_by", length = 150)
    private String reversedBy;

    @Column(name = "reversal_reason", length = 500)
    private String reversalReason;
}
