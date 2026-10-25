package com.akira.educacion.billing.repository;

import com.akira.educacion.billing.domain.BillingSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BillingSettingsRepository extends JpaRepository<BillingSettings, UUID> {

    Optional<BillingSettings> findByInstitutionId(UUID institutionId);
}
