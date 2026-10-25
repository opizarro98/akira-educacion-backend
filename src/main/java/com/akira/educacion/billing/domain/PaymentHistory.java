package com.akira.educacion.billing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Historial append-only de pagos: registro, confirmación, aplicación,
 * reversión y anulación.
 */
@Entity
@Table(name = "payment_history")
@Getter
@Setter
@NoArgsConstructor
public class PaymentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "institution_id", nullable = false)
    private UUID institutionId;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "action", nullable = false, length = 40)
    private String action;

    @Column(name = "details", length = 1000)
    private String details;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "actor_email", length = 150)
    private String actorEmail;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
