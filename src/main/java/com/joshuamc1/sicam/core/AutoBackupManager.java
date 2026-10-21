package com.joshuamc1.sicam.core;

import com.joshuamc1.sicam.services.BackupService;
import com.joshuamc1.sicam.config.AppConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

/**
 * Gestor de backups automáticos
 * - Backup al iniciar la app por primera vez en el día
 * - Backup cada vez que se cierre la aplicación
 */
public class AutoBackupManager {
    private static AutoBackupManager instance;
    private final Path cacheFile;
    private final Path legacyCacheFile;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final BackupService backupService;
    private Properties cacheProperties;
    private boolean autoBackupEnabled = false;

    private AutoBackupManager() {
        this(AppConfig.getSecureFolderPath(), Paths.get(System.getProperty("user.home"),
            ".inventario_municipal", ".cache", "backup_control.properties"));
    }

    AutoBackupManager(Path secureFolder, Path legacyCacheFile) {
        this.cacheFile = secureFolder.resolve(".cache").resolve("backup_control.properties");
        this.legacyCacheFile = legacyCacheFile;
        this.backupService = new BackupService();
        loadCache();
    }

    public static AutoBackupManager getInstance() {
        if (instance == null) {
            instance = new AutoBackupManager();
        }
        return instance;
    }

    /**
     * Verifica y crea backup automático al iniciar la aplicación
     * Solo se ejecuta una vez por día
     */
    public void checkStartupBackup() {
        if (!autoBackupEnabled) {
            Logs.info(getClass(), "Backups automáticos deshabilitados");
            return;
        }

        try {
            String today = LocalDate.now().format(DATE_FORMAT);
            String lastStartupBackup = cacheProperties.getProperty("last_startup_backup_date", "");

            if (!today.equals(lastStartupBackup)) {
                Logs.info(getClass(), "Creando backup automático de inicio del día...");

                // Crear backup en segundo plano
                new Thread(() -> {
                    try {
                        Path backupPath = backupService.createBackup(true); // true = automático
                        if (backupPath != null) {
                            // Actualizar cache
                            cacheProperties.setProperty("last_startup_backup_date", today);
                            cacheProperties.setProperty("last_startup_backup_time", LocalDateTime.now().format(DATETIME_FORMAT));
                            saveCache();

                            Logs.info(getClass(), "Backup automático de inicio creado: {}", backupPath.getFileName());
                        }
                    } catch (Exception e) {
                        Logs.error(getClass(), "Error creando backup automático de inicio: {}", e.getMessage());
                    }
                }, "auto-backup-startup").start();
            } else {
                Logs.info(getClass(), "Ya existe backup de inicio para hoy: {}", lastStartupBackup);
            }
        } catch (Exception e) {
            Logs.error(getClass(), "Error verificando backup de inicio: {}", e.getMessage());
        }
    }

    /**
     * Crea backup automático al cerrar la aplicación
     * Se ejecuta siempre que se cierre el programa
     */
    public void performShutdownBackup() {
        if (!autoBackupEnabled) {
            Logs.info(getClass(), "Backups automáticos deshabilitados");
            return;
        }

        try {
            Logs.info(getClass(), "Creando backup automático de cierre...");

            Path backupPath = backupService.createBackup(true); // true = automático
            if (backupPath != null) {
                // Actualizar cache con el último backup de cierre
                String now = LocalDateTime.now().format(DATETIME_FORMAT);
                cacheProperties.setProperty("last_shutdown_backup_time", now);
                cacheProperties.setProperty("last_shutdown_backup_date", LocalDate.now().format(DATE_FORMAT));
                saveCache();

                Logs.info(getClass(), "Backup automático de cierre creado: {}", backupPath.getFileName());
            }
        } catch (Exception e) {
            Logs.error(getClass(), "Error creando backup automático de cierre: {}", e.getMessage());
        }
    }

