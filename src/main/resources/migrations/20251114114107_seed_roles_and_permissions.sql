-- Insertar roles del sistema
INSERT INTO roles (id, nombre, descripcion, activo) VALUES
(1, 'ROOT', 'Acceso total al sistema sin restricciones', 1),
(2, 'ADMIN', 'Acceso a todos los módulos excepto auditorías', 1),
(3, 'AUDITOR', 'Acceso de solo lectura a auditorías y reportes', 1),
(4, 'SUPERVISOR', 'Gestión de operaciones y aprobaciones', 1),
(5, 'OPERADOR', 'Creación y edición de transacciones básicas', 1),
(6, 'CONSULTOR', 'Solo lectura en módulos permitidos', 1);

-- Insertar permisos del sistema
-- Módulo: Dashboard
INSERT INTO permisos (modulo, accion, descripcion) VALUES
('DASHBOARD', 'READ', 'Ver el dashboard principal'),

-- Módulo: Inventario
('INVENTARIO', 'CREATE', 'Crear productos en inventario'),
('INVENTARIO', 'READ', 'Ver productos en inventario'),
('INVENTARIO', 'UPDATE', 'Editar productos en inventario'),
('INVENTARIO', 'DELETE', 'Eliminar productos del inventario'),

-- Módulo: Ingresos
('INGRESOS', 'CREATE', 'Crear ingresos de productos'),
('INGRESOS', 'READ', 'Ver ingresos de productos'),
('INGRESOS', 'UPDATE', 'Editar ingresos de productos'),
('INGRESOS', 'DELETE', 'Eliminar ingresos de productos'),

-- Módulo: Egresos
('EGRESOS', 'CREATE', 'Crear egresos de productos'),
('EGRESOS', 'READ', 'Ver egresos de productos'),
('EGRESOS', 'UPDATE', 'Editar egresos de productos'),
('EGRESOS', 'DELETE', 'Eliminar egresos de productos'),

-- Módulo: Préstamos
('PRESTAMOS', 'CREATE', 'Crear préstamos de productos'),
('PRESTAMOS', 'READ', 'Ver préstamos de productos'),
('PRESTAMOS', 'UPDATE', 'Editar préstamos de productos'),
('PRESTAMOS', 'DELETE', 'Eliminar préstamos de productos'),
('PRESTAMOS', 'APPROVE', 'Aprobar préstamos de productos'),

-- Módulo: Personas
('PERSONAS', 'CREATE', 'Crear personas en el sistema'),
('PERSONAS', 'READ', 'Ver personas en el sistema'),
('PERSONAS', 'UPDATE', 'Editar personas en el sistema'),
('PERSONAS', 'DELETE', 'Eliminar personas del sistema'),

-- Módulo: Usuarios
('USUARIOS', 'CREATE', 'Crear usuarios en el sistema'),
('USUARIOS', 'READ', 'Ver usuarios en el sistema'),
('USUARIOS', 'UPDATE', 'Editar usuarios en el sistema'),
('USUARIOS', 'DELETE', 'Eliminar usuarios del sistema'),
('USUARIOS', 'CHANGE_ROLE', 'Cambiar rol de usuarios'),

-- Módulo: Roles
('ROLES', 'CREATE', 'Crear roles en el sistema'),
('ROLES', 'READ', 'Ver roles en el sistema'),
('ROLES', 'UPDATE', 'Editar roles en el sistema'),
('ROLES', 'DELETE', 'Eliminar roles del sistema'),
('ROLES', 'ASSIGN_PERMISSIONS', 'Asignar permisos a roles'),

-- Módulo: Auditoría
('AUDITORIA', 'READ', 'Ver registros de auditoría'),
('AUDITORIA', 'EXPORT', 'Exportar registros de auditoría'),

-- Módulo: Reportes
('REPORTES', 'READ', 'Ver reportes del sistema'),
('REPORTES', 'GENERATE', 'Generar reportes del sistema'),
('REPORTES', 'EXPORT', 'Exportar reportes del sistema'),

-- Módulo: Configuración
('CONFIGURACION', 'READ', 'Ver configuración del sistema'),
('CONFIGURACION', 'UPDATE', 'Modificar configuración del sistema'),

-- Módulo: Sistema (Operaciones Destructivas y Visibilidad)
('SISTEMA', 'DESTRUCTIVE_OPERATIONS', 'Permite ejecutar eliminaciones físicas permanentes'),
('SISTEMA', 'VIEW_DELETED', 'Permite visualizar registros eliminados lógicamente'),

