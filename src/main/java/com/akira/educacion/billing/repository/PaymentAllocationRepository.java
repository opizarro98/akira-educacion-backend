package com.akira.educacion.billing.repository;

import com.akira.educacion.billing.domain.PaymentAllocation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, UUID> {

    @EntityGraph(attributePaths = { "charge", "payment" })
    List<PaymentAllocation> findByInstitutionIdAndPaymentIdOrderByCreatedAtAsc(UUID institutionId, UUID paymentId);

    @EntityGraph(attributePaths = { "charge", "payment" })
    List<PaymentAllocation> findByInstitutionIdAndChargeIdOrderByCreatedAtAsc(UUID institutionId, UUID chargeId);

    @EntityGraph(attributePaths = { "charge", "payment" })
    Optional<PaymentAllocation> findByIdAndInstitutionIdAndPaymentId(UUID id, UUID institutionId, UUID paymentId);

    boolean existsByInstitutionIdAndPaymentIdAndOperationKey(UUID institutionId, UUID paymentId,
            String operationKey);

    boolean existsByInstitutionIdAndPaymentIdAndChargeIdAndReversedFalse(UUID institutionId, UUID paymentId,
            UUID chargeId);
}
