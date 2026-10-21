package com.joshuamc1.sicam.services;

import com.joshuamc1.sicam.config.AppConfig;
import com.joshuamc1.sicam.config.DatabaseConfig;
import com.joshuamc1.sicam.config.MySQLConfigManager;
import com.joshuamc1.sicam.core.Logs;
import com.joshuamc1.sicam.core.Session;
import com.joshuamc1.sicam.models.Backup;
import com.joshuamc1.sicam.models.User;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Servicio para gestión de backups de la base de datos
 */
public class BackupService {

    private static final String BACKUP_DIR_NAME = "backups";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private final AuditService auditService = new AuditService();

    /**
     * Obtiene el directorio de backups
     */
    public Path getBackupDirectory() {
        String userHome = System.getProperty("user.home");
        Path baseDir = Paths.get(userHome, AppConfig.getSecureDirName());
        Path backupDir = baseDir.resolve(BACKUP_DIR_NAME);

        try {
            if (!Files.exists(backupDir)) {
                Files.createDirectories(backupDir);
                Logs.info(getClass(), "Directorio de backups creado: {}", backupDir);
            }
        } catch (IOException e) {
            Logs.error(getClass(), "Error creando directorio de backups", e);
        }

        return backupDir;
    }

    /**
     * Crea un backup de la base de datos (manual, con auditoría)
     * @return Path del archivo de backup creado
     * @throws Exception si hay un error
     */
    public Path createBackup() throws Exception {
        return createBackup(false);
    }

    /**
     * Crea un backup de la base de datos
     * @param isAutomatic true si es backup automático, false si es manual
     * @return Path del archivo de backup creado
     * @throws Exception si hay un error
     */
    public Path createBackup(boolean isAutomatic) throws Exception {
        if (!isAutomatic) com.joshuamc1.sicam.core.Authorization.require("BACKUP","CREATE");
        if (!MySQLConfigManager.hasConfig()) {
            throw new Exception("Configuración de MySQL no encontrada");
        }

        // Obtener datos de configuración
        String host = extractHost(MySQLConfigManager.getJdbcUrl());
        String port = extractPort(MySQLConfigManager.getJdbcUrl());
        String database = extractDatabase(MySQLConfigManager.getJdbcUrl());
        String username = MySQLConfigManager.getUsername();
        String password = MySQLConfigManager.getPassword();

        String timestamp = LocalDateTime.now().format(FORMATTER);
        String filename = String.format("backup_%s_%s.sql.gz", database, timestamp);
        Path backupFile = getBackupDirectory().resolve(filename);

        Logs.info(getClass(), "Iniciando backup {}: {}", isAutomatic ? "(automático)" : "(manual)", filename);

        // Construir comando mysqldump con compresión
        ProcessBuilder pb = new ProcessBuilder();

        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            // Windows - con compresión usando powershell
            String passwordArg = password.isEmpty() ? "" : String.format("-p%s", password);

            String command = password.isEmpty()
                ? String.format("mysqldump -h%s -P%s -u%s %s | gzip > \"%s\"",
                    host, port, username, database, backupFile.toAbsolutePath().toString())
                : String.format("mysqldump -h%s -P%s -u%s %s %s | gzip > \"%s\"",
                    host, port, username, passwordArg, database, backupFile.toAbsolutePath().toString());

            pb.command("cmd.exe", "/c", command);
        } else {
            // Linux/Mac - con compresión usando gzip
            String passwordArg = password.isEmpty() ? "" : String.format("-p%s", password);

            String command = password.isEmpty()
                ? String.format("mysqldump -h%s -P%s -u%s %s | gzip > '%s'",
                    host, port, username, database, backupFile.toAbsolutePath().toString())
                : String.format("mysqldump -h%s -P%s -u%s %s %s | gzip > '%s'",
                    host, port, username, passwordArg, database, backupFile.toAbsolutePath().toString());

            pb.command("sh", "-c", command);
        }

        pb.redirectErrorStream(true);
        Process process = pb.start();

        // Leer salida del proceso
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();

        if (exitCode != 0) {
            String error = output.toString();
            Logs.error(getClass(), "Error en mysqldump: {}", error);

            // Registrar backup fallido
            try {
                DatabaseConfig.openConnection();
                if (isAutomatic) {
                    registrarBackupAutomatico(filename, backupFile.toString(), 0L, "FAILED", error);
                } else {
                    registrarBackupManual(filename, backupFile.toString(), 0L, "FAILED", error);
                }
                DatabaseConfig.closeConnection();
            } catch (Exception e) {
                Logs.error(getClass(), "Error registrando backup fallido en BD", e);
            }

            throw new Exception("Error ejecutando mysqldump: " + error);
        }

