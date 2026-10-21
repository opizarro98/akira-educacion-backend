package com.akira.educacion.catalog.repository;

import com.akira.educacion.catalog.domain.AcademicPeriod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AcademicPeriodRepository extends JpaRepository<AcademicPeriod, UUID> {

    List<AcademicPeriod> findByInstitutionIdOrderByStartDateDesc(UUID institutionId);

    Optional<AcademicPeriod> findByIdAndInstitutionId(UUID id, UUID institutionId);

    boolean existsByInstitutionIdAndNameIgnoreCase(UUID institutionId, String name);

    boolean existsByInstitutionIdAndNameIgnoreCaseAndIdNot(UUID institutionId, String name, UUID id);
}
