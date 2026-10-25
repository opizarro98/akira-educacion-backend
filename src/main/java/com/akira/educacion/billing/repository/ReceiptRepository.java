package com.akira.educacion.billing.repository;

import com.akira.educacion.billing.domain.Receipt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ReceiptRepository extends JpaRepository<Receipt, UUID> {

    @EntityGraph(attributePaths = { "payment", "payment.student", "payment.institution" })
    Optional<Receipt> findByIdAndInstitutionId(UUID id, UUID institutionId);

    @EntityGraph(attributePaths = { "payment", "payment.student" })
    Optional<Receipt> findByInstitutionIdAndPaymentId(UUID institutionId, UUID paymentId);

    boolean existsByInstitutionIdAndPaymentId(UUID institutionId, UUID paymentId);

    @EntityGraph(attributePaths = { "payment", "payment.student" })
    @Query("""
            select r from Receipt r
            where r.institution.id = :institutionId
              and (:hasPayment = false or r.payment.id = :paymentId)
              and (:hasStudent = false or r.payment.student.id = :studentId)
            """)
    Page<Receipt> search(@Param("institutionId") UUID institutionId,
            @Param("hasPayment") boolean hasPayment,
            @Param("paymentId") UUID paymentId,
            @Param("hasStudent") boolean hasStudent,
            @Param("studentId") UUID studentId,
            Pageable pageable);
}
