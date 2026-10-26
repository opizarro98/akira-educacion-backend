package com.akira.educacion.studentimport.repository;

import com.akira.educacion.studentimport.domain.StudentImport;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface StudentImportRepository extends JpaRepository<StudentImport, UUID> {

    Optional<StudentImport> findByIdAndInstitutionId(UUID id, UUID institutionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from StudentImport i where i.id = :id and i.institution.id = :institutionId")
    Optional<StudentImport> lockByIdAndInstitutionId(@Param("id") UUID id,
            @Param("institutionId") UUID institutionId);

    Page<StudentImport> findByInstitutionIdOrderByCreatedAtDesc(UUID institutionId, Pageable pageable);
}
