package com.akira.educacion.finance.domain;

import com.akira.educacion.common.domain.AuditableEntity;
import com.akira.educacion.institution.domain.Institution;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Movimiento de caja derivado de un pago o egreso. No se edita ni se crea
 * manualmente: lo generan las transacciones financieras y se protege con una
 * clave única por operación y tipo.
 */
@Entity
@Table(name = "cash_movement")
@Getter
@Setter
@NoArgsConstructor
public class CashMovement extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 30)
    private CashMovementType movementType;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private CashMovementSource sourceType;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "expense_id")
    private UUID expenseId;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "actor_email", length = 150)
    private String actorEmail;

    /** Documento de origen según el tipo, para la respuesta de la API. */
    public UUID sourceId() {
        return sourceType == CashMovementSource.PAYMENT ? paymentId : expenseId;
    }

    /** Importe con signo: positivo si entra dinero, negativo si sale. */
    public BigDecimal signedAmount() {
        return movementType.signed(amount);
    }
}
