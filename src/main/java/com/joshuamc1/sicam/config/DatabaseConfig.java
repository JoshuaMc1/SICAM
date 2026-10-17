package com.joshuamc1.sicam.config;

import org.javalite.activejdbc.Base;
import com.joshuamc1.sicam.core.Logs;

public class DatabaseConfig {
    private static final String DRIVER = "com.mysql.cj.jdbc.Driver";

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
