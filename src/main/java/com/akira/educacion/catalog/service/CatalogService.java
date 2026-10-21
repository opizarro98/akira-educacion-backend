package com.akira.educacion.catalog.service;

import com.akira.educacion.audit.AuditService;
import com.akira.educacion.catalog.api.AcademicPeriodRequest;
import com.akira.educacion.catalog.api.AcademicPeriodResponse;
import com.akira.educacion.catalog.api.CatalogServiceRequest;
import com.akira.educacion.catalog.api.CatalogServiceResponse;
import com.akira.educacion.catalog.api.EducationalLevelRequest;
import com.akira.educacion.catalog.api.EducationalLevelResponse;
import com.akira.educacion.catalog.api.PlanRequest;
import com.akira.educacion.catalog.api.PlanResponse;
import com.akira.educacion.catalog.api.ServiceRateRequest;
import com.akira.educacion.catalog.api.ServiceRateResponse;
import com.akira.educacion.catalog.domain.AcademicPeriod;
import com.akira.educacion.catalog.domain.CatalogServiceItem;
import com.akira.educacion.catalog.domain.EducationalLevel;
import com.akira.educacion.catalog.domain.Plan;
import com.akira.educacion.catalog.domain.PlanService;
import com.akira.educacion.catalog.domain.ServiceRate;
import com.akira.educacion.catalog.repository.AcademicPeriodRepository;
import com.akira.educacion.catalog.repository.CatalogServiceItemRepository;
import com.akira.educacion.catalog.repository.EducationalLevelRepository;
import com.akira.educacion.catalog.repository.PlanRepository;
import com.akira.educacion.catalog.repository.ServiceRateRepository;
import com.akira.educacion.common.api.BadRequestException;
import com.akira.educacion.common.api.ConflictException;
import com.akira.educacion.common.api.NotFoundException;
import com.akira.educacion.institution.domain.Institution;
import com.akira.educacion.institution.repository.InstitutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Catálogo institucional: niveles, períodos, servicios, planes y tarifas.
 *
 * <p>
 * Ningún precio se duplica en código: las tarifas viven en {@link ServiceRate} y
 * solo se copian a la matrícula cuando esta se crea o edita.
 */
@Service
@RequiredArgsConstructor
public class CatalogService {

    private final EducationalLevelRepository levelRepository;
    private final AcademicPeriodRepository periodRepository;
    private final CatalogServiceItemRepository serviceItemRepository;
    private final PlanRepository planRepository;
    private final ServiceRateRepository rateRepository;
    private final InstitutionRepository institutionRepository;
    private final AuditService auditService;

    // ------------------------------------------------------------------
    // Niveles educativos
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<EducationalLevelResponse> listLevels(UUID institutionId) {
        return levelRepository.findByInstitutionIdOrderBySortOrderAscNameAsc(institutionId).stream()
                .map(this::toLevelResponse)
                .toList();
    }

    @Transactional
    public EducationalLevelResponse createLevel(UUID institutionId, EducationalLevelRequest request) {
        validateLevelName(institutionId, request.name(), null);
        EducationalLevel level = new EducationalLevel();
        level.setInstitution(loadInstitution(institutionId));
        applyLevel(level, request);
        EducationalLevel saved = levelRepository.save(level);
        auditService.record("EDUCATIONAL_LEVEL_CREATED", "EducationalLevel", saved.getId(), institutionId,
                "name=" + saved.getName());
        return toLevelResponse(saved);
    }

    @Transactional
    public EducationalLevelResponse updateLevel(UUID institutionId, UUID id, EducationalLevelRequest request) {
        EducationalLevel level = levelRepository.findByIdAndInstitutionId(id, institutionId)
                .orElseThrow(() -> new NotFoundException("El nivel educativo no existe"));
        validateLevelName(institutionId, request.name(), id);
        applyLevel(level, request);
        EducationalLevel saved = levelRepository.save(level);
        auditService.record("EDUCATIONAL_LEVEL_UPDATED", "EducationalLevel", saved.getId(), institutionId,
                "name=" + saved.getName());
        return toLevelResponse(saved);
    }

    // ------------------------------------------------------------------
    // Períodos lectivos (opcionales en la matrícula)
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<AcademicPeriodResponse> listPeriods(UUID institutionId) {
        return periodRepository.findByInstitutionIdOrderByStartDateDesc(institutionId).stream()
                .map(this::toPeriodResponse)
                .toList();
    }

