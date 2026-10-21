package com.akira.educacion.catalog.repository;

import com.akira.educacion.catalog.domain.Plan;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanRepository extends JpaRepository<Plan, UUID> {

    @EntityGraph(attributePaths = "services")
    List<Plan> findByInstitutionIdOrderByNameAsc(UUID institutionId);

    Optional<Plan> findByIdAndInstitutionId(UUID id, UUID institutionId);

    boolean existsByInstitutionIdAndNameIgnoreCase(UUID institutionId, String name);

    boolean existsByInstitutionIdAndNameIgnoreCaseAndIdNot(UUID institutionId, String name, UUID id);
}
