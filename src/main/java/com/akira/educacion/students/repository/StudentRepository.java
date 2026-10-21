package com.akira.educacion.students.repository;

import com.akira.educacion.students.domain.Student;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface StudentRepository extends JpaRepository<Student, UUID> {

    Optional<Student> findByIdAndInstitutionId(UUID id, UUID institutionId);

    boolean existsByInstitutionIdAndDocumentNumberIgnoreCase(UUID institutionId, String documentNumber);

    boolean existsByInstitutionIdAndDocumentNumberIgnoreCaseAndIdNot(UUID institutionId, String documentNumber,
            UUID id);

    /**
     * Búsqueda con patrones ya preparados por el servicio ({@code %valor%} o
     * {@code %} para "sin filtro"). Se evitan parámetros opcionales comparados
     * con {@code null} para que PostgreSQL no tenga que inferir el tipo de un
     * parámetro sin contexto.
     */
    @Query("""
            select s from Student s
            where s.institution.id = :institutionId
              and cast(s.status as string) like :statusPattern
              and (lower(s.firstName) like :searchPattern
                   or lower(s.lastName) like :searchPattern
                   or lower(coalesce(s.documentNumber, '')) like :searchPattern)
            """)
    Page<Student> search(@Param("institutionId") UUID institutionId,
            @Param("statusPattern") String statusPattern,
            @Param("searchPattern") String searchPattern,
            Pageable pageable);
}
