package com.akira.educacion.students.domain;

import com.akira.educacion.common.domain.AuditableEntity;
import com.akira.educacion.institution.domain.Institution;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Estudiante de una institución. La identificación es opcional y única dentro
 * de la institución, no a nivel global.
 */
@Entity
@Table(name = "student")
@Getter
@Setter
@NoArgsConstructor
public class Student extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "document_type", length = 30)
    private String documentType;

    @Column(name = "document_number", length = 40)
    private String documentNumber;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "admission_date", nullable = false)
    private LocalDate admissionDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StudentStatus status = StudentStatus.PRE_REGISTERED;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "phone", length = 40)
    private String phone;

    @Column(name = "address", length = 250)
    private String address;

    /** Observaciones internas; su lectura está protegida por permiso. */
    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    public String fullName() {
        return lastName + ", " + firstName;
    }
}
