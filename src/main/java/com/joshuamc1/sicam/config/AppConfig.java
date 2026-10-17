package com.joshuamc1.sicam.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Clase centralizada para parámetros configurables de la aplicación.
 * Incluye detección de entorno y rutas por defecto.
 */
public final class AppConfig {
    private AppConfig() {}

    // Entorno: "production" ó "development". Por defecto development.
    private static final String ENV = System.getProperty("app.env", System.getenv().getOrDefault("APP_ENV", "development"));

    // Nombre de la base de datos (para SQLite en modo production)
    private static final String DEFAULT_DB_NAME = System.getProperty("app.db.name", System.getenv().getOrDefault("APP_DB_NAME", "database.db"));

    // Carpeta segura en Windows (perfil del usuario)
    private static final String DEFAULT_SECURE_DIR = System.getProperty("app.secure.dir", System.getenv().getOrDefault("APP_SECURE_DIR",
            Paths.get(System.getProperty("user.home"), ".sicam").toString()));

    public static boolean isProduction() {
        return ENV != null && ENV.toLowerCase(Locale.ROOT).startsWith("prod");
    }

    public static boolean isDevelopment() {
        return !isProduction();
    }

    public static String getEnv() {
        return ENV;
    }

    public static String getDatabaseFileName() {
        return DEFAULT_DB_NAME;
    }

    public static Path getSecureFolderPath() {
        return Paths.get(DEFAULT_SECURE_DIR);
    }

    /**
     * Nombre del directorio seguro (ej: ".sicam") — método compatible con código existente.
     */
    public static String getSecureDirName() {
        Path p = getSecureFolderPath();
        Path fileName = p.getFileName();
        return fileName != null ? fileName.toString() : ".sicam";
    }

    /**
     * Ruta completa hacia la base de datos SQLite dentro de la carpeta segura.
     */
    public static Path getSecureDatabasePath() {
        Path dir = getSecureFolderPath();
        // asegurar existencia del directorio (no crear aquí, solo devolver path)
        return dir.resolve(getDatabaseFileName()).toAbsolutePath();
    }
}
