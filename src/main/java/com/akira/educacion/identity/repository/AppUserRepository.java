package com.akira.educacion.identity.repository;

import com.akira.educacion.identity.domain.AppUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    @EntityGraph(attributePaths = { "roles", "roles.permissions", "institution" })
    @Query("select u from AppUser u where lower(u.email) = lower(:email)")
    Optional<AppUser> findWithRolesByEmail(@Param("email") String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByIdAndActiveTrue(UUID id);

    Page<AppUser> findByInstitutionId(UUID institutionId, Pageable pageable);

    @EntityGraph(attributePaths = { "roles", "institution" })
    @Query("""
            select u from AppUser u
            where u.institution.id = :institutionId
              and (lower(u.email) like :pattern or lower(u.fullName) like :pattern)
            """)
    Page<AppUser> searchByInstitution(@Param("institutionId") UUID institutionId,
            @Param("pattern") String pattern, Pageable pageable);

    @EntityGraph(attributePaths = { "roles", "institution" })
    @Query("select u from AppUser u where u.id = :id and u.institution.id = :institutionId")
    Optional<AppUser> findByIdAndInstitutionId(@Param("id") UUID id,
            @Param("institutionId") UUID institutionId);
}
