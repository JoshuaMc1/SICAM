package com.joshuamc1.sicam.config;

import com.joshuamc1.sicam.core.Logs;
import org.javalite.activejdbc.Base;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Ejecuta las migraciones de base de datos desde los archivos .sql en resources/migrations.
 */
public class MigrationRunner {

    /**
     * Ejecuta todas las migraciones pendientes en orden alfabético.
     * Asume que la conexión a la base de datos ya está abierta.
     */
    public static void runMigrations() {
        try {
            Logs.info(MigrationRunner.class, "Iniciando migraciones de base de datos...");

            createMigrationTable();

            // Ejecutar de forma idempotente basados en schema_migrations de la BD
            List<String> migrationFiles = getMigrationFiles();
            int applied = 0;

            for (String migrationFile : migrationFiles) {
                if (!isMigrationApplied(migrationFile)) {
                    executeMigration(migrationFile);
                    recordMigration(migrationFile);
                    applied++;
                } else {
                    Logs.debug(MigrationRunner.class, "Migración ya aplicada (omitiendo): {}", migrationFile);
                }
            }

            // Marcar en AppState para referencia, pero NO condiciona la ejecución
            AppState.setMigrationsApplied(true);

            if (applied == 0) {
                Logs.info(MigrationRunner.class, "Migraciones completadas (sin cambios, idempotente)");
            } else {
                Logs.info(MigrationRunner.class, "Migraciones aplicadas: {}", applied);
            }

            // Crear eventos MySQL para préstamos vencidos
            createOverdueLoansEvents();

        } catch (Exception e) {
            Logs.error(MigrationRunner.class, "Error ejecutando migraciones: {}", e.getMessage());
            throw new RuntimeException("Error ejecutando migraciones de base de datos", e);
        }
    }

    /**
     * Crea la tabla de control de migraciones si no existe (MySQL).
     */
    private static void createMigrationTable() {
        try {
            Base.exec("CREATE TABLE IF NOT EXISTS schema_migrations (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "migration_name VARCHAR(255) NOT NULL UNIQUE, " +
                    "applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;");
            Logs.debug(MigrationRunner.class, "Tabla de control de migraciones verificada");
        } catch (Exception e) {
            Logs.warn(MigrationRunner.class, "Error creando tabla de migraciones: {}", e.getMessage());
        }
    }

    /**
     * Verifica si una migración ya fue aplicada.
     */
    private static boolean isMigrationApplied(String migrationName) {
        try {
            Long count = Base.count("schema_migrations", "migration_name = ?", migrationName);
            return count != null && count > 0;
        } catch (Exception e) {
            Logs.warn(MigrationRunner.class, "Error verificando migración {}: {}", migrationName, e.getMessage());
            return false;
        }
    }

    /**
     * Registra una migración como aplicada.
     */
    private static void recordMigration(String migrationName) {
        try {
            Base.exec("INSERT INTO schema_migrations (migration_name) VALUES (?)", migrationName);
            Logs.debug(MigrationRunner.class, "Migración registrada: {}", migrationName);
        } catch (Exception e) {
            Logs.warn(MigrationRunner.class, "Error registrando migración {}: {}", migrationName, e.getMessage());
        }
    }

    /**
     * Obtiene la lista de archivos de migración en orden alfabético. El seeder se excluye para evitar crear usuarios por defecto.
     */
    private static @NotNull List<String> getMigrationFiles() {
        List<String> files = new ArrayList<>();
        files.add("20251114112627_create_roles_table.sql");
        files.add("20251114112628_create_user_table.sql");
        files.add("20251114112848_create_people_table.sql");
        files.add("20251114112911_create_product_table.sql");
        files.add("20251124120000_migrate_to_multiproduct_transactions.sql");
        files.add("20251114114102_create_audit_table.sql");
        files.add("20251114114104_create_indexes_table.sql");
        files.add("20251114114105_create_permissions_table.sql");
        files.add("20251114114106_create_role_permissions_table.sql");
        files.add("20251114114107_seed_roles_and_permissions.sql");
        files.add("20251120000001_create_backups_table.sql");
        files.add("20251120000002_add_backup_permissions.sql");
        Collections.sort(files);
        return files;
    }

    /**
     * Ejecuta un archivo de migración específico.
     */
    private static void executeMigration(String filename) {
        try {
            String path = "/migrations/" + filename;
            InputStream is = MigrationRunner.class.getResourceAsStream(path);

            if (is == null) {
                Logs.warn(MigrationRunner.class, "Archivo de migración no encontrado: {}", path);
                return;
            }

            String sql = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));

