-- Agregar permisos para el módulo de Backup
INSERT INTO permisos (modulo, accion, descripcion) VALUES
('BACKUP', 'CREATE', 'Crear copias de seguridad de la base de datos'),
('BACKUP', 'READ', 'Ver historial de copias de seguridad'),
('BACKUP', 'DELETE', 'Eliminar copias de seguridad antiguas');

-- Asignar permisos de BACKUP a ROOT
INSERT INTO rol_permisos (rol_id, permiso_id)
SELECT r.id, p.id
FROM roles r, permisos p
WHERE r.nombre = 'ROOT' AND p.modulo = 'BACKUP';

-- Asignar permisos de BACKUP a ADMIN
INSERT INTO rol_permisos (rol_id, permiso_id)
SELECT r.id, p.id
FROM roles r, permisos p
WHERE r.nombre = 'ADMIN' AND p.modulo = 'BACKUP';

