# Akira Educación — backend

API REST del sistema de gestión escolar multiinstitución. **Fase 1**: fundamentos, autenticación JWT, instituciones, usuarios, roles, permisos y auditoría. **Fase 2**: gestión escolar, catálogo y matrículas. **Fase 3A**: obligaciones de cobro y conceptos financieros. **Fase 3B**: pagos, aplicaciones y recibos internos. **Fase 4**: egresos, caja y reportes financieros. **Fase 5A**: comunicaciones asistidas e importación de estudiantes desde Excel.

- Java 25, Spring Boot 4.1.1, Spring Framework 7, Hibernate 7.
- PostgreSQL + Flyway (`ddl-auto=validate`).
- Spring Security con JWT HS256 (Nimbus JOSE).
- OpenAPI/Swagger con springdoc 3.1.1.

## Requisitos

- JDK 25.
- PostgreSQL local con un usuario que pueda crear esquemas.
- No se necesita Maven instalado: se usa `./mvnw` (Maven 3.10.0).

## Configuración

1. Copiar `.env.example` a `.env` y ajustar los valores. **No versionar `.env`.**
2. Exportar las variables en la terminal (Spring no lee `.env` automáticamente):

```sh
set -a
source .env
set +a
```

3. Perfiles disponibles:
   - `dev` (por defecto): datos de demostración, schema `akira_dev`, Swagger habilitado.
   - `test`: schema aislado `akira_test`, limpia y migra al iniciar las pruebas.
   - `prod`: sin datos de demostración, `DB_SCHEMA=public`, OpenAPI deshabilitado por defecto.

`JWT_SECRET` debe tener al menos 32 bytes. En `dev` existe un valor por defecto **solo para desarrollo**; en producción es obligatorio definirlo.

### Base de datos local

El proyecto espera una base local. Si el usuario de PostgreSQL no tiene permiso `CREATEDB`, se puede usar un esquema dedicado (como se hizo en la verificación de la fase):

```sh
DB_URL=jdbc:postgresql://localhost:5432/<base_local>
DB_USER=<usuario>
DB_PASSWORD=<clave>
DB_SCHEMA=akira_dev
```

Flyway crea el esquema si no existe (`create-schemas=true`). Las pruebas usan `akira_test`.

## Ejecutar

```sh
env DB_URL=... DB_USER=... DB_PASSWORD=... DB_SCHEMA=akira_dev ./mvnw spring-boot:run
```

La API queda en `http://localhost:8080/api/v1`.
Swagger UI: `http://localhost:8080/swagger-ui.html`.
OpenAPI JSON: `http://localhost:8080/v3/api-docs`.

## Pruebas

Las pruebas usan el perfil `test` y un esquema aislado. **No usan la base externa compartida.**

```sh
env DB_URL=jdbc:postgresql://localhost:5432/<base_local> \
    DB_USER=<usuario> DB_PASSWORD=<clave> DB_SCHEMA=akira_test \
    ./mvnw -B clean test
```

Resultado verificado: **114 pruebas, 0 fallos** (Fase 1 + Fase 2 + Fase 3A + Fase 3B + Fase 4 + Fase 5A).

## Endpoints de la Fase 1

| Método | Ruta | Permiso |
|---|---|---|
| POST | `/api/v1/auth/login` | público |
| GET | `/api/v1/auth/me` | autenticado |
| GET | `/api/v1/institutions` | `PLATFORM_INSTITUTION_MANAGE` |
| POST | `/api/v1/institutions` | `PLATFORM_INSTITUTION_MANAGE` |
| GET | `/api/v1/institutions/me` | `INSTITUTION_READ` |
| PUT | `/api/v1/institutions/me` | `INSTITUTION_WRITE` |
| GET | `/api/v1/institutions/{id}` | `INSTITUTION_READ` + aislamiento |
| PUT | `/api/v1/institutions/{id}` | `PLATFORM_INSTITUTION_MANAGE` |
| GET | `/api/v1/users` | `USER_READ` |
| POST | `/api/v1/users` | `USER_WRITE` |