    @Transactional
    public AcademicPeriodResponse createPeriod(UUID institutionId, AcademicPeriodRequest request) {
        validatePeriod(institutionId, request, null);
        AcademicPeriod period = new AcademicPeriod();
        period.setInstitution(loadInstitution(institutionId));
        applyPeriod(period, request);
        AcademicPeriod saved = periodRepository.save(period);
        auditService.record("ACADEMIC_PERIOD_CREATED", "AcademicPeriod", saved.getId(), institutionId,
                "name=" + saved.getName());
        return toPeriodResponse(saved);
    }

    @Transactional
    public AcademicPeriodResponse updatePeriod(UUID institutionId, UUID id, AcademicPeriodRequest request) {
        AcademicPeriod period = periodRepository.findByIdAndInstitutionId(id, institutionId)
                .orElseThrow(() -> new NotFoundException("El período lectivo no existe"));
        validatePeriod(institutionId, request, id);
        applyPeriod(period, request);
        AcademicPeriod saved = periodRepository.save(period);
        auditService.record("ACADEMIC_PERIOD_UPDATED", "AcademicPeriod", saved.getId(), institutionId,
                "name=" + saved.getName());
        return toPeriodResponse(saved);
    }

    // ------------------------------------------------------------------
    // Servicios
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<CatalogServiceResponse> listServices(UUID institutionId) {
        return serviceItemRepository.findByInstitutionIdOrderByNameAsc(institutionId).stream()
                .map(this::toServiceResponse)
                .toList();
    }

    @Transactional
    public CatalogServiceResponse createService(UUID institutionId, CatalogServiceRequest request) {
        validateServiceName(institutionId, request.name(), null);
        CatalogServiceItem service = new CatalogServiceItem();
        service.setInstitution(loadInstitution(institutionId));
        applyService(service, request);
        CatalogServiceItem saved = serviceItemRepository.save(service);
        auditService.record("CATALOG_SERVICE_CREATED", "CatalogService", saved.getId(), institutionId,
                "name=" + saved.getName());
        return toServiceResponse(saved);
    }

    @Transactional
    public CatalogServiceResponse updateService(UUID institutionId, UUID id, CatalogServiceRequest request) {
        CatalogServiceItem service = findService(institutionId, id);
        validateServiceName(institutionId, request.name(), id);
        applyService(service, request);
        CatalogServiceItem saved = serviceItemRepository.save(service);
        auditService.record("CATALOG_SERVICE_UPDATED", "CatalogService", saved.getId(), institutionId,
                "name=" + saved.getName());
        return toServiceResponse(saved);
    }

    // ------------------------------------------------------------------
    // Planes
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<PlanResponse> listPlans(UUID institutionId) {
        return planRepository.findByInstitutionIdOrderByNameAsc(institutionId).stream()
                .map(this::toPlanResponse)
                .toList();
    }

    @Transactional
    public PlanResponse createPlan(UUID institutionId, PlanRequest request) {
        validatePlanName(institutionId, request.name(), null);
        Plan plan = new Plan();
        plan.setInstitution(loadInstitution(institutionId));
        plan.setName(request.name().trim());
        plan.setDescription(trimToNull(request.description()));
        plan.setActive(request.active() == null || request.active());
        replaceServices(plan, request.serviceIds(), institutionId);
        Plan saved = planRepository.save(plan);
        auditService.record("PLAN_CREATED", "Plan", saved.getId(), institutionId, "name=" + saved.getName());
        return toPlanResponse(saved);
    }

    @Transactional
    public PlanResponse updatePlan(UUID institutionId, UUID id, PlanRequest request) {
        Plan plan = planRepository.findByIdAndInstitutionId(id, institutionId)
                .orElseThrow(() -> new NotFoundException("El plan no existe"));
        validatePlanName(institutionId, request.name(), id);
        plan.setName(request.name().trim());
        plan.setDescription(trimToNull(request.description()));
        if (request.active() != null) {
            plan.setActive(request.active());
        }
        if (request.serviceIds() != null) {
            replaceServices(plan, request.serviceIds(), institutionId);
        }
        Plan saved = planRepository.save(plan);
        auditService.record("PLAN_UPDATED", "Plan", saved.getId(), institutionId, "name=" + saved.getName());
        return toPlanResponse(saved);
    }

