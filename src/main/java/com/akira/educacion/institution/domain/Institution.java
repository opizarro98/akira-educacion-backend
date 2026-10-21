package com.akira.educacion.institution.domain;

import com.akira.educacion.common.domain.AuditableEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

/**
 * Institución educativa. Contiene la configuración editable por su
 * administrador y los datos que se usan en documentos internos.
 */
@Entity
@Table(name = "institution")
@Getter
@Setter
@NoArgsConstructor
public class Institution extends AuditableEntity {

    @Column(name = "legal_name", nullable = false, length = 200)
    private String legalName;

    @Column(name = "trade_name", length = 200)
    private String tradeName;

    @Column(name = "tax_id", length = 30)
    private String taxId;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "phone", length = 40)
    private String phone;

    @Column(name = "website", length = 200)
    private String website;

    @Column(name = "address", length = 250)
    private String address;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "country", nullable = false, length = 100)
    private String country = "Ecuador";

    @Column(name = "timezone", nullable = false, length = 60)
    private String timezone = "America/Guayaquil";

    @Column(name = "language", nullable = false, length = 10)
    private String language = "es";

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "primary_color", length = 20)
    private String primaryColor;

    @Column(name = "secondary_color", length = 20)
    private String secondaryColor;

    @Column(name = "vat_enabled", nullable = false)
    private boolean vatEnabled;

    @Column(name = "vat_rate", precision = 5, scale = 2)
    private BigDecimal vatRate;

    @Column(name = "vat_effective_from")
    private LocalDate vatEffectiveFrom;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "institution_module", joinColumns = @JoinColumn(name = "institution_id"))
    @Column(name = "module_code", nullable = false, length = 40)
    @Enumerated(EnumType.STRING)
    private Set<InstitutionModuleCode> modules = EnumSet.noneOf(InstitutionModuleCode.class);
}
