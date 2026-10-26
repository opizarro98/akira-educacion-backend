package com.akira.educacion.studentimport.domain;

import com.akira.educacion.students.domain.StudentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Fila leída del Excel con su resultado de validación. Los errores se guardan
 * separados por saltos de línea.
 */
@Entity
@Table(name = "student_import_row")
@Getter
@Setter
@NoArgsConstructor
public class StudentImportRow {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "institution_id", nullable = false)
    private UUID institutionId;

    @Column(name = "import_id", nullable = false)
    private UUID importId;

    @Column(name = "row_number", nullable = false)
    private int rowNumber;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "document_type", length = 30)
    private String documentType;

    @Column(name = "document_number", length = 40)
    private String documentNumber;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "admission_date")
    private LocalDate admissionDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "student_status", length = 20)
    private StudentStatus studentStatus;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "phone", length = 40)
    private String phone;

    @Column(name = "address", length = 250)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(name = "row_status", nullable = false, length = 30)
    private ImportRowStatus rowStatus = ImportRowStatus.VALID;

    @Column(name = "errors", columnDefinition = "text")
    private String errors;

    @Enumerated(EnumType.STRING)
    @Column(name = "duplicate_type", length = 30)
    private ImportDuplicateType duplicateType;

    @Column(name = "existing_student_id")
    private UUID existingStudentId;

    @Column(name = "created_student_id")
    private UUID createdStudentId;

    @Column(name = "updated_existing", nullable = false)
    private boolean updatedExisting;
}