Los errores usan `ProblemDetail` (RFC 9457) con `code` y, en validaciones, `errors`.

## Endpoints de la Fase 2

Todas las rutas institucionales resuelven la institución desde el token. Un usuario
institucional que envíe `institutionId` de otra institución recibe `403`. `PLATFORM_ADMIN`
no pertenece a una institución: debe indicar `?institutionId=` explícitamente y el acceso
queda registrado como `PLATFORM_GLOBAL_ACCESS` en la auditoría.

### Estudiantes

| Método | Ruta | Permiso |
|---|---|---|
| GET | `/api/v1/students?search=&status=&page=&size=` | `STUDENT_READ` |
| POST | `/api/v1/students` | `STUDENT_WRITE` |
| GET | `/api/v1/students/{id}` | `STUDENT_READ` |
| PUT | `/api/v1/students/{id}` | `STUDENT_WRITE` |
| DELETE | `/api/v1/students/{id}` | `STUDENT_WRITE` (409 si tiene matrículas) |
| GET | `/api/v1/students/{id}/representatives` | `REPRESENTATIVE_READ` |
| POST | `/api/v1/students/{id}/representatives` | `REPRESENTATIVE_WRITE` |
| PUT | `/api/v1/students/{id}/representatives/{representativeId}` | `REPRESENTATIVE_WRITE` |
| DELETE | `/api/v1/students/{id}/representatives/{representativeId}` | `REPRESENTATIVE_WRITE` |

Las observaciones (`notes`) solo se devuelven con `STUDENT_NOTES_READ`; en caso contrario
llegan como `null` con `notesRestricted = true`.

### Representantes

| Método | Ruta | Permiso |
|---|---|---|
| GET | `/api/v1/representatives?search=&page=&size=` | `REPRESENTATIVE_READ` |
| POST | `/api/v1/representatives` | `REPRESENTATIVE_WRITE` |
| GET | `/api/v1/representatives/{id}` | `REPRESENTATIVE_READ` |
| PUT | `/api/v1/representatives/{id}` | `REPRESENTATIVE_WRITE` |
| GET | `/api/v1/representatives/{id}/students` | `REPRESENTATIVE_READ` |

### Catálogo

| Método | Ruta | Permiso |
|---|---|---|
| GET/POST | `/api/v1/catalog/levels` | `CATALOG_READ` / `CATALOG_WRITE` |
| PUT | `/api/v1/catalog/levels/{id}` | `CATALOG_WRITE` |
| GET/POST | `/api/v1/catalog/periods` | `CATALOG_READ` / `CATALOG_WRITE` |
| PUT | `/api/v1/catalog/periods/{id}` | `CATALOG_WRITE` |
| GET/POST | `/api/v1/catalog/services` | `CATALOG_READ` / `CATALOG_WRITE` |
| PUT | `/api/v1/catalog/services/{id}` | `CATALOG_WRITE` |
| GET/POST | `/api/v1/catalog/plans` | `CATALOG_READ` / `CATALOG_WRITE` |
| PUT | `/api/v1/catalog/plans/{id}` | `CATALOG_WRITE` |
| GET/POST | `/api/v1/catalog/rates` | `CATALOG_READ` / `CATALOG_WRITE` |
| PUT | `/api/v1/catalog/rates/{id}` | `CATALOG_WRITE` |

### Matrículas

| Método | Ruta | Permiso |
|---|---|---|
| GET | `/api/v1/enrollments?status=&studentId=&academicPeriodId=&page=&size=` | `ENROLLMENT_READ` |
| POST | `/api/v1/enrollments` | `ENROLLMENT_WRITE` |
| GET | `/api/v1/enrollments/{id}` | `ENROLLMENT_READ` |
| PUT | `/api/v1/enrollments/{id}` | `ENROLLMENT_WRITE` (solo `DRAFT`) |
| POST | `/api/v1/enrollments/{id}/confirm` | `ENROLLMENT_WRITE` |
| POST | `/api/v1/enrollments/{id}/cancel` | `ENROLLMENT_WRITE` |
| POST | `/api/v1/enrollments/{id}/complete` | `ENROLLMENT_WRITE` |
| GET | `/api/v1/enrollments/{id}/history` | `ENROLLMENT_READ` |

