package com.akira.educacion.communication.repository;

import com.akira.educacion.communication.domain.Communication;
import com.akira.educacion.communication.domain.CommunicationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CommunicationRepository extends JpaRepository<Communication, UUID> {

    @EntityGraph(attributePaths = { "student", "representative", "template" })
    Optional<Communication> findByIdAndInstitutionId(UUID id, UUID institutionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Communication c where c.id = :id and c.institution.id = :institutionId")
    Optional<Communication> lockByIdAndInstitutionId(@Param("id") UUID id,
            @Param("institutionId") UUID institutionId);

    @EntityGraph(attributePaths = { "student", "representative", "template" })
    @Query("""
            select c from Communication c
            where c.institution.id = :institutionId
              and (:hasStatus = false or cast(c.status as string) = :status)
              and (:hasStudent = false or c.student.id = :studentId)
            """)
    Page<Communication> search(@Param("institutionId") UUID institutionId,
            @Param("hasStatus") boolean hasStatus,
            @Param("status") String status,
            @Param("hasStudent") boolean hasStudent,
            @Param("studentId") UUID studentId,
            Pageable pageable);

    long countByInstitutionIdAndStatus(UUID institutionId, CommunicationStatus status);
}