    @Transactional(readOnly = true)
    public Plan findPlan(UUID institutionId, UUID id) {
        return planRepository.findByIdAndInstitutionId(id, institutionId)
                .orElseThrow(() -> new NotFoundException("El plan no existe"));
    }

    @Transactional(readOnly = true)
    public EducationalLevel findLevel(UUID institutionId, UUID id) {
        return levelRepository.findByIdAndInstitutionId(id, institutionId)
                .orElseThrow(() -> new NotFoundException("El nivel educativo no existe"));
    }

    @Transactional(readOnly = true)
    public AcademicPeriod findPeriod(UUID institutionId, UUID id) {
        return periodRepository.findByIdAndInstitutionId(id, institutionId)
                .orElseThrow(() -> new NotFoundException("El período lectivo no existe"));
    }

    @Transactional(readOnly = true)
    public CatalogServiceItem findService(UUID institutionId, UUID id) {
        return serviceItemRepository.findByIdAndInstitutionId(id, institutionId)
                .orElseThrow(() -> new NotFoundException("El servicio no existe"));
    }

    // ------------------------------------------------------------------
    // Tarifas
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ServiceRateResponse> listRates(UUID institutionId) {
        return rateRepository.findByInstitutionIdOrderByValidFromDesc(institutionId).stream()
                .map(this::toRateResponse)
                .toList();
    }

    @Transactional
    public ServiceRateResponse createRate(UUID institutionId, ServiceRateRequest request) {
        CatalogServiceItem service = findService(institutionId, request.serviceId());
        validateRateRange(request);
        ServiceRate rate = new ServiceRate();
        rate.setInstitution(loadInstitution(institutionId));
        rate.setService(service);
        applyRate(rate, request);
        ServiceRate saved = rateRepository.save(rate);
        auditService.record("SERVICE_RATE_CREATED", "ServiceRate", saved.getId(), institutionId,
                "serviceId=" + service.getId() + "; amount=" + saved.getAmount());
        return toRateResponse(saved);
    }

    @Transactional
    public ServiceRateResponse updateRate(UUID institutionId, UUID id, ServiceRateRequest request) {
        ServiceRate rate = rateRepository.findByIdAndInstitutionId(id, institutionId)
                .orElseThrow(() -> new NotFoundException("La tarifa no existe"));
        CatalogServiceItem service = findService(institutionId, request.serviceId());
        validateRateRange(request);
        rate.setService(service);
        applyRate(rate, request);
        ServiceRate saved = rateRepository.save(rate);
        auditService.record("SERVICE_RATE_UPDATED", "ServiceRate", saved.getId(), institutionId,
                "serviceId=" + service.getId() + "; amount=" + saved.getAmount());
        return toRateResponse(saved);
    }

    // ------------------------------------------------------------------
    // Apoyo interno
    // ------------------------------------------------------------------

    private void replaceServices(Plan plan, List<UUID> serviceIds, UUID institutionId) {
        if (serviceIds == null) {
            return;
        }
        Set<UUID> desired = new LinkedHashSet<>(serviceIds);
        plan.getServices().removeIf(item -> !desired.contains(item.getService().getId()));

        Set<UUID> current = new LinkedHashSet<>();
        plan.getServices().forEach(item -> current.add(item.getService().getId()));

        for (UUID serviceId : desired) {
            if (current.contains(serviceId)) {
                continue;
            }
            CatalogServiceItem service = findService(institutionId, serviceId);
            PlanService item = new PlanService();
            item.setInstitution(plan.getInstitution());
            item.setPlan(plan);
            item.setService(service);
            plan.getServices().add(item);
        }
    }

    private void applyLevel(EducationalLevel level, EducationalLevelRequest request) {
        level.setName(request.name().trim());
        level.setCode(trimToNull(request.code()));
        level.setDescription(trimToNull(request.description()));
        level.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        level.setActive(request.active() == null || request.active());
    }

    private void applyPeriod(AcademicPeriod period, AcademicPeriodRequest request) {
        period.setName(request.name().trim());
        period.setStartDate(request.startDate());
        period.setEndDate(request.endDate());
        period.setActive(request.active() == null || request.active());
    }

    private void applyService(CatalogServiceItem service, CatalogServiceRequest request) {
        service.setName(request.name().trim());
        service.setCode(trimToNull(request.code()));
        service.setDescription(trimToNull(request.description()));
        service.setActive(request.active() == null || request.active());
    }