## Endpoints de la Fase 3A

Solo obligaciones de cobro y conceptos financieros. **No** se registran pagos ni recibos.
Los importes usan `NUMERIC(12,2)` y `BigDecimal`; el detalle conserva la tasa de IVA y el
precio acordado como instantánea histórica.

### Obligaciones de cobro

| Método | Ruta | Permiso |
|---|---|---|
| GET | `/api/v1/billing/charges?status=&studentId=&issueFrom=&issueTo=&dueFrom=&dueTo=&page=&size=` | `CHARGE_READ` |
| GET | `/api/v1/billing/charges/{id}` | `CHARGE_READ` |
| GET | `/api/v1/billing/charges/{id}/balance` | `CHARGE_READ` |
| GET | `/api/v1/billing/charges/{id}/history` | `CHARGE_READ` |
| POST | `/api/v1/billing/charges` | `CHARGE_WRITE` |
| POST | `/api/v1/billing/charges/from-enrollment/{enrollmentId}` | `CHARGE_WRITE` |
| POST | `/api/v1/billing/charges/generate-monthly` | `CHARGE_WRITE` |
| POST | `/api/v1/billing/charges/{id}/void` | `CHARGE_WRITE` |

- Estados: `PENDING`, `PARTIAL`, `PAID`, `VOID`. En esta fase solo se emiten `PENDING` y se anulan como `VOID`.
- Una obligación no se edita: solo se crea, se consulta o se anula con motivo.
- `from-enrollment` exige una matrícula `CONFIRMED`, usa el precio acordado y la tasa de IVA congelada en la matrícula.
- La generación es idempotente: el cobro inicial usa `ENROLLMENT_INITIAL:{enrollmentId}` y la generación
  mensual `MONTHLY:{studentId}:{conceptId}:{YYYY-MM}`. Las obligaciones manuales aceptan `idempotencyKey`.
- `generate-monthly` es siempre una acción explícita del usuario; no hay procesos programados.

### Conceptos y configuración

| Método | Ruta | Permiso |
|---|---|---|
| GET | `/api/v1/billing/concepts` | `BILLING_CONCEPT_READ` |
| POST | `/api/v1/billing/concepts` | `BILLING_CONCEPT_WRITE` |
| PUT | `/api/v1/billing/concepts/{id}` | `BILLING_CONCEPT_WRITE` |
| GET | `/api/v1/billing/settings` | `BILLING_SETTINGS_READ` |
| PUT | `/api/v1/billing/settings` | `BILLING_SETTINGS_WRITE` |

La configuración define frecuencia (`MONTHLY`, `BIMONTHLY`, `QUARTERLY`, `ANNUAL`, `ONE_TIME`),
día de emisión (1-28) y días hasta el vencimiento (0-120).

## Endpoints de la Fase 3B

Pagos, aplicaciones y recibos internos. No se implementan egresos, caja ni reportes avanzados.

### Pagos

| Método | Ruta | Permiso |
|---|---|---|
| GET | `/api/v1/billing/payments?status=&studentId=&method=&receivedFrom=&receivedTo=&page=&size=` | `PAYMENT_READ` |
| GET | `/api/v1/billing/payments/{id}` | `PAYMENT_READ` |
| GET | `/api/v1/billing/payments/{id}/history` | `PAYMENT_READ` |
| POST | `/api/v1/billing/payments` | `PAYMENT_WRITE` |
| POST | `/api/v1/billing/payments/{id}/confirm` | `PAYMENT_VERIFY` |
| POST | `/api/v1/billing/payments/{id}/void` | `PAYMENT_VOID` |
| POST | `/api/v1/billing/payments/{id}/allocations` | `PAYMENT_APPLY` |
| POST | `/api/v1/billing/payments/{id}/allocations/{allocationId}/reverse` | `PAYMENT_APPLY` |

