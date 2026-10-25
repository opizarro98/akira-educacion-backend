package com.akira.educacion.billing.domain;

import com.akira.educacion.common.domain.AuditableEntity;
import com.akira.educacion.institution.domain.Institution;
import com.akira.educacion.students.domain.Student;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pago recibido de un estudiante. No se edita libremente: se registra, se
 * confirma (cuando corresponde verificarlo), se aplica a obligaciones y, si es
 * necesario, se anula conservando su historial.
 */
@Entity
@Table(name = "payment")
@Getter
@Setter
@NoArgsConstructor
public class Payment extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 20)
    private PaymentMethod method;

    @Column(name = "reference", length = 100)
    private String reference;

    @Column(name = "received_date", nullable = false)
    private LocalDate receivedDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PaymentStatus status = PaymentStatus.PENDING_VERIFICATION;

    /** Suma de aplicaciones vigentes; se mantiene en la misma transacción. */
    @Column(name = "applied_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal appliedAmount = BigDecimal.ZERO;

    @Column(name = "registered_by_user_id")
    private UUID registeredByUserId;

    @Column(name = "registered_by_email", length = 150)
    private String registeredByEmail;

    @Column(name = "confirmed_by_user_id")
    private UUID confirmedByUserId;

    @Column(name = "confirmed_by_email", length = 150)
    private String confirmedByEmail;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "idempotency_key", length = 160)
    private String idempotencyKey;

    @Column(name = "void_reason", length = 500)
    private String voidReason;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "voided_by", length = 150)
    private String voidedBy;

    @OneToMany(mappedBy = "payment", fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    private List<PaymentAllocation> allocations = new ArrayList<>();

    public BigDecimal availableAmount() {
        return amount.subtract(appliedAmount);
    }
}
