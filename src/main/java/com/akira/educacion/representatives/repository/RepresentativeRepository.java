package com.akira.educacion.representatives.repository;

import com.akira.educacion.representatives.domain.Representative;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RepresentativeRepository extends JpaRepository<Representative, UUID> {

    Optional<Representative> findByIdAndInstitutionId(UUID id, UUID institutionId);

    boolean existsByInstitutionIdAndDocumentNumberIgnoreCase(UUID institutionId, String documentNumber);

    boolean existsByInstitutionIdAndDocumentNumberIgnoreCaseAndIdNot(UUID institutionId, String documentNumber,
            UUID id);

    @Query("""
            select r from Representative r
            where r.institution.id = :institutionId
              and (lower(r.fullName) like :searchPattern
                   or lower(coalesce(r.documentNumber, '')) like :searchPattern
                   or lower(coalesce(r.email, '')) like :searchPattern)
            """)
    Page<Representative> search(@Param("institutionId") UUID institutionId,
            @Param("searchPattern") String searchPattern,
            Pageable pageable);
}