- Estados: `PENDING_VERIFICATION`, `CONFIRMED`, `VOID`.
- Medios: `CASH`, `BANK_TRANSFER`.
- El efectivo se confirma de inmediato solo si el usuario tiene `PAYMENT_VERIFY`; en caso contrario queda pendiente.
- Las transferencias quedan pendientes y requieren referencia bancaria.
- Un pago pendiente o anulado no cuenta como dinero disponible ni puede aplicarse.
- La confirmación emite un recibo interno con numeración secuencial por institución.
- Anular un pago revierte automáticamente sus aplicaciones vigentes, restaura el saldo de las
  obligaciones, anula el recibo y conserva historial y auditoría. La operación es atómica.

### Aplicaciones

```json
{
  "idempotencyKey": "operacion-uuid",
  "allocations": [
    { "chargeId": "uuid", "amount": 100.00 },
    { "chargeId": "uuid", "amount": 75.00 }
  ]
}
```

- El pago y las obligaciones se bloquean con bloqueo pesimista.
- La suma de aplicaciones no supera el importe disponible del pago ni el saldo de cada obligación.
- Solo se aplican pagos `CONFIRMED` a obligaciones no anuladas del mismo estudiante e institución.
- La operación es transaccional: cualquier fallo revierte todo el lote.
- Reintentar con la misma `idempotencyKey` devuelve el pago sin duplicar aplicaciones.
- Revertir una aplicación restaura el saldo y deja motivo, actor y fecha.

### Recibos internos

| Método | Ruta | Permiso |
|---|---|---|
| GET | `/api/v1/billing/receipts?paymentId=&studentId=&page=&size=` | `RECEIPT_READ` |
| GET | `/api/v1/billing/receipts/{id}` | `RECEIPT_READ` |
| GET | `/api/v1/billing/receipts/by-payment/{paymentId}` | `RECEIPT_READ` |

## Endpoints de la Fase 4

Egresos, movimientos de caja y reportes financieros. Los movimientos de caja son derivados:
no pueden crearse ni editarse desde la API. Cada pago o egreso confirmado genera como máximo
un movimiento por tipo y origen, protegido por una clave única en base de datos.

### Categorías de egreso

| Método | Ruta | Permiso |
|---|---|---|
| GET | `/api/v1/finance/expense-categories?active=` | `EXPENSE_CATEGORY_READ` |
| GET | `/api/v1/finance/expense-categories/{id}` | `EXPENSE_CATEGORY_READ` |
| POST | `/api/v1/finance/expense-categories` | `EXPENSE_CATEGORY_WRITE` |
| PUT | `/api/v1/finance/expense-categories/{id}` | `EXPENSE_CATEGORY_WRITE` |

### Egresos

| Método | Ruta | Permiso |
|---|---|---|
| GET | `/api/v1/finance/expenses?status=&categoryId=&from=&to=&page=&size=` | `EXPENSE_READ` |
| GET | `/api/v1/finance/expenses/{id}` | `EXPENSE_READ` |
| GET | `/api/v1/finance/expenses/{id}/history` | `EXPENSE_READ` |
| POST | `/api/v1/finance/expenses` | `EXPENSE_WRITE` |
| PUT | `/api/v1/finance/expenses/{id}` | `EXPENSE_WRITE` (solo `DRAFT`) |
| POST | `/api/v1/finance/expenses/{id}/confirm` | `EXPENSE_WRITE` |
| POST | `/api/v1/finance/expenses/{id}/void` | `EXPENSE_WRITE` |

