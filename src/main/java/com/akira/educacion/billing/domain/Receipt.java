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
 * Recibo interno emitido al confirmar un pago. No es un comprobante fiscal; la
 * numeración es secuencial por institución.
 */
@Entity
@Table(name = "receipt")
@Getter
@Setter
@NoArgsConstructor
public class Receipt extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "receipt_number", nullable = false, length = 30)
    private String receiptNumber;

    @Column(name = "sequence_number", nullable = false)
    private long sequenceNumber;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "issued_by_email", length = 150)
    private String issuedByEmail;

    @Column(name = "voided", nullable = false)
    private boolean voided;

    @Column(name = "void_reason", length = 500)
    private String voidReason;

    @Column(name = "voided_at")
    private Instant voidedAt;
}
