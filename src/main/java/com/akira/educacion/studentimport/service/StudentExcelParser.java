package com.akira.educacion.studentimport.service;

import com.akira.educacion.common.api.BadRequestException;
import com.akira.educacion.students.domain.StudentStatus;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Lectura y validación de archivos Excel de estudiantes.
 *
 * <p>
 * No guarda el archivo ni escribe estudiantes: devuelve filas validadas para
 * que
 * el servicio decida si corresponde persistir el lote de previsualización.
 */
@Component
public class StudentExcelParser {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final int MAX_ROWS = 2000;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private static final Map<String, String> REQUIRED_COLUMNS = Map.of(
            "firstname", "Nombres",
            "lastname", "Apellidos",
            "admissiondate", "Fecha de ingreso");

    private static final Map<String, Set<String>> ALIASES = Map.of(
            "firstname", Set.of("firstname", "nombres", "nombre", "primernombre"),
            "lastname", Set.of("lastname", "apellidos", "apellido", "primerapellido"),
            "admissiondate", Set.of("admissiondate", "fechaingreso", "fechadeingreso", "ingreso"),
            "documenttype", Set.of("documenttype", "tipodocumento", "tipodeidentificacion"),
            "documentnumber", Set.of("documentnumber", "identificacion", "documento", "cedula",
                    "numerodocumento", "numerodeidentificacion"),
            "birthdate", Set.of("birthdate", "fechanacimiento", "fechadenacimiento"),
            "email", Set.of("email", "correo", "correoelectronico"),
            "phone", Set.of("phone", "telefono", "celular"),
            "status", Set.of("status", "estado"),
            "address", Set.of("address", "direccion"));

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("d/M/uuuu"),
            DateTimeFormatter.ofPattern("dd/MM/uuuu"),
            DateTimeFormatter.ofPattern("d-M-uuuu"),
            DateTimeFormatter.ofPattern("dd-MM-uuuu"),
            DateTimeFormatter.ofPattern("uuuu/M/d"));

    private final DataFormatter formatter = new DataFormatter(Locale.of("es", "EC"));

    public ParsedFile parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Debe adjuntar un archivo Excel");
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new BadRequestException("Solo se permiten archivos Excel con extensión .xlsx");
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException ex) {
            throw new BadRequestException("No fue posible leer el archivo adjunto");
        }
        if (content.length == 0) {
            throw new BadRequestException("El archivo está vacío");
        }
        if (content.length > MAX_FILE_SIZE) {
            throw new BadRequestException("El archivo supera el tamaño máximo permitido (5 MB)");
        }

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            Sheet sheet = workbook.getNumberOfSheets() == 0 ? null : workbook.getSheetAt(0);
            if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) {
                throw new BadRequestException("El archivo no contiene datos");
            }
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new BadRequestException("El archivo no contiene una fila de encabezados");
            }

            Map<String, Integer> headers = readHeaders(headerRow);
            List<String> missing = REQUIRED_COLUMNS.entrySet().stream()
                    .filter(entry -> !headers.containsKey(entry.getKey()))
                    .map(Map.Entry::getValue)
                    .toList();
            if (!missing.isEmpty()) {
                throw new BadRequestException("Faltan columnas obligatorias: " + String.join(", ", missing));
            }

            List<ParsedStudentRow> rows = new ArrayList<>();
            for (int index = headerRow.getRowNum() + 1; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (row == null || isBlank(row, headers)) {
                    continue;
                }
                if (rows.size() >= MAX_ROWS) {
                    throw new BadRequestException("El archivo supera el máximo de " + MAX_ROWS + " filas");
                }
                rows.add(parseRow(row, headers, index + 1));
            }
            if (rows.isEmpty()) {
                throw new BadRequestException("El archivo no contiene filas de estudiantes");
            }
            return new ParsedFile(fileName, content.length, sha256(content), rows);
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BadRequestException("El archivo no es un Excel .xlsx válido");
        }
    }

    private Map<String, Integer> readHeaders(Row headerRow) {
        Map<String, Integer> headers = new HashMap<>();
        for (int column = 0; column < headerRow.getLastCellNum(); column++) {
            Cell cell = headerRow.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (cell == null) {
                continue;
            }
            String normalized = normalize(headerText(cell));
            if (normalized.isEmpty()) {
                continue;
            }
            for (Map.Entry<String, Set<String>> entry : ALIASES.entrySet()) {
                if (entry.getValue().contains(normalized)) {
                    headers.putIfAbsent(entry.getKey(), column);
                    break;
                }
            }
        }
        return headers;
    }

    private ParsedStudentRow parseRow(Row row, Map<String, Integer> headers, int rowNumber) {
        List<String> errors = new ArrayList<>();

        String firstName = text(row, headers, "firstname");
        String lastName = text(row, headers, "lastname");
        String documentType = text(row, headers, "documenttype");
        String documentNumber = normalizeDocument(text(row, headers, "documentnumber"));
        String email = text(row, headers, "email");
        String phone = text(row, headers, "phone");
        String address = text(row, headers, "address");
        StudentStatus status = parseStatus(text(row, headers, "status"), errors);

        if (firstName == null) {
            errors.add("Nombres es obligatorio");
        } else if (firstName.length() > 100) {
            errors.add("Nombres supera los 100 caracteres");
        }
        if (lastName == null) {
            errors.add("Apellidos es obligatorio");
        } else if (lastName.length() > 100) {
            errors.add("Apellidos supera los 100 caracteres");
        }
        if (documentType != null && documentType.length() > 30) {
            errors.add("Tipo de documento supera los 30 caracteres");
        }
        if (documentNumber != null && documentNumber.length() > 40) {
            errors.add("Identificación supera los 40 caracteres");
        }
        if (email != null && (!EMAIL.matcher(email).matches() || email.length() > 150)) {
            errors.add("Correo electrónico no válido");
        }
        if (phone != null && phone.length() > 40) {
            errors.add("Teléfono supera los 40 caracteres");
        }
        if (address != null && address.length() > 250) {
            errors.add("Dirección supera los 250 caracteres");
        }

        LocalDate birthDate = readDate(row, headers, "birthdate", "Fecha de nacimiento", errors);
        if (birthDate != null && !birthDate.isBefore(LocalDate.now())) {
            errors.add("La fecha de nacimiento debe ser anterior a hoy");
        }
        LocalDate admissionDate = readDate(row, headers, "admissiondate", "Fecha de ingreso", errors);
        if (admissionDate == null && !hasDateError(errors, "Fecha de ingreso")) {
            errors.add("Fecha de ingreso es obligatoria");
        }

        return new ParsedStudentRow(rowNumber, firstName, lastName, documentType, documentNumber, birthDate,
                admissionDate, status, email, phone, address, List.copyOf(errors));
    }

    private LocalDate readDate(Row row, Map<String, Integer> headers, String key, String label,
            List<String> errors) {
        Integer column = headers.get(key);
        if (column == null) {
            return null;
        }
        Cell cell = row.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        String value = formatter.formatCellValue(cell).trim();
        if (value.isEmpty()) {
            return null;
        }
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(value, format);
            } catch (DateTimeParseException ignored) {
                // Se prueba el siguiente formato.
            }
        }
        errors.add(label + " no tiene un formato válido (use AAAA-MM-DD)");
        return null;
    }

    private StudentStatus parseStatus(String value, List<String> errors) {
        if (value == null) {
            return null;
        }
        try {
            return StudentStatus.valueOf(value.trim().toUpperCase(Locale.ROOT).replace(' ', '_'));
        } catch (IllegalArgumentException ex) {
            errors.add("Estado no válido: " + value);
            return null;
        }
    }

    private boolean hasDateError(List<String> errors, String label) {
        return errors.stream().anyMatch(error -> error.startsWith(label));
    }

    private boolean isBlank(Row row, Map<String, Integer> headers) {
        for (Integer column : headers.values()) {
            Cell cell = row.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (cell != null && !headerText(cell).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String text(Row row, Map<String, Integer> headers, String key) {
        Integer column = headers.get(key);
        if (column == null) {
            return null;
        }
        Cell cell = row.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) {
            return null;
        }
        String value = headerText(cell);
        return value.isBlank() ? null : value;
    }

    private String headerText(Cell cell) {
        return formatter.formatCellValue(cell).trim();
    }

    private String normalizeDocument(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalize(String value) {
        String decomposed = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
    }

    private String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (Exception ex) {
            throw new IllegalStateException("No fue posible calcular el hash del archivo", ex);
        }
    }

    public record ParsedFile(String fileName, long size, String hash, List<ParsedStudentRow> rows) {
    }

    public record ParsedStudentRow(
            int rowNumber,
            String firstName,
            String lastName,
            String documentType,
            String documentNumber,
            LocalDate birthDate,
            LocalDate admissionDate,
            StudentStatus studentStatus,
            String email,
            String phone,
            String address,
            List<String> errors) {

        public boolean valid() {
            return errors.isEmpty();
        }
    }
}