- Estados: `DRAFT`, `CONFIRMED`, `VOID`.
- Un `DRAFT` es editable y no afecta la caja.
- Confirmar genera una salida de caja (`EXPENSE_OUT`) y bloquea la edición.
- Anular un egreso confirmado genera `EXPENSE_VOID_REVERSAL`; anular un borrador no genera movimiento.
- El historial es append-only y la anulación exige motivo.

### Movimientos de caja

| Método | Ruta | Permiso |
|---|---|---|
| GET | `/api/v1/finance/cash-movements?type=&from=&to=&page=&size=` | `CASH_MOVEMENT_READ` |

Tipos: `PAYMENT_IN`, `EXPENSE_OUT`, `PAYMENT_VOID_REVERSAL`, `EXPENSE_VOID_REVERSAL`.
El rango de fechas se interpreta en la zona horaria de la institución con límites
`[inicio del día, inicio del día siguiente)`.

### Reportes y dashboard

| Método | Ruta | Permiso |
|---|---|---|
| GET | `/api/v1/finance/reports/confirmed-payments?from=&to=` | `FINANCE_REPORT_READ` |
| GET | `/api/v1/finance/reports/pending-payments?from=&to=` | `FINANCE_REPORT_READ` |
| GET | `/api/v1/finance/reports/voided-payments?from=&to=` | `FINANCE_REPORT_READ` |
| GET | `/api/v1/finance/reports/outstanding?from=&to=` | `FINANCE_REPORT_READ` |
| GET | `/api/v1/finance/reports/expenses?from=&to=` | `FINANCE_REPORT_READ` |
| GET | `/api/v1/finance/reports/cash-flow?from=&to=` | `FINANCE_REPORT_READ` |
| GET | `/api/v1/finance/reports/cash-summary?from=&to=` | `FINANCE_REPORT_READ` |
| GET | `/api/v1/finance/dashboard?from=&to=` | `FINANCE_REPORT_READ` |

Sin fechas se usa el mes en curso de la institución. Cada reporte incluye en su cabecera la
institución, el período, la zona horaria, la moneda, si incluye anulaciones y los importes
devengado, cobrado y su diferencia. Los reportes globales de `PLATFORM_ADMIN` quedan auditados
con `PLATFORM_GLOBAL_ACCESS`; cada consulta registra además `FINANCE_REPORT_VIEWED`.

## Endpoints de la Fase 5A

### Comunicaciones asistidas

No hay integración con WhatsApp Business API. El backend genera un enlace `wa.me` y registra
la comunicación, pero **no puede confirmar el envío ni la entrega**. El estado `OPENED` solo
indica que el usuario abrió el enlace.

| Método | Ruta | Permiso |
|---|---|---|
| GET | `/api/v1/communications/templates?active=` | `MESSAGE_TEMPLATE_READ` |
| GET | `/api/v1/communications/templates/{id}` | `MESSAGE_TEMPLATE_READ` |
| POST | `/api/v1/communications/templates` | `MESSAGE_TEMPLATE_WRITE` |
| PUT | `/api/v1/communications/templates/{id}` | `MESSAGE_TEMPLATE_WRITE` |
| GET | `/api/v1/communications?status=&studentId=&page=&size=` | `COMMUNICATION_READ` |
| GET | `/api/v1/communications/{id}` | `COMMUNICATION_READ` |
| POST | `/api/v1/communications/preview` | `COMMUNICATION_WRITE` |
| POST | `/api/v1/communications` | `COMMUNICATION_WRITE` |
| POST | `/api/v1/communications/{id}/opened` | `COMMUNICATION_WRITE` |
| POST | `/api/v1/communications/{id}/failed` | `COMMUNICATION_WRITE` |

- Estados: `DRAFT`, `READY`, `OPENED`, `FAILED`.
- Marcadores: `{{estudiante}}`, `{{representante}}`, `{{institucion}}`, `{{fecha}}` y
  `{{detalle_financiero}}`. Los marcadores desconocidos se rechazan.
