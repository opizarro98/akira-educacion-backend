-- Permisos financieros granulares de la Fase 5B.
--
-- Decisión de negocio documentada: el MVP todavía no define segregación de
-- funciones; los permisos se separan técnicamente para poder asignarlos por
-- rol en el futuro, pero se conceden a los mismos roles que antes tenían
-- EXPENSE_WRITE / PAYMENT_APPLY. No se agregan capacidades nuevas.

INSERT INTO permission (id, code, description) VALUES
    ('11111111-1111-1111-1111-111111111701', 'EXPENSE_CREATE', 'Registrar egresos en borrador'),
    ('11111111-1111-1111-1111-111111111702', 'EXPENSE_UPDATE', 'Editar egresos en borrador'),
    ('11111111-1111-1111-1111-111111111703', 'EXPENSE_CONFIRM', 'Confirmar egresos y generar salida de caja'),
    ('11111111-1111-1111-1111-111111111704', 'EXPENSE_VOID', 'Anular egresos con motivo'),
    ('11111111-1111-1111-1111-111111111705', 'PAYMENT_REVERSE', 'Revertir aplicaciones de pago con motivo');

-- PLATFORM_ADMIN: alcance global explícito y auditado.
INSERT INTO role_permission (role_id, permission_id) VALUES
    ('22222222-2222-2222-2222-222222222201', '11111111-1111-1111-1111-111111111701'),
    ('22222222-2222-2222-2222-222222222201', '11111111-1111-1111-1111-111111111702'),
    ('22222222-2222-2222-2222-222222222201', '11111111-1111-1111-1111-111111111703'),
    ('22222222-2222-2222-2222-222222222201', '11111111-1111-1111-1111-111111111704'),
    ('22222222-2222-2222-2222-222222222201', '11111111-1111-1111-1111-111111111705');

-- Fase 5B: PLATFORM_ADMIN administra usuarios mediante contexto explícito.
INSERT INTO role_permission (role_id, permission_id) VALUES
    ('22222222-2222-2222-2222-222222222201', '11111111-1111-1111-1111-111111111104'),
    ('22222222-2222-2222-2222-222222222201', '11111111-1111-1111-1111-111111111105');

-- INSTITUTION_ADMIN: administración financiera completa de su institución.
INSERT INTO role_permission (role_id, permission_id) VALUES
    ('22222222-2222-2222-2222-222222222202', '11111111-1111-1111-1111-111111111701'),
    ('22222222-2222-2222-2222-222222222202', '11111111-1111-1111-1111-111111111702'),
    ('22222222-2222-2222-2222-222222222202', '11111111-1111-1111-1111-111111111703'),
    ('22222222-2222-2222-2222-222222222202', '11111111-1111-1111-1111-111111111704'),
    ('22222222-2222-2222-2222-222222222202', '11111111-1111-1111-1111-111111111705');

-- TREASURY: operación financiera completa.
INSERT INTO role_permission (role_id, permission_id) VALUES
    ('22222222-2222-2222-2222-222222222204', '11111111-1111-1111-1111-111111111701'),
    ('22222222-2222-2222-2222-222222222204', '11111111-1111-1111-1111-111111111702'),
    ('22222222-2222-2222-2222-222222222204', '11111111-1111-1111-1111-111111111703'),
    ('22222222-2222-2222-2222-222222222204', '11111111-1111-1111-1111-111111111704'),
    ('22222222-2222-2222-2222-222222222204', '11111111-1111-1111-1111-111111111705');

-- EXPENSE_WRITE queda obsoleto: ningún endpoint lo verifica y se retiran sus
-- concesiones para evitar permisos zombis en los tokens emitidos.
DELETE FROM role_permission
WHERE permission_id = (SELECT id FROM permission WHERE code = 'EXPENSE_WRITE');
