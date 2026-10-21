package com.akira.educacion.catalog.api;

import com.akira.educacion.catalog.service.CatalogService;
import com.akira.educacion.institution.service.InstitutionScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/catalog")
@RequiredArgsConstructor
@Tag(name = "Catálogo", description = "Niveles, períodos, servicios, planes y tarifas")
public class CatalogController {

    private final CatalogService catalogService;
    private final InstitutionScope institutionScope;

    // ---------------------------- Niveles ----------------------------

    @GetMapping("/levels")
    @PreAuthorize("hasAuthority('CATALOG_READ')")
    public List<EducationalLevelResponse> listLevels(@RequestParam(required = false) UUID institutionId) {
        return catalogService.listLevels(institutionScope.resolve(institutionId));
    }

    @PostMapping("/levels")
    @PreAuthorize("hasAuthority('CATALOG_WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea un nivel educativo")
    public EducationalLevelResponse createLevel(@RequestParam(required = false) UUID institutionId,
            @Valid @RequestBody EducationalLevelRequest request) {
        return catalogService.createLevel(institutionScope.resolve(institutionId), request);
    }

    @PutMapping("/levels/{id}")
    @PreAuthorize("hasAuthority('CATALOG_WRITE')")
    @Operation(summary = "Edita un nivel educativo")
    public EducationalLevelResponse updateLevel(@PathVariable UUID id,
            @RequestParam(required = false) UUID institutionId,
            @Valid @RequestBody EducationalLevelRequest request) {
        return catalogService.updateLevel(institutionScope.resolve(institutionId), id, request);
    }

    // --------------------------- Períodos ----------------------------

    @GetMapping("/periods")
    @PreAuthorize("hasAuthority('CATALOG_READ')")
    public List<AcademicPeriodResponse> listPeriods(@RequestParam(required = false) UUID institutionId) {
        return catalogService.listPeriods(institutionScope.resolve(institutionId));
    }

    @PostMapping("/periods")
    @PreAuthorize("hasAuthority('CATALOG_WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea un período lectivo")
    public AcademicPeriodResponse createPeriod(@RequestParam(required = false) UUID institutionId,
            @Valid @RequestBody AcademicPeriodRequest request) {
        return catalogService.createPeriod(institutionScope.resolve(institutionId), request);
    }

    @PutMapping("/periods/{id}")
    @PreAuthorize("hasAuthority('CATALOG_WRITE')")
    @Operation(summary = "Edita un período lectivo")
    public AcademicPeriodResponse updatePeriod(@PathVariable UUID id,
            @RequestParam(required = false) UUID institutionId,
            @Valid @RequestBody AcademicPeriodRequest request) {
        return catalogService.updatePeriod(institutionScope.resolve(institutionId), id, request);
    }

    // --------------------------- Servicios ---------------------------

    @GetMapping("/services")
    @PreAuthorize("hasAuthority('CATALOG_READ')")
    public List<CatalogServiceResponse> listServices(@RequestParam(required = false) UUID institutionId) {
        return catalogService.listServices(institutionScope.resolve(institutionId));
    }

    @PostMapping("/services")
    @PreAuthorize("hasAuthority('CATALOG_WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea un servicio del catálogo")
    public CatalogServiceResponse createService(@RequestParam(required = false) UUID institutionId,
            @Valid @RequestBody CatalogServiceRequest request) {
        return catalogService.createService(institutionScope.resolve(institutionId), request);
    }

    @PutMapping("/services/{id}")
    @PreAuthorize("hasAuthority('CATALOG_WRITE')")
    @Operation(summary = "Edita un servicio del catálogo")
    public CatalogServiceResponse updateService(@PathVariable UUID id,
            @RequestParam(required = false) UUID institutionId,
            @Valid @RequestBody CatalogServiceRequest request) {
        return catalogService.updateService(institutionScope.resolve(institutionId), id, request);
    }

    // ----------------------------- Planes ----------------------------

    @GetMapping("/plans")
    @PreAuthorize("hasAuthority('CATALOG_READ')")
    public List<PlanResponse> listPlans(@RequestParam(required = false) UUID institutionId) {
        return catalogService.listPlans(institutionScope.resolve(institutionId));
    }

    @PostMapping("/plans")
    @PreAuthorize("hasAuthority('CATALOG_WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea un plan con sus servicios incluidos")
    public PlanResponse createPlan(@RequestParam(required = false) UUID institutionId,
            @Valid @RequestBody PlanRequest request) {
        return catalogService.createPlan(institutionScope.resolve(institutionId), request);
    }

    @PutMapping("/plans/{id}")
    @PreAuthorize("hasAuthority('CATALOG_WRITE')")
    @Operation(summary = "Edita un plan y reemplaza sus servicios incluidos")
    public PlanResponse updatePlan(@PathVariable UUID id, @RequestParam(required = false) UUID institutionId,
            @Valid @RequestBody PlanRequest request) {
        return catalogService.updatePlan(institutionScope.resolve(institutionId), id, request);
    }

    // ---------------------------- Tarifas ----------------------------

    @GetMapping("/rates")
    @PreAuthorize("hasAuthority('CATALOG_READ')")
    public List<ServiceRateResponse> listRates(@RequestParam(required = false) UUID institutionId) {
        return catalogService.listRates(institutionScope.resolve(institutionId));
    }

    @PostMapping("/rates")
    @PreAuthorize("hasAuthority('CATALOG_WRITE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea una tarifa con su vigencia")
    public ServiceRateResponse createRate(@RequestParam(required = false) UUID institutionId,
            @Valid @RequestBody ServiceRateRequest request) {
        return catalogService.createRate(institutionScope.resolve(institutionId), request);
    }

    @PutMapping("/rates/{id}")
    @PreAuthorize("hasAuthority('CATALOG_WRITE')")
    @Operation(summary = "Edita una tarifa. Las matrículas ya creadas conservan su precio acordado")
    public ServiceRateResponse updateRate(@PathVariable UUID id,
            @RequestParam(required = false) UUID institutionId,
            @Valid @RequestBody ServiceRateRequest request) {
        return catalogService.updateRate(institutionScope.resolve(institutionId), id, request);
    }
}
