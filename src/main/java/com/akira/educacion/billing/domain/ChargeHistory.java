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
 * Historial append-only de las obligaciones de cobro. No se actualiza ni se
 * elimina: cada emisión, anulación o cambio de estado deja un registro.
 */
@Entity
@Table(name = "charge_history")
@Getter
@Setter
@NoArgsConstructor
public class ChargeHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "institution_id", nullable = false)
    private UUID institutionId;

    @Column(name = "charge_id", nullable = false)
    private UUID chargeId;

    @Column(name = "action", nullable = false, length = 40)
    private String action;

    @Column(name = "from_status", length = 20)
    private String fromStatus;

    @Column(name = "to_status", length = 20)
    private String toStatus;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "details", length = 1000)
    private String details;

    @Column(name = "actor_email", length = 150)
    private String actorEmail;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
