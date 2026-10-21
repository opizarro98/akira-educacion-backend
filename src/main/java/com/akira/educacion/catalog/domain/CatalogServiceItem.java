package com.akira.educacion.catalog.domain;

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
 * Servicio ofrecido por la institución (transporte, alimentación, materiales,
 * etc.). Los precios no viven aquí sino en {@link ServiceRate}, de modo que el
 * historial de tarifas se conserve.
 */
@Entity
@Table(name = "catalog_service")
@Getter
@Setter
@NoArgsConstructor
public class CatalogServiceItem extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "code", length = 40)
    private String code;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
