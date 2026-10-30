package com.joshuamc1.sicam.config;

import org.javalite.activejdbc.Base;
import com.joshuamc1.sicam.core.Logs;

public class DatabaseConfig {
    private static final String DRIVER = "com.mysql.cj.jdbc.Driver";

    /** Conexión breve e independiente para renovar/liberar sesiones incluso al cerrar la UI. */
    public static java.sql.Connection openSessionConnection() throws java.sql.SQLException {
        java.util.Properties properties = new java.util.Properties();
        properties.setProperty("user", MySQLConfigManager.getUsername());
        properties.setProperty("password", MySQLConfigManager.getPassword());
        properties.setProperty("connectTimeout", "5000");
        properties.setProperty("socketTimeout", "5000");
        return java.sql.DriverManager.getConnection(MySQLConfigManager.getJdbcUrl(), properties);
    }

    public static void openConnection() {
        if (!Base.hasConnection()) {
            try {
                String url = MySQLConfigManager.getJdbcUrl();
                String user = MySQLConfigManager.getUsername();
                String password = MySQLConfigManager.getPassword();

                if (url == null || url.isEmpty()) {
                    throw new RuntimeException("No se encontró configuración de MySQL");
                }

                Base.open(DRIVER, url, user, password);
                Logs.info(DatabaseConfig.class, "Conexión abierta a MySQL");
            } catch (Exception e) {
                Logs.error(DatabaseConfig.class, "Error abriendo la conexión a MySQL: {}", e.getMessage());
                throw new RuntimeException("Error abriendo la conexión a la base de datos: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Intenta abrir la conexión y devuelve true si fue exitosa o false si falló.
     */
    public static boolean tryOpenConnection() {
        try {
            openConnection();
            return true;
        } catch (RuntimeException e) {
            // ya fue loggeado en openConnection
            return false;
        }
    }

    public static void closeConnection() {
        if (Base.hasConnection()) {
            Base.close();
            Logs.info(DatabaseConfig.class, "Conexión cerrada");
        }
    }
}