    private void applyRate(ServiceRate rate, ServiceRateRequest request) {
        rate.setAmount(request.amount().setScale(2, RoundingMode.HALF_UP));
        rate.setValidFrom(request.validFrom());
        rate.setValidTo(request.validTo());
        rate.setActive(request.active() == null || request.active());
    }

    private void validateRateRange(ServiceRateRequest request) {
        if (request.validTo() != null && request.validTo().isBefore(request.validFrom())) {
            throw new BadRequestException("La vigencia final no puede ser anterior a la inicial");
        }
        if (request.amount() != null && request.amount().compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("El monto de la tarifa no puede ser negativo");
        }
    }

    private void validateLevelName(UUID institutionId, String name, UUID currentId) {
        boolean duplicated = currentId == null
                ? levelRepository.existsByInstitutionIdAndNameIgnoreCase(institutionId, name.trim())
                : levelRepository.existsByInstitutionIdAndNameIgnoreCaseAndIdNot(institutionId, name.trim(),
                        currentId);
        if (duplicated) {
            throw new ConflictException("Ya existe un nivel educativo con ese nombre");
        }
    }

    private void validatePeriod(UUID institutionId, AcademicPeriodRequest request, UUID currentId) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new BadRequestException("La fecha de fin no puede ser anterior a la fecha de inicio");
        }
        boolean duplicated = currentId == null
                ? periodRepository.existsByInstitutionIdAndNameIgnoreCase(institutionId, request.name().trim())
                : periodRepository.existsByInstitutionIdAndNameIgnoreCaseAndIdNot(institutionId,
                        request.name().trim(), currentId);
        if (duplicated) {
            throw new ConflictException("Ya existe un período lectivo con ese nombre");
        }
    }

    private void validateServiceName(UUID institutionId, String name, UUID currentId) {
        boolean duplicated = currentId == null
                ? serviceItemRepository.existsByInstitutionIdAndNameIgnoreCase(institutionId, name.trim())
                : serviceItemRepository.existsByInstitutionIdAndNameIgnoreCaseAndIdNot(institutionId, name.trim(),
                        currentId);
        if (duplicated) {
            throw new ConflictException("Ya existe un servicio con ese nombre");
        }
    }

    private void validatePlanName(UUID institutionId, String name, UUID currentId) {
        boolean duplicated = currentId == null
                ? planRepository.existsByInstitutionIdAndNameIgnoreCase(institutionId, name.trim())
                : planRepository.existsByInstitutionIdAndNameIgnoreCaseAndIdNot(institutionId, name.trim(),
                        currentId);
        if (duplicated) {
            throw new ConflictException("Ya existe un plan con ese nombre");
        }
    }

    private Institution loadInstitution(UUID institutionId) {
        return institutionRepository.findById(institutionId)
                .orElseThrow(() -> new NotFoundException("La institución no existe"));
    }

    private EducationalLevelResponse toLevelResponse(EducationalLevel level) {
        return new EducationalLevelResponse(level.getId(), level.getName(), level.getCode(),
                level.getDescription(), level.getSortOrder(), level.isActive());
    }

    private AcademicPeriodResponse toPeriodResponse(AcademicPeriod period) {
        return new AcademicPeriodResponse(period.getId(), period.getName(), period.getStartDate(),
                period.getEndDate(), period.isActive());
    }

    private CatalogServiceResponse toServiceResponse(CatalogServiceItem service) {
        return new CatalogServiceResponse(service.getId(), service.getName(), service.getCode(),
                service.getDescription(), service.isActive());
    }

    private PlanResponse toPlanResponse(Plan plan) {
        List<PlanResponse.ServiceRef> services = plan.getServices().stream()
                .map(item -> new PlanResponse.ServiceRef(item.getService().getId(), item.getService().getName(),
                        item.getService().getCode()))
                .toList();
        return new PlanResponse(plan.getId(), plan.getName(), plan.getDescription(), plan.isActive(), services);
    }

    private ServiceRateResponse toRateResponse(ServiceRate rate) {
        return new ServiceRateResponse(rate.getId(), rate.getService().getId(), rate.getService().getName(),
                rate.getAmount(), rate.getValidFrom(), rate.getValidTo(), rate.isActive());
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
