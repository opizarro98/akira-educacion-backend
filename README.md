# Akira Educación — backend

API REST del sistema de gestión escolar multiinstitución. **Fase 1**: fundamentos, autenticación JWT, instituciones, usuarios, roles, permisos y auditoría.

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

Resultado verificado: **22 pruebas, 0 fallos**.

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
├── common/          # errores, paginación, seguridad y configuración
├── config/          # datos de arranque de desarrollo
├── identity/        # usuarios, roles, permisos, login
└── institution/     # instituciones y su configuración
src/main/resources/db/migration
├── V1__initial_schema.sql
└── V2__seed_roles_and_permissions.sql
```

## Notas de seguridad

- La institución se deriva del token JWT (`inst`), nunca de un parámetro del cliente.
- `PLATFORM_ADMIN` no pertenece a ninguna institución y solo usa operaciones explícitas.
- Las contraseñas se almacenan con BCrypt.
- Las operaciones sobre instituciones y usuarios dejan eventos en `audit_event`.
- `ddl-auto=validate`: el esquema pertenece a Flyway.
