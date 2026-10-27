package com.akira.educacion.studentimport;

import com.akira.educacion.identity.domain.RoleCodes;
import com.akira.educacion.institution.domain.Institution;
import com.akira.educacion.support.StudentImportTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StudentImportIntegrationTest extends StudentImportTestSupport {

        private static final String PASSWORD = "Secreta123!";
        private static final String MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

        private final String today = LocalDate.now().toString();

        private Institution institution;
        private String token;
        private String secretaryToken;
        private String treasuryToken;
        private String directorToken;

        @BeforeEach
        void setUp() throws Exception {
                institution = newInstitution("Colegio Importaciones");

                newInstitutionAdmin(institution, "admin.importaciones@test.local", PASSWORD);
                newUser(institution, "secretaria.importaciones@test.local", PASSWORD, RoleCodes.SECRETARY);
                newUser(institution, "tesoreria.importaciones@test.local", PASSWORD, RoleCodes.TREASURY);
                newUser(institution, "direccion.importaciones@test.local", PASSWORD, RoleCodes.DIRECTOR);

                token = login("admin.importaciones@test.local", PASSWORD);
                secretaryToken = login("secretaria.importaciones@test.local", PASSWORD);
                treasuryToken = login("tesoreria.importaciones@test.local", PASSWORD);
                directorToken = login("direccion.importaciones@test.local", PASSWORD);
        }

        @Test
        void validaExcelCorrectoYNoPersisteEstudiantes() throws Exception {
                byte[] file = workbook(HEADERS,
                                row("Ana", "Pérez", today, "1711111111", "2010-01-01", "ana@test.local", "0999999999",
                                                "ACTIVE"),
                                row("Luis", "Gómez", today, null, "2011-02-02", null, null, null));

                MvcResult result = uploadPreview(secretaryToken, file, "estudiantes.xlsx")
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.status").value("PREVIEWED"))
                                .andExpect(jsonPath("$.totalRows").value(2))
                                .andExpect(jsonPath("$.validRows").value(2))
                                .andExpect(jsonPath("$.rows.length()").value(2))
                                .andExpect(jsonPath("$.rows[0].rowStatus").value("VALID"))
                                .andReturn();

                assertThat(importId(result)).isNotBlank();
                assertThat(studentCount(institution.getId())).isZero();

                mockMvc.perform(get("/api/v1/students")
                                .header(HttpHeaders.AUTHORIZATION, bearer(secretaryToken)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.totalElements").value(0));
        }

        @Test
        void rechazaExtensionNoPermitida() throws Exception {
                byte[] file = workbook(HEADERS, row("Ana", "Pérez", today, null, null, null, null, null));

                uploadPreview(secretaryToken, file, "estudiantes.csv")
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        }

        @Test
        void rechazaColumnasObligatoriasFaltantes() throws Exception {
                byte[] file = workbook(new String[] { "Nombres", "Apellidos" }, row("Ana", "Pérez"));

                uploadPreview(secretaryToken, file, "estudiantes.xlsx")
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                                .andExpect(jsonPath("$.detail")
                                                .value(org.hamcrest.Matchers.containsString("Fecha de ingreso")));
        }

        @Test
        void detectaDuplicadosDentroDelArchivo() throws Exception {
                byte[] file = workbook(HEADERS,
                                row("Ana", "Pérez", today, "1711111111", "2010-01-01", null, null, null),
                                row("Otra", "Persona", today, "1711111111", "2010-02-02", null, null, null),
                                row("Luis", "Silva", today, null, "2012-05-05", null, null, null),
                                row("Luis", "Silva", today, null, "2012-05-05", null, null, null));

                uploadPreview(secretaryToken, file, "duplicados.xlsx")
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.validRows").value(2))
                                .andExpect(jsonPath("$.duplicateRows").value(2))
                                .andExpect(jsonPath("$.rows[1].rowStatus").value("DUPLICATE_FILE"))
                                .andExpect(jsonPath("$.rows[1].duplicateType").value("DOCUMENT"))
                                .andExpect(jsonPath("$.rows[3].rowStatus").value("DUPLICATE_FILE"))
                                .andExpect(jsonPath("$.rows[3].duplicateType").value("NAME_BIRTHDATE"));
        }

        @Test
        void detectaCoincidenciaConEstudianteExistentePorIdentificacion() throws Exception {
                String existing = createStudentWithBirthDate(token, "Ana", "Pérez", "1712222222",
                                LocalDate.of(2010, 3, 3), "existente@test.local");

                byte[] file = workbook(HEADERS,
                                row("Ana", "Pérez", today, "1712222222", "2010-03-03", "otra@test.local", null,
                                                "ACTIVE"));

                uploadPreview(secretaryToken, file, "existentes.xlsx")
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.duplicateRows").value(1))
                                .andExpect(jsonPath("$.rows[0].rowStatus").value("DUPLICATE_EXISTING"))
                                .andExpect(jsonPath("$.rows[0].duplicateType").value("DOCUMENT"))
                                .andExpect(jsonPath("$.rows[0].existingStudentId").value(existing));
        }

        @Test
        void detectaCoincidenciaPorNombreYFechaDeNacimiento() throws Exception {
                String existing = createStudentWithBirthDate(token, "Ana", "Pérez", null,
                                LocalDate.of(2010, 3, 3), null);

                byte[] file = workbook(HEADERS,
                                row("Ana", "Pérez", today, null, "2010-03-03", null, null, null));

                uploadPreview(secretaryToken, file, "existentes-nombre.xlsx")
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.duplicateRows").value(1))
                                .andExpect(jsonPath("$.rows[0].rowStatus").value("DUPLICATE_EXISTING"))
                                .andExpect(jsonPath("$.rows[0].duplicateType").value("NAME_BIRTHDATE"))
                                .andExpect(jsonPath("$.rows[0].existingStudentId").value(existing));
        }

        @Test
        void importaSoloDespuesDeConfirmarEsIdempotenteYAudita() throws Exception {
                byte[] file = workbook(HEADERS,
                                row("Ana", "Pérez", today, "1713333333", "2010-01-01", null, null, "ACTIVE"),
                                row("Luis", "Gómez", today, "1714444444", "2011-02-02", null, null, "PRE_REGISTERED"));

                String id = importId(uploadPreview(secretaryToken, file, "importar.xlsx")
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.validRows").value(2))
                                .andReturn());
                assertThat(studentCount(institution.getId())).isZero();

                confirmImport(secretaryToken, id, false)
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("IMPORTED"))
                                .andExpect(jsonPath("$.importedRows").value(2));
                assertThat(studentCount(institution.getId())).isEqualTo(2);

                confirmImport(secretaryToken, id, false)
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("IMPORTED"))
                                .andExpect(jsonPath("$.importedRows").value(2));
                assertThat(studentCount(institution.getId())).isEqualTo(2);

                Integer audits = jdbcTemplate.queryForObject("""
                                select count(*) from audit_event
                                where action = 'STUDENT_IMPORT_CONFIRMED' and institution_id = ?
                                """, Integer.class, institution.getId());
                assertThat(audits).isNotNull().isGreaterThanOrEqualTo(1);
        }

        @Test
        void mantieneFilasInvalidasSinImportar() throws Exception {
                byte[] file = workbook(HEADERS,
                                row("Ana", "Pérez", today, "1715555555", "2010-01-01", null, null, "ACTIVE"),
                                row("Luis", "", today, "1716666666", "2011-02-02", null, null, "ACTIVE"));

                String id = importId(uploadPreview(secretaryToken, file, "mixto.xlsx")
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.validRows").value(1))
                                .andExpect(jsonPath("$.errorRows").value(1))
                                .andExpect(jsonPath("$.rows[1].rowStatus").value("ERROR"))
                                .andExpect(jsonPath("$.rows[1].errors[0]").value("Apellidos es obligatorio"))
                                .andReturn());

                confirmImport(secretaryToken, id, false)
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.importedRows").value(1))
                                .andExpect(jsonPath("$.errorRows").value(1));
                assertThat(studentCount(institution.getId())).isEqualTo(1);
        }

        @Test
        void noSobrescribeExistentesSinOpcionExplicita() throws Exception {
                createStudentWithBirthDate(token, "Ana", "Pérez", "1713333333", LocalDate.of(2010, 1, 1),
                                "viejo@test.local");

                byte[] file = workbook(HEADERS,
                                row("Ana", "Pérez", today, "1713333333", "2010-01-01", "nuevo@test.local", null,
                                                "ACTIVE"));

                String first = importId(uploadPreview(secretaryToken, file, "actualizar.xlsx")
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.rows[0].rowStatus").value("DUPLICATE_EXISTING"))
                                .andReturn());
                confirmImport(secretaryToken, first, false)
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.updatedRows").value(0));
                assertThat(emailOf("1713333333")).isEqualTo("viejo@test.local");

                String second = importId(uploadPreview(secretaryToken, file, "actualizar.xlsx")
                                .andExpect(status().isCreated())
                                .andReturn());
                confirmImport(secretaryToken, second, true)
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.updatedRows").value(1))
                                .andExpect(jsonPath("$.rows[0].updatedExisting").value(true));
                assertThat(emailOf("1713333333")).isEqualTo("nuevo@test.local");
                assertThat(studentCount(institution.getId())).isEqualTo(1);
        }

        @Test
        void loteObsoletoNoAbortaLaImportacionYSaltaLaFilaDuplicada() throws Exception {
                byte[] file = workbook(HEADERS,
                                row("Ana", "Pérez", today, "1719999999", "2010-01-01", null, null, "ACTIVE"));

                String first = importId(uploadPreview(secretaryToken, file, "lote-1.xlsx")
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.validRows").value(1))
                                .andReturn());
                String stale = importId(uploadPreview(secretaryToken, file, "lote-2.xlsx")
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.validRows").value(1))
                                .andReturn());

                confirmImport(secretaryToken, first, false)
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.importedRows").value(1));

                // El segundo lote se previsualizó antes de que existiera el estudiante:
                // al confirmar, la fila se conserva sin importar y no aborta el lote.
                confirmImport(secretaryToken, stale, false)
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.importedRows").value(0))
                                .andExpect(jsonPath("$.errorRows").value(1))
                                .andExpect(jsonPath("$.rows[0].rowStatus").value("ERROR"))
                                .andExpect(jsonPath("$.rows[0].errors[0]").value(
                                                org.hamcrest.Matchers.containsString("ya existe en la institución")));

                assertThat(studentCount(institution.getId())).isEqualTo(1);
        }

        @Test
        void registraValoresAnterioresYNuevosAlActualizarExistente() throws Exception {
                createStudentWithBirthDate(token, "Ana", "Pérez", "1713333333", LocalDate.of(2010, 1, 1),
                                "viejo@test.local");

                byte[] file = workbook(HEADERS,
                                row("Ana", "Pérez", today, "1713333333", "2010-01-01", "nuevo@test.local", null,
                                                "ACTIVE"));

                String id = importId(uploadPreview(secretaryToken, file, "auditar.xlsx")
                                .andExpect(status().isCreated())
                                .andReturn());
                confirmImport(secretaryToken, id, true)
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.updatedRows").value(1));

                String details = jdbcTemplate.queryForObject("""
                                select details from audit_event
                                where action = 'STUDENT_IMPORT_ROW_UPDATED' and institution_id = ?
                                order by occurred_at desc limit 1
                                """, String.class, institution.getId());

                assertThat(details)
                                .contains("before=", "after=", "viejo@test.local", "nuevo@test.local", "***3333")
                                .doesNotContain("1713333333");
        }

        @Test
        void rechazaAccesoCruzadoEntreInstituciones() throws Exception {
                Institution other = newInstitution("Colegio Ajeno Importaciones");
                newInstitutionAdmin(other, "admin.ajeno.importaciones@test.local", PASSWORD);
                String otherToken = login("admin.ajeno.importaciones@test.local", PASSWORD);

                byte[] file = workbook(HEADERS, row("Ana", "Pérez", today, "1717777777", null, null, null, null));

                MockMultipartFile multipartFile = new MockMultipartFile("file", "cruce.xlsx", MIME, file);
                mockMvc.perform(multipart("/api/v1/imports/students/preview")
                                .file(multipartFile)
                                .param("institutionId", other.getId().toString())
                                .header(HttpHeaders.AUTHORIZATION, bearer(secretaryToken)))
                                .andExpect(status().isForbidden());

                String otherImport = importId(uploadPreview(otherToken, file, "ajeno.xlsx")
                                .andExpect(status().isCreated())
                                .andReturn());

                mockMvc.perform(get("/api/v1/imports/students/" + otherImport)
                                .header(HttpHeaders.AUTHORIZATION, bearer(secretaryToken)))
                                .andExpect(status().isNotFound());

                confirmImport(secretaryToken, otherImport, false)
                                .andExpect(status().isNotFound());

                newPlatformAdmin("plataforma.importaciones@test.local", PASSWORD);
                String platformToken = login("plataforma.importaciones@test.local", PASSWORD);
                MockMultipartFile platformFile = new MockMultipartFile("file", "plataforma.xlsx", MIME, file);
                mockMvc.perform(multipart("/api/v1/imports/students/preview")
                                .file(platformFile)
                                .param("institutionId", other.getId().toString())
                                .header(HttpHeaders.AUTHORIZATION, bearer(platformToken)))
                                .andExpect(status().isCreated());

                MockMultipartFile missingScope = new MockMultipartFile("file", "sin-contexto.xlsx", MIME, file);
                mockMvc.perform(multipart("/api/v1/imports/students/preview")
                                .file(missingScope)
                                .header(HttpHeaders.AUTHORIZATION, bearer(platformToken)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void verificaPermisosPorRol() throws Exception {
                byte[] file = workbook(HEADERS, row("Ana", "Pérez", today, "1718888888", null, null, null, null));

                uploadPreview(directorToken, file, "direccion.xlsx")
                                .andExpect(status().isForbidden());
                mockMvc.perform(get("/api/v1/imports/students")
                                .header(HttpHeaders.AUTHORIZATION, bearer(directorToken)))
                                .andExpect(status().isOk());

                uploadPreview(treasuryToken, file, "tesoreria.xlsx")
                                .andExpect(status().isForbidden());
                mockMvc.perform(get("/api/v1/imports/students")
                                .header(HttpHeaders.AUTHORIZATION, bearer(treasuryToken)))
                                .andExpect(status().isOk());

                String id = importId(uploadPreview(secretaryToken, file, "secretaria.xlsx")
                                .andExpect(status().isCreated())
                                .andReturn());
                confirmImport(secretaryToken, id, false)
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("IMPORTED"));
        }

        private String emailOf(String documentNumber) {
                return jdbcTemplate.queryForObject(
                                "select email from student where document_number = ?", String.class, documentNumber);
        }
}
