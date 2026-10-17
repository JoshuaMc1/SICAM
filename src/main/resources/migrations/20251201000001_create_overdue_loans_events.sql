-- =====================================================
-- CONFIGURACIÓN: ACTIVAR EVENT SCHEDULER
-- =====================================================
SET GLOBAL event_scheduler = ON;

-- =====================================================
-- EVENTO 1: Actualizar préstamos vencidos a las 8:35 AM
-- =====================================================

DROP EVENT IF EXISTS update_overdue_loans_morning;
DELIMITER $$

CREATE EVENT update_overdue_loans_morning
ON SCHEDULE
    EVERY 1 DAY
    STARTS (CURDATE() + INTERVAL '08:35:00' HOUR_SECOND)
COMMENT 'Actualiza préstamos vencidos cada día a las 8:35 AM'
DO
BEGIN
    DECLARE affected_count INT DEFAULT 0;

    -- Actualizar préstamos vencidos
    UPDATE transactions
    SET loan_status = 'overdue'
    WHERE type = 'loan'
      AND loan_status = 'pending'
      AND status = 'active'
      AND expected_return_date < CURDATE();

    -- Cantidad afectada
    SET affected_count = ROW_COUNT();

    -- Registrar auditoría
    IF affected_count > 0 THEN
        INSERT INTO audit (
            user_id, action, table_name, record_id, details, created_at
        ) VALUES (
            1,
            'scheduled_update',
            'transactions',
            NULL,
            CONCAT('Evento automático: ', affected_count,
                   ' préstamo(s) actualizado(s) a estado VENCIDO a las 08:35 AM'),
            NOW()
        );
    END IF;
END$$

DELIMITER ;

-- =====================================================
-- EVENTO 2: Actualizar préstamos vencidos a las 9:00 AM
-- =====================================================

DROP EVENT IF EXISTS update_overdue_loans_backup;
DELIMITER $$

CREATE EVENT update_overdue_loans_backup
ON SCHEDULE
    EVERY 1 DAY
    STARTS (CURDATE() + INTERVAL '09:00:00' HOUR_SECOND)
COMMENT 'Actualiza préstamos vencidos cada día a las 9:00 AM como respaldo'
DO
BEGIN
    DECLARE affected_count INT DEFAULT 0;

    -- Actualizar préstamos vencidos
    UPDATE transactions
    SET loan_status = 'overdue'
    WHERE type = 'loan'
      AND loan_status = 'pending'
      AND status = 'active'
      AND expected_return_date < CURDATE();

    -- Cantidad afectada
    SET affected_count = ROW_COUNT();

    -- Auditoría
    IF affected_count > 0 THEN
        INSERT INTO audit (
            user_id, action, table_name, record_id, details, created_at
        ) VALUES (
            1,
            'scheduled_update',
            'transactions',
            NULL,
            CONCAT('Evento automático (respaldo): ', affected_count,
                   ' préstamo(s) actualizado(s) a estado VENCIDO a las 09:00 AM'),
            NOW()
        );
    END IF;
END$$

DELIMITER ;

-- =====================================================
-- EVENTO 3: Verificación a medianoche (00:05 AM)
-- =====================================================

DROP EVENT IF EXISTS update_overdue_loans_midnight;
DELIMITER $$

CREATE EVENT update_overdue_loans_midnight
ON SCHEDULE
    EVERY 1 DAY
    STARTS (CURDATE() + INTERVAL 1 DAY + INTERVAL '00:05:00' HOUR_SECOND)
COMMENT 'Actualiza préstamos vencidos cada día a las 00:05 AM'
DO
BEGIN
    DECLARE affected_count INT DEFAULT 0;

    -- Actualizar préstamos vencidos
    UPDATE transactions
    SET loan_status = 'overdue'
    WHERE type = 'loan'
      AND loan_status = 'pending'
      AND status = 'active'
      AND expected_return_date < CURDATE();

    -- Cantidad afectada
    SET affected_count = ROW_COUNT();

    -- Auditoría
    IF affected_count > 0 THEN
        INSERT INTO audit (
            user_id, action, table_name, record_id, details, created_at
        ) VALUES (
            1,
            'scheduled_update',
            'transactions',
            NULL,
            CONCAT('Evento automático (medianoche): ', affected_count,
                   ' préstamo(s) actualizado(s) a estado VENCIDO a las 00:05 AM'),
            NOW()
        );
    END IF;
END$$

DELIMITER ;

-- =====================================================
-- COMANDOS ÚTILES
-- =====================================================
-- Ver eventos
-- SHOW EVENTS;

-- Ver scheduler
-- SHOW VARIABLES LIKE 'event_scheduler';

-- Activar un evento
-- ALTER EVENT update_overdue_loans_morning ENABLE;

-- Desactivar un evento
-- ALTER EVENT update_overdue_loans_morning DISABLE;

-- Ver registros de auditoría
-- SELECT * FROM audit ORDER BY created_at DESC;
