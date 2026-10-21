package com.akira.educacion.catalog.repository;

import com.akira.educacion.catalog.domain.EducationalLevel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EducationalLevelRepository extends JpaRepository<EducationalLevel, UUID> {

    List<EducationalLevel> findByInstitutionIdOrderBySortOrderAscNameAsc(UUID institutionId);

    Optional<EducationalLevel> findByIdAndInstitutionId(UUID id, UUID institutionId);

    boolean existsByInstitutionIdAndNameIgnoreCase(UUID institutionId, String name);

    boolean existsByInstitutionIdAndNameIgnoreCaseAndIdNot(UUID institutionId, String name, UUID id);
}
