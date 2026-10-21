package com.akira.educacion.identity.repository;

import com.akira.educacion.identity.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByCode(String code);

    List<Role> findByCodeIn(Collection<String> codes);
}
