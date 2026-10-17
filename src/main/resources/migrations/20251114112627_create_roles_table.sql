CREATE TABLE IF NOT EXISTS roles (
    id INT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(50) UNIQUE NOT NULL,
    descripcion VARCHAR(255),
    status ENUM('active', 'inactive', 'deleted') DEFAULT 'active',
    activo TINYINT(1) DEFAULT 1,
    deleted_at TIMESTAMP NULL DEFAULT NULL,
    deleted_by INT NULL DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_roles_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Agregar foreign key para deleted_by después de crear la tabla users
-- ALTER TABLE roles ADD CONSTRAINT fk_roles_deleted_by
-- FOREIGN KEY (deleted_by) REFERENCES users(id) ON DELETE SET NULL;

