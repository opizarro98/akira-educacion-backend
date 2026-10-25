package com.akira.educacion.billing.repository;

import com.akira.educacion.billing.domain.BillingConcept;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BillingConceptRepository extends JpaRepository<BillingConcept, UUID> {

    List<BillingConcept> findByInstitutionIdOrderByNameAsc(UUID institutionId);

    Optional<BillingConcept> findByIdAndInstitutionId(UUID id, UUID institutionId);

    Optional<BillingConcept> findByInstitutionIdAndCodeIgnoreCase(UUID institutionId, String code);

    boolean existsByInstitutionIdAndNameIgnoreCase(UUID institutionId, String name);

    boolean existsByInstitutionIdAndNameIgnoreCaseAndIdNot(UUID institutionId, String name, UUID id);

    boolean existsByInstitutionIdAndCodeIgnoreCase(UUID institutionId, String code);

    boolean existsByInstitutionIdAndCodeIgnoreCaseAndIdNot(UUID institutionId, String code, UUID id);
}