    /**
     * Habilita o deshabilita los backups automáticos
     */
    public void setAutoBackupEnabled(boolean enabled) {
        this.autoBackupEnabled = enabled;
        cacheProperties.setProperty("auto_backup_enabled", String.valueOf(enabled));
        saveCache();
        Logs.info(getClass(), "Backups automáticos {}", enabled ? "habilitados" : "deshabilitados");
    }

    /**
     * Verifica si los backups automáticos están habilitados
     */
    public boolean isAutoBackupEnabled() {
        return autoBackupEnabled;
    }

    /**
     * Obtiene información del último backup de inicio
     */
    public String getLastStartupBackupInfo() {
        String date = cacheProperties.getProperty("last_startup_backup_date", "Nunca");
        String time = cacheProperties.getProperty("last_startup_backup_time", "");

        if ("Nunca".equals(date)) {
            return "Nunca";
        }

        return time.isEmpty() ? date : time;
    }

    /**
     * Obtiene información del último backup de cierre
     */
    public String getLastShutdownBackupInfo() {
        String date = cacheProperties.getProperty("last_shutdown_backup_date", "Nunca");
        String time = cacheProperties.getProperty("last_shutdown_backup_time", "");

        if ("Nunca".equals(date)) {
            return "Nunca";
        }

        return time.isEmpty() ? date : time;
    }

    /**
     * Carga el cache desde el archivo
     */
    private void loadCache() {
        cacheProperties = new Properties();

        try {
            Files.createDirectories(cacheFile.getParent());
            // Migrar una sola vez, sin reemplazar la configuración nueva ni borrar la anterior.
            if (!Files.exists(cacheFile) && Files.exists(legacyCacheFile)) {
                Files.copy(legacyCacheFile, cacheFile);
                Logs.info(getClass(), "Control de backups migrado a la carpeta de SICAM");
            }
            if (Files.exists(cacheFile)) {
                try (var input = Files.newInputStream(cacheFile)) {
                    cacheProperties.load(input);
                }
                Logs.info(getClass(), "Cache de backups cargado");
            } else {
                // Inicializar con valores por defecto
                cacheProperties.setProperty("auto_backup_enabled", "false");
                cacheProperties.setProperty("last_startup_backup_date", "");
                cacheProperties.setProperty("last_startup_backup_time", "");
                cacheProperties.setProperty("last_shutdown_backup_date", "");
                cacheProperties.setProperty("last_shutdown_backup_time", "");
                saveCache();
                Logs.info(getClass(), "Cache de backups inicializado");
            }

            // Cargar configuración
            autoBackupEnabled = Boolean.parseBoolean(
                cacheProperties.getProperty("auto_backup_enabled", "false")
            );

        } catch (IOException e) {
            Logs.error(getClass(), "Error cargando cache de backups: {}", e.getMessage());
            // Valores por defecto
            cacheProperties.setProperty("auto_backup_enabled", "false");
            autoBackupEnabled = false;
        }
    }

    /**
     * Guarda el cache en el archivo
     */
    private void saveCache() {
        try {
            Files.createDirectories(cacheFile.getParent());
            try (var output = Files.newOutputStream(cacheFile)) {
                cacheProperties.store(output, "Control de Backups Automáticos - SICAM");
            }
        } catch (IOException e) {
            Logs.error(getClass(), "Error guardando cache de backups: {}", e.getMessage());
        }
    }

    /**
     * Resetea el cache (útil para testing)
     */
    public void resetCache() {
        cacheProperties.clear();
        cacheProperties.setProperty("auto_backup_enabled", "false");
        cacheProperties.setProperty("last_startup_backup_date", "");
        cacheProperties.setProperty("last_startup_backup_time", "");
        cacheProperties.setProperty("last_shutdown_backup_date", "");
        cacheProperties.setProperty("last_shutdown_backup_time", "");
        saveCache();
        autoBackupEnabled = false;
        Logs.info(getClass(), "Cache de backups reseteado");
    }
}