- El representante debe estar vinculado al estudiante, autorizado para comunicaciones y tener
  teléfono válido. Los teléfonos locales ecuatorianos se normalizan a `593...`.
- La información financiera solo se incluye si el usuario la escribe y confirma; la vista previa
  devuelve el mensaje exacto y el enlace codificado en UTF-8.

### Importación de estudiantes

| Método | Ruta | Permiso |
|---|---|---|
| POST | `/api/v1/imports/students/preview` (multipart, campo `file`) | `STUDENT_IMPORT_WRITE` |
| POST | `/api/v1/imports/students/{id}/confirm` | `STUDENT_IMPORT_WRITE` |
| GET | `/api/v1/imports/students?page=&size=` | `STUDENT_IMPORT_READ` |
| GET | `/api/v1/imports/students/{id}` | `STUDENT_IMPORT_READ` |

- Solo se aceptan archivos `.xlsx` de hasta 5 MB y 2 000 filas.
- La vista previa valida y guarda el lote, pero **no crea estudiantes**. La institución nunca
  se toma del archivo.
- Columnas obligatorias: `Nombres`, `Apellidos`, `Fecha de ingreso`.
- Duplicados: identificación, o nombre+apellido+fecha de nacimiento cuando no hay identificación.
  También se detectan duplicados dentro del archivo y coincidencias con estudiantes existentes.
- La confirmación importa solo filas válidas, es idempotente al reintentar el mismo lote y
  puede actualizar coincidencias existentes solo con `allowUpdateExisting=true`.
- El archivo se procesa en memoria; solo se guardan nombre, tamaño y hash SHA-256 para auditoría.

## Usuarios de desarrollo

Se crean solo con el perfil `dev` y `APP_SEED_ENABLED=true`:

| Correo | Rol | Contraseña |
|---|---|---|
| `admin@akira.local` | `PLATFORM_ADMIN` | definida en `APP_SEED_ADMIN_PASSWORD` (por defecto, solo desarrollo) |
| `admin@demo.local` | `INSTITUTION_ADMIN` | definida en `APP_SEED_DEMO_PASSWORD` (por defecto, solo desarrollo) |

No usar estos valores en producción. Cambiarlos o desactivar `APP_SEED_ENABLED`.

## Estructura

```text
src/main/java/com/akira/educacion
├── audit/           # eventos de auditoría
├── billing/         # obligaciones, pagos, aplicaciones, recibos y configuración
├── catalog/         # niveles, períodos, servicios, planes y tarifas
├── common/          # errores, paginación, seguridad y configuración
├── communication/   # plantillas, comunicaciones y enlaces wa.me
├── config/          # datos de arranque de desarrollo
├── enrollment/      # matrículas, servicios adicionales e historial
├── finance/         # egresos, categorías, caja, reportes y dashboard
├── identity/        # usuarios, roles, permisos, login
├── institution/     # instituciones, configuración y alcance institucional
├── representatives/ # representantes
├── studentimport/   # carga, validación e importación de estudiantes
└── students/        # estudiantes y vínculos con representantes
src/main/resources/db/migration
├── V1__initial_schema.sql
├── V2__seed_roles_and_permissions.sql
├── V3__school_management_schema.sql
├── V4__phase2_permissions.sql
├── V5__billing_schema.sql
├── V6__phase3_permissions.sql
├── V7__payments_schema.sql
├── V8__phase3b_permissions.sql
├── V9__finance_schema.sql
├── V10__phase4_permissions.sql
├── V11__communications_and_student_import_schema.sql
└── V12__phase5a_permissions.sql
```

## Notas de seguridad

- La institución se deriva del token JWT (`inst`), nunca de un parámetro del cliente.
- `PLATFORM_ADMIN` no pertenece a ninguna institución y solo usa operaciones explícitas.
- Las contraseñas se almacenan con BCrypt.
- Las operaciones sobre instituciones y usuarios dejan eventos en `audit_event`.
- `ddl-auto=validate`: el esquema pertenece a Flyway.
