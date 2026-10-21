package com.akira.educacion.institution.service;

import com.akira.educacion.audit.AuditService;
import com.akira.educacion.common.api.BadRequestException;
import com.akira.educacion.common.api.ConflictException;
import com.akira.educacion.common.api.NotFoundException;
import com.akira.educacion.common.api.PageResponse;
import com.akira.educacion.common.security.CurrentUser;
import com.akira.educacion.identity.domain.RoleCodes;
import com.akira.educacion.identity.service.UserService;
import com.akira.educacion.institution.api.CreateInstitutionRequest;
import com.akira.educacion.institution.api.InstitutionResponse;
import com.akira.educacion.institution.api.UpdateInstitutionRequest;
import com.akira.educacion.institution.domain.Institution;
import com.akira.educacion.institution.domain.InstitutionModuleCode;
import com.akira.educacion.institution.repository.InstitutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InstitutionService {

    private final InstitutionRepository institutionRepository;
    private final UserService userService;
    private final CurrentUser currentUser;
    private final AuditService auditService;

    @Transactional
    public InstitutionResponse create(CreateInstitutionRequest request) {
        requirePlatformAdmin();

        String taxId = normalize(request.taxId());
        if (taxId != null && institutionRepository.existsByTaxId(taxId)) {
            throw new ConflictException("Ya existe una institución con la identificación tributaria indicada");
        }

        Institution institution = new Institution();
        institution.setLegalName(request.legalName().trim());
        institution.setTradeName(normalize(request.tradeName()));
        institution.setTaxId(taxId);
        institution.setEmail(normalize(request.email()));
        institution.setPhone(normalize(request.phone()));
        institution.setWebsite(normalize(request.website()));
        institution.setAddress(normalize(request.address()));
        institution.setCity(normalize(request.city()));
        institution.setCountry(request.country().trim());
        institution.setTimezone(request.timezone().trim());
        institution.setLanguage(request.language().trim());
        institution.setCurrency(normalizeCurrency(request.currency()));
        institution.setLogoUrl(normalize(request.logoUrl()));
        institution.setPrimaryColor(normalize(request.primaryColor()));
        institution.setSecondaryColor(normalize(request.secondaryColor()));
        institution.setVatEnabled(Boolean.TRUE.equals(request.vatEnabled()));
        institution.setVatRate(request.vatRate());
        institution.setVatEffectiveFrom(request.vatEffectiveFrom());
        institution.setModules(resolveModules(request.modules(), true));
        institution.setActive(true);

        validateVat(institution);
        Institution saved = institutionRepository.save(institution);

        if (request.admin() != null) {
            userService.create(
                    saved,
                    request.admin().email(),
                    request.admin().fullName(),
                    request.admin().password(),
                    Set.of(RoleCodes.INSTITUTION_ADMIN));
        }

        auditService.record("INSTITUTION_CREATED", "Institution", saved.getId(), saved.getId(),
                "legalName=" + saved.getLegalName());
        return toResponse(saved);
    }

    @Transactional
    public InstitutionResponse update(UUID institutionId, UpdateInstitutionRequest request) {
        Institution institution = institutionRepository.findById(institutionId)
                .orElseThrow(() -> new NotFoundException("La institución no existe"));

        if (!currentUser.isPlatformAdmin() && !institutionId.equals(currentUser.requiredInstitutionId())) {
            throw new AccessDeniedException("No puede administrar otra institución");
        }

        List<String> changed = new ArrayList<>();

        if (request.legalName() != null) {
            institution.setLegalName(request.legalName().trim());
            changed.add("legalName");
        }
        if (request.tradeName() != null) {
            institution.setTradeName(normalize(request.tradeName()));
            changed.add("tradeName");
        }
        if (request.taxId() != null) {
            String taxId = normalize(request.taxId());
            if (taxId != null && institutionRepository.existsByTaxIdAndIdNot(taxId, institutionId)) {
                throw new ConflictException("Ya existe otra institución con esa identificación tributaria");
            }
            institution.setTaxId(taxId);
            changed.add("taxId");
        }
        if (request.email() != null) {
            institution.setEmail(normalize(request.email()));
            changed.add("email");
        }
        if (request.phone() != null) {
            institution.setPhone(normalize(request.phone()));
            changed.add("phone");
        }
        if (request.website() != null) {
            institution.setWebsite(normalize(request.website()));
            changed.add("website");
        }
        if (request.address() != null) {
            institution.setAddress(normalize(request.address()));
            changed.add("address");
        }
        if (request.city() != null) {
            institution.setCity(normalize(request.city()));
            changed.add("city");
        }
        if (request.country() != null) {
            institution.setCountry(request.country().trim());
            changed.add("country");
        }
        if (request.timezone() != null) {
            institution.setTimezone(request.timezone().trim());
            changed.add("timezone");
        }
        if (request.language() != null) {
            institution.setLanguage(request.language().trim());
            changed.add("language");
        }
        if (request.currency() != null) {
            institution.setCurrency(normalizeCurrency(request.currency()));
            changed.add("currency");
        }
        if (request.logoUrl() != null) {
            institution.setLogoUrl(normalize(request.logoUrl()));
            changed.add("logoUrl");
        }
        if (request.primaryColor() != null) {
            institution.setPrimaryColor(normalize(request.primaryColor()));
            changed.add("primaryColor");
        }
        if (request.secondaryColor() != null) {
            institution.setSecondaryColor(normalize(request.secondaryColor()));
            changed.add("secondaryColor");
        }
        if (request.vatEnabled() != null) {
            institution.setVatEnabled(request.vatEnabled());
            changed.add("vatEnabled");
        }
        if (request.vatRate() != null) {
            institution.setVatRate(request.vatRate());
            changed.add("vatRate");
        }
        if (request.vatEffectiveFrom() != null) {
            institution.setVatEffectiveFrom(request.vatEffectiveFrom());
            changed.add("vatEffectiveFrom");
        }
        if (request.modules() != null) {
            institution.setModules(resolveModules(request.modules(), false));
            changed.add("modules");
        }
        if (request.active() != null) {
            institution.setActive(request.active());
            changed.add("active");
        }

        validateVat(institution);
        Institution saved = institutionRepository.save(institution);

        auditService.record("INSTITUTION_UPDATED", "Institution", saved.getId(), saved.getId(),
                "fields=" + String.join(",", changed));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public InstitutionResponse getById(UUID institutionId) {
        if (!currentUser.isPlatformAdmin() && !institutionId.equals(currentUser.requiredInstitutionId())) {
            throw new AccessDeniedException("No puede consultar otra institución");
        }
        return toResponse(find(institutionId));
    }

    @Transactional(readOnly = true)
    public InstitutionResponse getMine() {
        return toResponse(find(currentUser.requiredInstitutionId()));
    }

    @Transactional(readOnly = true)
    public PageResponse<InstitutionResponse> list(String search, Pageable pageable) {
        requirePlatformAdmin();
        String normalized = (search == null || search.isBlank()) ? null : search.trim();
        Page<Institution> page = normalized == null
                ? institutionRepository.findAll(pageable)
                : institutionRepository.search(normalized, pageable);
        return PageResponse.from(page.map(this::toResponse));
    }

    private Institution find(UUID institutionId) {
        return institutionRepository.findById(institutionId)
                .orElseThrow(() -> new NotFoundException("La institución no existe"));
    }

    private void requirePlatformAdmin() {
        if (!currentUser.isPlatformAdmin()) {
            throw new AccessDeniedException("La operación requiere un administrador de plataforma");
        }
    }

    private void validateVat(Institution institution) {
        BigDecimal rate = institution.getVatRate();
        if (institution.isVatEnabled()) {
            if (rate == null) {
                throw new BadRequestException("Debe indicar la tasa de IVA cuando el IVA está habilitado");
            }
            if (rate.signum() < 0 || rate.compareTo(new BigDecimal("100")) > 0) {
                throw new BadRequestException("La tasa de IVA debe estar entre 0 y 100");
            }
        }
    }

    private Set<InstitutionModuleCode> resolveModules(Set<InstitutionModuleCode> requested, boolean defaultsWhenEmpty) {
        if (requested == null || requested.isEmpty()) {
            return defaultsWhenEmpty
                    ? EnumSet.allOf(InstitutionModuleCode.class)
                    : EnumSet.noneOf(InstitutionModuleCode.class);
        }
        return EnumSet.copyOf(requested);
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return "USD";
        }
        return currency.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private InstitutionResponse toResponse(Institution institution) {
        return new InstitutionResponse(
                institution.getId(),
                institution.getLegalName(),
                institution.getTradeName(),
                institution.getTaxId(),
                institution.getEmail(),
                institution.getPhone(),
                institution.getWebsite(),
                institution.getAddress(),
                institution.getCity(),
                institution.getCountry(),
                institution.getTimezone(),
                institution.getLanguage(),
                institution.getCurrency(),
                institution.getLogoUrl(),
                institution.getPrimaryColor(),
                institution.getSecondaryColor(),
                institution.isVatEnabled(),
                institution.getVatRate(),
                institution.getVatEffectiveFrom(),
                institution.isActive(),
                Set.copyOf(institution.getModules()),
                institution.getCreatedAt(),
                institution.getUpdatedAt());
    }
}
