-- Migración de transacciones mono-producto a multi-producto
-- VERSIÓN DESARROLLO: Sin migración de datos - Solo estructura
-- Esta versión elimina datos existentes y crea la nueva estructura limpia

-- Paso 1: Deshabilitar validación de foreign keys temporalmente
SET FOREIGN_KEY_CHECKS = 0;

-- Paso 2: Eliminar tabla transactions existente si existe
DROP TABLE IF EXISTS transactions;

-- Paso 3: Crear tabla transactions con nueva estructura (sin product_id ni quantity)
CREATE TABLE transactions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    type VARCHAR(20) NOT NULL,
    date DATE DEFAULT (CURRENT_DATE),
    user_id INT NOT NULL,
    person_id INT,
    notes TEXT,
    total_items INT DEFAULT 0,

    -- Campos para soft delete / invalidación
    status ENUM('active', 'cancelled', 'invalidated') DEFAULT 'active',
    invalidated_at TIMESTAMP NULL DEFAULT NULL,
    invalidated_by INT NULL DEFAULT NULL,
    invalidation_reason TEXT NULL,

    -- Campos para gestión de préstamos (FASE 10)
    loan_code VARCHAR(20) UNIQUE NULL COMMENT 'Código único del préstamo (ej: P-JA20251127-001)',
    loan_status ENUM('pending', 'returned', 'paid', 'replaced', 'overdue') DEFAULT 'pending',
    expected_return_date DATE NULL COMMENT 'Fecha esperada de devolución del préstamo',
    actual_return_date DATE NULL COMMENT 'Fecha real de devolución',
    related_transaction_id INT NULL COMMENT 'ID de transacción relacionada (para devoluciones)',
    return_type ENUM('same_product', 'purchased', 'replaced', 'other') NULL,
    return_notes TEXT NULL COMMENT 'Notas sobre la devolución del préstamo',

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (user_id) REFERENCES users(id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    FOREIGN KEY (person_id) REFERENCES people(id)
        ON UPDATE CASCADE
        ON DELETE SET NULL,
    FOREIGN KEY (invalidated_by) REFERENCES users(id)
        ON UPDATE CASCADE
        ON DELETE SET NULL,
    FOREIGN KEY (related_transaction_id) REFERENCES transactions(id)
        ON UPDATE CASCADE
        ON DELETE SET NULL,

    INDEX idx_transactions_status (status),
    INDEX idx_loan_status (loan_status),
    INDEX idx_loan_code (loan_code),
    INDEX idx_related_transaction (related_transaction_id),

    CHECK (type IN ('in', 'out', 'loan'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Paso 4: Asegurar que transaction_items existe con estructura correcta
DROP TABLE IF EXISTS transaction_items;

CREATE TABLE transaction_items (
    id INT AUTO_INCREMENT PRIMARY KEY,
    transaction_id INT NOT NULL,
    product_id INT NOT NULL,
    quantity DECIMAL(10,2) NOT NULL,
    notes TEXT,
    status ENUM('active', 'cancelled') DEFAULT 'active',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (transaction_id) REFERENCES transactions(id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    INDEX idx_transaction (transaction_id),
    INDEX idx_product (product_id),
    INDEX idx_transaction_items_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Paso 5: Reactivar validación de foreign keys
SET FOREIGN_KEY_CHECKS = 1;

