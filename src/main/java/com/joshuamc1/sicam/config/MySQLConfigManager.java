package com.joshuamc1.sicam.config;

import com.joshuamc1.sicam.core.Logs;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.Properties;

/**
 * Gestiona la configuración de MySQL guardada de forma segura (con cifrado básico).
 */
public class MySQLConfigManager {
    private static final String CONFIG_FILE = "mysql.config";
    private static final String SALT = "sicam2026"; // Salt fijo para el cifrado
    private static final String SECRET = "7jq4b6PXmxk7Fmi1CYHhJt34NjZ9zCMh"; // Clave para cifrado

    private static final Path configPath;
    private static final Properties props = new Properties();

    static {
        Path dir = AppConfig.getSecureFolderPath();
        try {
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
        } catch (IOException e) {
            throw new RuntimeException("No se pudo crear carpeta de configuración: " + dir, e);
        }
        configPath = dir.resolve(CONFIG_FILE);
        loadConfig();
    }

    /**
     * Guarda la configuración de MySQL de forma cifrada.
     */
    public static synchronized void saveConfig(String host, String port, String database, String username, String password) {
        try {
            props.setProperty("host", encrypt(host));
            props.setProperty("port", encrypt(port));
            props.setProperty("database", encrypt(database));
            props.setProperty("username", encrypt(username));
            props.setProperty("password", encrypt(password));

            try (OutputStream os = Files.newOutputStream(configPath)) {
                props.store(os, "MySQL Configuration - Encrypted");
            }
            Logs.info(MySQLConfigManager.class, "Configuración MySQL guardada exitosamente");
        } catch (Exception e) {
            Logs.error(MySQLConfigManager.class, "Error guardando configuración MySQL: {}", e.getMessage());
            throw new RuntimeException("Error guardando configuración MySQL", e);
        }
    }

    /**
     * Carga la configuración de MySQL.
     */
    private static synchronized void loadConfig() {
        if (Files.exists(configPath)) {
            try (InputStream is = Files.newInputStream(configPath)) {
                props.load(is);
                Logs.info(MySQLConfigManager.class, "Configuración MySQL cargada exitosamente");
            } catch (IOException e) {
                Logs.error(MySQLConfigManager.class, "Error cargando configuración MySQL: {}", e.getMessage());
            }
        }
    }

    /**
     * Obtiene la URL JDBC de MySQL.
     */
    public static String getJdbcUrl() {
        try {
            String host = decrypt(props.getProperty("host", ""));
            String port = decrypt(props.getProperty("port", "3306"));
            String database = decrypt(props.getProperty("database", ""));

            return String.format("jdbc:mysql://%s:%s/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                    host, port, database);
        } catch (Exception e) {
            Logs.error(MySQLConfigManager.class, "Error obteniendo URL JDBC: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Obtiene el usuario de MySQL.
     */
    public static String getUsername() {
        try {
            return decrypt(props.getProperty("username", ""));
        } catch (Exception e) {
            Logs.error(MySQLConfigManager.class, "Error obteniendo username: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Obtiene la contraseña de MySQL.
     */
    public static String getPassword() {
        try {
            return decrypt(props.getProperty("password", ""));
        } catch (Exception e) {
            Logs.error(MySQLConfigManager.class, "Error obteniendo password: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Verifica si existe configuración guardada.
     */
    public static boolean hasConfig() {
        return Files.exists(configPath) &&
               props.containsKey("host") &&
               props.containsKey("database") &&
               props.containsKey("username");
    }

    /**
     * Cifra un texto usando AES.
     */
    private static String encrypt(String value) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(SECRET.toCharArray(), SALT.getBytes(), 65536, 256);
        SecretKey tmp = factory.generateSecret(spec);
        SecretKey secret = new SecretKeySpec(tmp.getEncoded(), "AES");

        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, secret);
        byte[] encrypted = cipher.doFinal(value.getBytes());
        return Base64.getEncoder().encodeToString(encrypted);
    }

    /**
     * Descifra un texto usando AES.
     */
    private static String decrypt(String encrypted) throws Exception {
        if (encrypted == null || encrypted.isEmpty()) {
            return "";
        }

        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(SECRET.toCharArray(), SALT.getBytes(), 65536, 256);
        SecretKey tmp = factory.generateSecret(spec);
        SecretKey secret = new SecretKeySpec(tmp.getEncoded(), "AES");

        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, secret);
        byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encrypted));
        return new String(decrypted);
    }
}
