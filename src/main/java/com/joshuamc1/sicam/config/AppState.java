package com.joshuamc1.sicam.config;

import com.joshuamc1.sicam.core.Logs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Gestiona un archivo de estado simple en la carpeta segura de la aplicación.
 * Guarda flags como: migrations_applied=true/false, admin_created=true/false
 */
public class AppState {
    private static final String STATE_FILE = "appstate.properties";
    private static final Properties props = new Properties();
    private static final Path statePath;

    static {
        Path dir = AppConfig.getSecureFolderPath();
        try {
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
        } catch (IOException e) {
            throw new RuntimeException("No se pudo crear carpeta de estado: " + dir, e);
        }
        statePath = dir.resolve(STATE_FILE);

        if (Files.exists(statePath)) {
            try (InputStream is = Files.newInputStream(statePath)) {
                props.load(is);
            } catch (IOException e) {
                Logs.error(AppState.class, "Error cargando estado de la app: {}", e.getMessage());
            }
        }
    }

    private static synchronized void save() {
        try (OutputStream os = Files.newOutputStream(statePath)) {
            props.store(os, "App state flags");
        } catch (IOException e) {
            Logs.error(AppState.class, "Error guardando estado de la app: {}", e.getMessage());
        }
    }

    public static boolean isMigrationsApplied() {
        return Boolean.parseBoolean(props.getProperty("migrations_applied", "false"));
    }

    public static void setMigrationsApplied(boolean v) {
        props.setProperty("migrations_applied", String.valueOf(v));
        save();
    }

    public static boolean isAdminCreated() {
        return Boolean.parseBoolean(props.getProperty("admin_created", "false"));
    }

    public static void setAdminCreated(boolean v) {
        props.setProperty("admin_created", String.valueOf(v));
        save();
    }

    public static boolean isDatabaseConfigured() {
        return Boolean.parseBoolean(props.getProperty("database_configured", "false"));
    }

    public static void setDatabaseConfigured(boolean v) {
        props.setProperty("database_configured", String.valueOf(v));
        save();
    }

    public static boolean isRootCreated() {
        return Boolean.parseBoolean(props.getProperty("root_created", "false"));
    }

    public static void setRootCreated(boolean v) {
        props.setProperty("root_created", String.valueOf(v));
        save();
    }
}
