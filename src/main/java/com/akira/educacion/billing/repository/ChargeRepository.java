package com.akira.educacion.billing.repository;

import com.akira.educacion.billing.domain.Charge;
import com.akira.educacion.billing.domain.ChargeOrigin;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface ChargeRepository extends JpaRepository<Charge, UUID> {

    @EntityGraph(attributePaths = { "student", "enrollment", "items", "items.concept" })
    Optional<Charge> findByIdAndInstitutionId(UUID id, UUID institutionId);

    /**
     * Bloqueo pesimista para aplicar o revertir pagos sin carreras entre
     * transacciones concurrentes sobre la misma obligación.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Charge c where c.id = :id and c.institution.id = :institutionId")
    Optional<Charge> lockByIdAndInstitutionId(@Param("id") UUID id,
            @Param("institutionId") UUID institutionId);

    @EntityGraph(attributePaths = { "student", "enrollment", "items", "items.concept" })
    Optional<Charge> findByInstitutionIdAndIdempotencyKey(UUID institutionId, String idempotencyKey);

    @EntityGraph(attributePaths = { "student", "enrollment", "items" })
    Optional<Charge> findByInstitutionIdAndEnrollmentIdAndOrigin(UUID institutionId, UUID enrollmentId,
            ChargeOrigin origin);

    boolean existsByInstitutionIdAndEnrollmentIdAndOrigin(UUID institutionId, UUID enrollmentId, ChargeOrigin origin);

    @EntityGraph(attributePaths = { "student", "enrollment" })
    @Query("""
            select c from Charge c
            where c.institution.id = :institutionId
              and (:hasStatus = false or cast(c.status as string) = :status)
              and (:hasStudent = false or c.student.id = :studentId)
              and (:hasIssueFrom = false or c.issueDate >= :issueFrom)
              and (:hasIssueTo = false or c.issueDate <= :issueTo)
              and (:hasDueFrom = false or c.dueDate >= :dueFrom)
              and (:hasDueTo = false or c.dueDate <= :dueTo)
            """)
    Page<Charge> search(@Param("institutionId") UUID institutionId,
            @Param("hasStatus") boolean hasStatus,
            @Param("status") String status,
            @Param("hasStudent") boolean hasStudent,
            @Param("studentId") UUID studentId,
            @Param("hasIssueFrom") boolean hasIssueFrom,
            @Param("issueFrom") LocalDate issueFrom,
            @Param("hasIssueTo") boolean hasIssueTo,
            @Param("issueTo") LocalDate issueTo,
            @Param("hasDueFrom") boolean hasDueFrom,
            @Param("dueFrom") LocalDate dueFrom,
            @Param("hasDueTo") boolean hasDueTo,
            @Param("dueTo") LocalDate dueTo,
            Pageable pageable);

    long countByInstitutionIdAndStatus(UUID institutionId, com.akira.educacion.billing.domain.ChargeStatus status);
}