        // Verificar que el archivo fue creado
        if (!Files.exists(backupFile)) {
            throw new Exception("El archivo de backup no fue creado");
        }

        long fileSize = Files.size(backupFile);

        // Validar tamaño mínimo
        if (fileSize < 100) {
            Logs.warn(getClass(), "Backup muy pequeño ({}), posible error", fileSize);
            throw new Exception("Backup demasiado pequeño, posible error en la compresión");
        }

        // Registrar backup exitoso
        DatabaseConfig.openConnection();
        try {
            if (isAutomatic) {
                registrarBackupAutomatico(filename, backupFile.toString(), fileSize, "SUCCESS", null);
            } else {
                registrarBackupManual(filename, backupFile.toString(), fileSize, "SUCCESS", null);
            }

            Logs.info(getClass(), "Backup {} completado exitosamente: {} ({} bytes)",
                isAutomatic ? "automático" : "manual", filename, fileSize);
        } finally {
            DatabaseConfig.closeConnection();
        }

        return backupFile;
    }

    /**
     * Extrae el host de la URL JDBC
     */
    private @NotNull String extractHost(@NotNull String jdbcUrl) {
        int start = jdbcUrl.indexOf("//") + 2;
        int end = jdbcUrl.indexOf(":", start);
        return jdbcUrl.substring(start, end);
    }

    /**
     * Extrae el puerto de la URL JDBC
     */
    private @NotNull String extractPort(@NotNull String jdbcUrl) {
        int start = jdbcUrl.indexOf(":", jdbcUrl.indexOf("//")) + 1;
        int end = jdbcUrl.indexOf("/", start);
        return jdbcUrl.substring(start, end);
    }

    /**
     * Extrae el nombre de la base de datos de la URL JDBC
     */
    private @NotNull String extractDatabase(@NotNull String jdbcUrl) {
        int start = jdbcUrl.lastIndexOf("/") + 1;
        int end = jdbcUrl.indexOf("?");
        if (end == -1) end = jdbcUrl.length();
        return jdbcUrl.substring(start, end);
    }

    /**
     * Registra un backup MANUAL en la base de datos (con usuario de sesión y auditoría)
     */
    private void registrarBackupManual(String filename, String filepath, long size, String status, String errorMessage) {
        try {
            Logs.info(getClass(), "Registrando backup manual en BD: filename={}, size={} bytes, status={}",
                filename, size, status);

            Backup backup = new Backup();
            backup.set("filename", filename);
            backup.set("filepath", filepath);
            backup.set("size_bytes", size); // No necesita boxing
            backup.set("usuario_id", Session.getCurrentUserId()); // Usuario de sesión
            backup.set("status", status);
            backup.set("error_message", "Copia de seguridad creada manualmente");

            if (errorMessage != null) {
                backup.set("error_message", errorMessage);
            }

            boolean saved = backup.saveIt();

            if (saved) {
                Logs.info(getClass(), "Backup manual registrado exitosamente con ID: {}", backup.getId());

                // Auditoría solo para backups manuales
                auditService.logAction(Session.getCurrentUserId(), "create_backup", "backups", backup.getInteger("id"),
                    String.format("{\"filename\":\"%s\",\"size\":%d}", filename, size));
            } else {
                Logs.error(getClass(), "No se pudo guardar el backup manual en BD");
            }
        } catch (Exception e) {
            Logs.error(getClass(), "Error registrando backup manual en BD", e);
        }
    }

    /**
     * Registra un backup AUTOMÁTICO en la base de datos (con usuario SYSTEM, sin auditoría)
     */
    private void registrarBackupAutomatico(String filename, String filepath, long size, String status, String errorMessage) {
        try {
            Logs.info(getClass(), "Registrando backup automático en BD: filename={}, size={} bytes, status={}",
                filename, size, status);

            // Buscar usuario SYSTEM
            User systemUser = User.findFirst("username = ?", "SYSTEM");

            if (systemUser == null) {
                Logs.error(getClass(), "Usuario SYSTEM no encontrado en la base de datos");
                throw new RuntimeException("Usuario SYSTEM no encontrado");
            }

            Integer systemUserId = systemUser.getInteger("id");
            Logs.info(getClass(), "Usuario SYSTEM encontrado con ID: {}", systemUserId);

            Backup backup = new Backup();
            backup.set("filename", filename);
            backup.set("filepath", filepath);
            backup.set("size_bytes", size); // No necesita boxing
            backup.set("usuario_id", systemUserId); // Usuario SYSTEM
            backup.set("status", status);
            backup.set("error_message", "Copia de seguridad creada por el sistema");

            if (errorMessage != null) {
                backup.set("error_message", errorMessage);
            }

            boolean saved = backup.saveIt();

            if (saved) {
                Logs.info(getClass(), "Backup automático registrado exitosamente con ID: {}", backup.getId());
                // NO se registra auditoría para backups automáticos
            } else {
                Logs.error(getClass(), "No se pudo guardar el backup automático en BD");
            }
        } catch (Exception e) {
            Logs.error(getClass(), "Error registrando backup automático en BD", e);
            throw new RuntimeException("Error registrando backup automático: " + e.getMessage());
        }
    }

    /**
     * Lista todos los backups
     */
    public List<Backup> listBackups() {
        com.joshuamc1.sicam.core.Authorization.require("BACKUP","READ");
        return Backup.findAll().orderBy("id DESC");
    }

    /**
     * Lista backups paginados
     */
    public List<Backup> listBackupsPaged(int offset, int limit) {
        return listBackupsPaged(offset, limit, com.joshuamc1.sicam.core.PageSort.DEFAULT);
    }

    public List<Backup> listBackupsPaged(int offset, int limit, com.joshuamc1.sicam.core.PageSort sort) {
        com.joshuamc1.sicam.core.Authorization.require("BACKUP", "READ");
        String order = sort.toSql(java.util.Map.of(
            "id", "b.id", "filename", "b.filename", "size", "b.size_bytes",
            "user", "COALESCE(u.username, 'Desconocido')", "message", "b.error_message",
            "date", "b.created_at", "status", "b.status"), "b.id DESC");
        return com.joshuamc1.sicam.core.DatabaseManager.callInConnection(() ->
            new java.util.ArrayList<>(Backup.<Backup>findBySQL(
                "SELECT b.* FROM backups b LEFT JOIN users u ON u.id=b.usuario_id ORDER BY " + order + " LIMIT ? OFFSET ?",
                limit, offset)));
    }

    /**
     * Cuenta total de backups
     */
    public long countBackups() {
        com.joshuamc1.sicam.core.Authorization.require("BACKUP","READ");
        return Backup.count();
    }

    /**
     * Elimina un backup (archivo y registro)
     */
    public void deleteBackup(Integer backupId) throws Exception {
        com.joshuamc1.sicam.core.Authorization.require("BACKUP","DELETE");
        Backup backup = Backup.findById(backupId);
        if (backup == null) {
            throw new Exception("Backup no encontrado");
        }

        String filepath = backup.getString("filepath");

        // Eliminar archivo físico
        try {
            Path file = Paths.get(filepath);
            if (Files.exists(file)) {
                Files.delete(file);
                Logs.info(getClass(), "Archivo de backup eliminado: {}", filepath);
            }
        } catch (IOException e) {
            Logs.error(getClass(), "Error eliminando archivo de backup", e);
            throw new Exception("Error eliminando archivo: " + e.getMessage());
        }

        // Auditoría
        auditService.logAction(Session.getCurrentUserId(), "delete_backup", "backups", backupId,
            String.format("{\"filename\":\"%s\"}", backup.getString("filename")));

        // Eliminar registro
        backup.delete();
    }

    /**
     * Elimina backups antiguos (mantener solo los últimos N)
     */
    public int cleanOldBackups(int keepLast) throws Exception {
        com.joshuamc1.sicam.core.Authorization.require("BACKUP","DELETE");
        List<Backup> allBackups = Backup.findAll().orderBy("created_at DESC");

        int deleted = 0;
        for (int i = keepLast; i < allBackups.size(); i++) {
            Backup backup = allBackups.get(i);
            try {
                deleteBackup(backup.getInteger("id"));
                deleted++;
            } catch (Exception e) {
                Logs.error(getClass(), "Error eliminando backup antiguo: {}", backup.getString("filename"), e);
            }
        }

        return deleted;
    }
}