            if (sql.trim().isEmpty()) {
                Logs.warn(MigrationRunner.class, "Archivo de migración vacío: {}", filename);
                return;
            }

            String[] statements = sql.split(";\n|;\r\n|;\r|;\t|; ");
            for (String rawStatement : statements) {
                String statement = rawStatement.trim();
                if (statement.isEmpty()) continue;

                // Compatibilidad MySQL: quitar IF NOT EXISTS en CREATE [UNIQUE] INDEX
                statement = statement.replaceFirst("(?is)^\\s*create\\s+(unique\\s+)?index\\s+if\\s+not\\s+exists\\s+", "CREATE $1INDEX ");

                try {
                    Base.exec(statement);
                    Logs.debug(MigrationRunner.class, "Statement ejecutado ({})", truncate(statement));
                } catch (Exception e) {
                    String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                    if (msg.contains("already exists") || msg.contains("duplicate") || msg.contains("unique constraint") || msg.contains("constraint failed")) {
                        Logs.debug(MigrationRunner.class, "Statement ignorado por existir previamente: {}", truncate(statement));
                    } else {
                        throw e;
                    }
                }
            }

            Logs.info(MigrationRunner.class, "Migración ejecutada: {}", filename);

        } catch (Exception e) {
            Logs.error(MigrationRunner.class, "Error ejecutando migración {}: {}", filename, e.getMessage());
            throw new RuntimeException("Error ejecutando migración: " + filename, e);
        }
    }

    private static String truncate(@NotNull String s) {
        return s.length() > 60 ? s.substring(0,57) + "..." : s;
    }

    /**
     * Crea eventos MySQL para actualizar préstamos vencidos automáticamente.
     * Se ejecuta después de las migraciones y usa JDBC directo para evitar problemas con DELIMITER.
     */
    private static void createOverdueLoansEvents() {
        try {
            Logs.info(MigrationRunner.class, "Configurando eventos de préstamos vencidos...");

            // Activar event scheduler
            enableEventScheduler();

            // Crear los 3 eventos
            createOverdueEventMorning();
            createOverdueEventBackup();
            createOverdueEventMidnight();

            Logs.info(MigrationRunner.class, "Eventos de préstamos vencidos configurados correctamente");
        } catch (Exception e) {
            Logs.warn(MigrationRunner.class, "No se pudieron crear eventos automáticos: {}", e.getMessage());
            Logs.warn(MigrationRunner.class, "Los eventos pueden crearse manualmente con MANUAL_create_overdue_events.sql");
        }
    }

    /**
     * Activa el event scheduler de MySQL
     */
    private static void enableEventScheduler() {
        try {
            Base.exec("SET GLOBAL event_scheduler = ON");
            Logs.debug(MigrationRunner.class, "Event scheduler activado");
        } catch (Exception e) {
            Logs.debug(MigrationRunner.class, "Event scheduler ya estaba activo o no se pudo activar: {}", e.getMessage());
        }
    }

    /**
     * Crea evento para actualizar préstamos vencidos a las 8:35 AM
     */
    private static void createOverdueEventMorning() {
        String eventName = "update_overdue_loans_morning";
        try {
            // Eliminar si existe
            dropEventIfExists(eventName);

            // Crear el evento (sin DELIMITER, todo en una línea)
            String sql = "CREATE EVENT " + eventName + " " +
                    "ON SCHEDULE EVERY 1 DAY " +
                    "STARTS (CURDATE() + INTERVAL '08:35:00' HOUR_SECOND) " +
                    "COMMENT 'Actualiza préstamos vencidos cada día a las 8:35 AM' " +
                    "DO " +
                    "BEGIN " +
                    "  DECLARE affected_count INT DEFAULT 0; " +
                    "  DECLARE system_user_id INT DEFAULT 1; " +
                    "  SELECT id INTO system_user_id FROM users WHERE username = 'SYSTEM' LIMIT 1; " +
                    "  UPDATE transactions SET loan_status = 'overdue' " +
                    "  WHERE type = 'loan' AND loan_status = 'pending' AND status = 'active' " +
                    "  AND expected_return_date < CURDATE(); " +
                    "  SET affected_count = ROW_COUNT(); " +
                    "  IF affected_count > 0 THEN " +
                    "    INSERT INTO audit (user_id, action, table_name, record_id, details, created_at) " +
                    "    VALUES (system_user_id, 'scheduled_update', 'transactions', NULL, " +
                    "            CONCAT('Evento automático: ', affected_count, ' préstamo(s) actualizado(s) a estado VENCIDO a las 08:35 AM'), NOW()); " +
                    "  END IF; " +
                    "END";

            Base.exec(sql);
            Logs.debug(MigrationRunner.class, "Evento creado: {}", eventName);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (msg.contains("already exists")) {
                Logs.debug(MigrationRunner.class, "Evento {} ya existe", eventName);
            } else {
                throw e;
            }
        }
    }

    /**
     * Crea evento para actualizar préstamos vencidos a las 9:00 AM (respaldo)
     */
    private static void createOverdueEventBackup() {
        String eventName = "update_overdue_loans_backup";
        try {
            dropEventIfExists(eventName);

            String sql = "CREATE EVENT " + eventName + " " +
                    "ON SCHEDULE EVERY 1 DAY " +
                    "STARTS (CURDATE() + INTERVAL '09:00:00' HOUR_SECOND) " +
                    "COMMENT 'Actualiza préstamos vencidos cada día a las 9:00 AM como respaldo' " +
                    "DO " +
                    "BEGIN " +
                    "  DECLARE affected_count INT DEFAULT 0; " +
                    "  DECLARE system_user_id INT DEFAULT 1; " +
                    "  SELECT id INTO system_user_id FROM users WHERE username = 'SYSTEM' LIMIT 1; " +
                    "  UPDATE transactions SET loan_status = 'overdue' " +
                    "  WHERE type = 'loan' AND loan_status = 'pending' AND status = 'active' " +
                    "  AND expected_return_date < CURDATE(); " +
                    "  SET affected_count = ROW_COUNT(); " +
                    "  IF affected_count > 0 THEN " +
                    "    INSERT INTO audit (user_id, action, table_name, record_id, details, created_at) " +
                    "    VALUES (system_user_id, 'scheduled_update', 'transactions', NULL, " +
                    "            CONCAT('Evento automático (respaldo): ', affected_count, ' préstamo(s) actualizado(s) a estado VENCIDO a las 09:00 AM'), NOW()); " +
                    "  END IF; " +
                    "END";

            Base.exec(sql);
            Logs.debug(MigrationRunner.class, "Evento creado: {}", eventName);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (msg.contains("already exists")) {
                Logs.debug(MigrationRunner.class, "Evento {} ya existe", eventName);
            } else {
                throw e;
            }
        }
    }

    /**
     * Crea evento para actualizar préstamos vencidos a las 00:05 AM (medianoche)
     */
    private static void createOverdueEventMidnight() {
        String eventName = "update_overdue_loans_midnight";
        try {
            dropEventIfExists(eventName);

            String sql = "CREATE EVENT " + eventName + " " +
                    "ON SCHEDULE EVERY 1 DAY " +
                    "STARTS (CURDATE() + INTERVAL 1 DAY + INTERVAL '00:05:00' HOUR_SECOND) " +
                    "COMMENT 'Actualiza préstamos vencidos cada día a las 00:05 AM' " +
                    "DO " +
                    "BEGIN " +
                    "  DECLARE affected_count INT DEFAULT 0; " +
                    "  DECLARE system_user_id INT DEFAULT 1; " +
                    "  SELECT id INTO system_user_id FROM users WHERE username = 'SYSTEM' LIMIT 1; " +
                    "  UPDATE transactions SET loan_status = 'overdue' " +
                    "  WHERE type = 'loan' AND loan_status = 'pending' AND status = 'active' " +
                    "  AND expected_return_date < CURDATE(); " +
                    "  SET affected_count = ROW_COUNT(); " +
                    "  IF affected_count > 0 THEN " +
                    "    INSERT INTO audit (user_id, action, table_name, record_id, details, created_at) " +
                    "    VALUES (system_user_id, 'scheduled_update', 'transactions', NULL, " +
                    "            CONCAT('Evento automático (medianoche): ', affected_count, ' préstamo(s) actualizado(s) a estado VENCIDO a las 00:05 AM'), NOW()); " +
                    "  END IF; " +
                    "END";

            Base.exec(sql);
            Logs.debug(MigrationRunner.class, "Evento creado: {}", eventName);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (msg.contains("already exists")) {
                Logs.debug(MigrationRunner.class, "Evento {} ya existe", eventName);
            } else {
                throw e;
            }
        }
    }

    /**
     * Elimina un evento si existe
     */
    private static void dropEventIfExists(String eventName) {
        try {
            Base.exec("DROP EVENT IF EXISTS " + eventName);
        } catch (Exception e) {
            // Ignorar errores al eliminar
            Logs.debug(MigrationRunner.class, "No se pudo eliminar evento {}: {}", eventName, e.getMessage());
        }
    }
}
