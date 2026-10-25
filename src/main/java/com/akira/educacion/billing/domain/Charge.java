package com.akira.educacion.billing.domain;

import com.akira.educacion.common.domain.AuditableEntity;
import com.akira.educacion.enrollment.domain.Enrollment;
import com.akira.educacion.institution.domain.Institution;
import com.akira.educacion.students.domain.Student;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Obligación de cobro emitida a un estudiante.
 *
 * <p>
 * Es un documento histórico: una vez creado no se edita libremente. Los
 * importes, el IVA y la moneda quedan congelados, y la única transición
 * permitida en esta fase es la anulación con motivo.
 */
@Entity
@Table(name = "charge")
@Getter
@Setter
@NoArgsConstructor
public class Charge extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    /** Matrícula que originó el cobro inicial, si aplica. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enrollment_id")
    private Enrollment enrollment;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, length = 20)
    private ChargeOrigin origin = ChargeOrigin.MANUAL;

    /**
     * Clave de idempotencia enviada por el cliente o derivada del origen. El
     * índice único de base de datos impide duplicados.
     */
    @Column(name = "idempotency_key", length = 160)
    private String idempotencyKey;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ChargeStatus status = ChargeStatus.PENDING;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "subtotal", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "total", nullable = false, precision = 12, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    /** Importe pagado; permanece en cero en la Fase 3A. */
    @Column(name = "paid_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(name = "balance", nullable = false, precision = 12, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "responsible_user_id")
    private java.util.UUID responsibleUserId;

    @Column(name = "responsible_email", length = 150)
    private String responsibleEmail;

    @Column(name = "void_reason", length = 500)
    private String voidReason;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "voided_by", length = 150)
    private String voidedBy;

    @OneToMany(mappedBy = "charge", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ChargeItem> items = new ArrayList<>();

    public boolean isVoidable() {
        return status != ChargeStatus.VOID && paidAmount.signum() == 0;
    }

    public void addItem(ChargeItem item) {
        item.setCharge(this);
        item.setInstitution(institution);
        items.add(item);
    }
}