-- Permisos de RESTORE por módulo
('INVENTARIO', 'RESTORE', 'Restaurar productos eliminados'),
('INGRESOS', 'RESTORE', 'Restaurar ingresos invalidados'),
('EGRESOS', 'RESTORE', 'Restaurar egresos invalidados'),
('PRESTAMOS', 'RESTORE', 'Restaurar préstamos invalidados'),
('USUARIOS', 'RESTORE', 'Restaurar usuarios deshabilitados'),
('ROLES', 'RESTORE', 'Restaurar roles deshabilitados'),
('PERSONAS', 'RESTORE', 'Restaurar personas deshabilitadas');

-- ====================================
-- ASIGNACIÓN DE PERMISOS A ROLES
-- ====================================

-- 1. ROL ROOT: Acceso total sin restricciones
INSERT INTO rol_permisos (rol_id, permiso_id)
SELECT 1, id FROM permisos;

-- 2. ROL ADMIN: Acceso completo excepto operaciones destructivas
-- Tiene acceso a TODO incluyendo auditoría, roles, usuarios
-- Puede ver eliminados y restaurar
-- NO puede hacer eliminaciones físicas (DESTRUCTIVE_OPERATIONS es solo para ROOT)
INSERT INTO rol_permisos (rol_id, permiso_id)
SELECT 2, id FROM permisos
WHERE modulo != 'SISTEMA' OR accion != 'DESTRUCTIVE_OPERATIONS';

-- 3. ROL AUDITOR: Solo lectura de auditoría, reportes y puede ver eliminados
INSERT INTO rol_permisos (rol_id, permiso_id)
SELECT 3, id FROM permisos
WHERE (modulo = 'AUDITORIA' AND accion IN ('READ', 'EXPORT'))
   OR (modulo = 'REPORTES' AND accion IN ('READ', 'GENERATE', 'EXPORT'))
   OR (modulo = 'DASHBOARD' AND accion = 'READ')
   OR (modulo = 'SISTEMA' AND accion = 'VIEW_DELETED')
   OR (modulo = 'INVENTARIO' AND accion = 'READ')
   OR (modulo = 'INGRESOS' AND accion = 'READ')
   OR (modulo = 'EGRESOS' AND accion = 'READ')
   OR (modulo = 'PRESTAMOS' AND accion = 'READ')
   OR (modulo = 'PERSONAS' AND accion = 'READ')
   OR (modulo = 'USUARIOS' AND accion = 'READ');

-- 4. ROL SUPERVISOR: Gestión completa de operaciones + puede restaurar
INSERT INTO rol_permisos (rol_id, permiso_id)
SELECT 4, id FROM permisos
WHERE (modulo IN ('DASHBOARD', 'INVENTARIO', 'INGRESOS', 'EGRESOS', 'PRESTAMOS', 'PERSONAS', 'REPORTES')
       AND accion IN ('CREATE', 'READ', 'UPDATE', 'DELETE', 'APPROVE', 'GENERATE', 'EXPORT'))
   OR (modulo IN ('INVENTARIO', 'INGRESOS', 'EGRESOS', 'PRESTAMOS', 'PERSONAS') AND accion = 'RESTORE')
   OR (modulo = 'SISTEMA' AND accion = 'VIEW_DELETED');

-- 5. ROL OPERADOR: Operaciones básicas sin eliminación ni restauración
INSERT INTO rol_permisos (rol_id, permiso_id)
SELECT 5, id FROM permisos
WHERE modulo IN ('DASHBOARD', 'INVENTARIO', 'INGRESOS', 'EGRESOS', 'PRESTAMOS', 'PERSONAS')
  AND accion IN ('CREATE', 'READ', 'UPDATE');

-- 6. ROL CONSULTOR: Solo lectura en módulos operativos
INSERT INTO rol_permisos (rol_id, permiso_id)
SELECT 6, id FROM permisos
WHERE accion IN ('READ', 'GENERATE', 'EXPORT')
  AND modulo NOT IN ('AUDITORIA', 'ROLES', 'USUARIOS', 'CONFIGURACION');

-- Crear usuario SYSTEM para backups automáticos y operaciones del sistema
-- Este usuario NO tiene contraseña y NO puede iniciar sesión
-- Se usa solo para registrar operaciones automáticas del sistema
INSERT INTO users (name, username, password, rol_id, must_change_password)
VALUES ('SYSTEM', 'SYSTEM', '', NULL, 0)
ON DUPLICATE KEY UPDATE username = username; -- Evitar error si ya existe

