package com.akira.educacion.communication.domain;

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

/**
 * Plantilla de mensaje institucional. El cuerpo admite marcadores como
 * {@code {{estudiante}}}; no se permite HTML ni marcadores desconocidos.
 */
@Entity
@Table(name = "message_template")
@Getter
@Setter
@NoArgsConstructor
public class MessageTemplate extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    @Column(name = "code", length = 40)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
