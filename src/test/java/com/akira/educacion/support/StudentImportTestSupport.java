package com.akira.educacion.support;

import com.jayway.jsonpath.JsonPath;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Helpers de la Fase 5A para construir archivos Excel y ejercitar la
 * importación a través de la API real.
 */
public abstract class StudentImportTestSupport extends SchoolTestSupport {

    protected static final String[] HEADERS = {
            "Nombres", "Apellidos", "Fecha de ingreso", "Identificación",
            "Fecha de nacimiento", "Correo", "Teléfono", "Estado"
    };

    protected static String[] row(String... values) {
        return values;
    }

    protected byte[] workbook(String[] headers, String[]... rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Estudiantes");
            Row headerRow = sheet.createRow(0);
            for (int column = 0; column < headers.length; column++) {
                headerRow.createCell(column).setCellValue(headers[column]);
            }
            for (int index = 0; index < rows.length; index++) {
                Row excelRow = sheet.createRow(index + 1);
                String[] values = rows[index];
                for (int column = 0; column < values.length; column++) {
                    if (values[column] != null) {
                        excelRow.createCell(column).setCellValue(values[column]);
                    }
                }
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    protected ResultActions uploadPreview(String token, byte[] content, String fileName) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", fileName,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content);
        return mockMvc.perform(multipart("/api/v1/imports/students/preview")
                .file(file)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    protected ResultActions confirmImport(String token, String importId, Boolean allowUpdateExisting) throws Exception {
        return mockMvc.perform(post("/api/v1/imports/students/" + importId + "/confirm")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"allowUpdateExisting":%s}
                        """.formatted(allowUpdateExisting)));
    }

    protected String createStudentWithBirthDate(String token, String firstName, String lastName, String document,
            LocalDate birthDate, String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/students")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "firstName":"%s",
                          "lastName":"%s",
                          "documentNumber":%s,
                          "birthDate":"%s",
                          "admissionDate":"%s",
                          "status":"ACTIVE",
                          "email":%s
                        }
                        """.formatted(firstName, lastName, jsonString(document), birthDate, LocalDate.now(),
                        jsonString(email))))
                .andReturn();
        return readId(result);
    }

    protected String importId(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    protected int studentCount(java.util.UUID institutionId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from student where institution_id = ?", Integer.class, institutionId);
        return count == null ? 0 : count;
    }
}
