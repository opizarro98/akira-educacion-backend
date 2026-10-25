package com.akira.educacion.billing.repository;

import com.akira.educacion.billing.domain.Payment;
import com.akira.educacion.billing.domain.PaymentStatus;
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

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    @EntityGraph(attributePaths = { "student", "allocations", "allocations.charge" })
    Optional<Payment> findByIdAndInstitutionId(UUID id, UUID institutionId);

    @EntityGraph(attributePaths = { "student", "allocations", "allocations.charge" })
    Optional<Payment> findByInstitutionIdAndIdempotencyKey(UUID institutionId, String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id and p.institution.id = :institutionId")
    Optional<Payment> lockByIdAndInstitutionId(@Param("id") UUID id,
            @Param("institutionId") UUID institutionId);

    @EntityGraph(attributePaths = { "student" })
    @Query("""
            select p from Payment p
            where p.institution.id = :institutionId
              and (:hasStatus = false or cast(p.status as string) = :status)
              and (:hasStudent = false or p.student.id = :studentId)
              and (:hasMethod = false or cast(p.method as string) = :method)
              and (:hasReceivedFrom = false or p.receivedDate >= :receivedFrom)
              and (:hasReceivedTo = false or p.receivedDate <= :receivedTo)
            """)
    Page<Payment> search(@Param("institutionId") UUID institutionId,
            @Param("hasStatus") boolean hasStatus,
            @Param("status") String status,
            @Param("hasStudent") boolean hasStudent,
            @Param("studentId") UUID studentId,
            @Param("hasMethod") boolean hasMethod,
            @Param("method") String method,
            @Param("hasReceivedFrom") boolean hasReceivedFrom,
            @Param("receivedFrom") LocalDate receivedFrom,
            @Param("hasReceivedTo") boolean hasReceivedTo,
            @Param("receivedTo") LocalDate receivedTo,
            Pageable pageable);

    long countByInstitutionIdAndStatus(UUID institutionId, PaymentStatus status);

    boolean existsByInstitutionIdAndStudentId(UUID institutionId, UUID studentId);
}
