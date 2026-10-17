package com.joshuamc1.sicam.core;

import javafx.scene.control.Alert;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;
import org.mindrot.jbcrypt.BCrypt;

import java.awt.*;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

public class Utils {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String DEFAULT_PASSWORD_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%&*()-_=+";

    /**
     * Concatenar cadenas con espacios (ignora null y cadenas vacías)
     * */
    public static @NotNull String concatWithSpaces(@NotNull String @NotNull ... strings) {
        if (strings.length == 0) return "";
        return Arrays.stream(strings)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(" "));
    }

    /**
     * Concatenar cadenas con un símbolo específico (ignora null y cadenas vacías)
     * */
    public static @NotNull String concatWithSymbol(String symbol, @NotNull String... strings) {
        if (symbol == null) symbol = "";
        if (strings.length == 0) return "";
        return Arrays.stream(strings)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(symbol));
    }

    /**
     * Verificar si una cadena es null o está vacía (después de trim)
     * */
    public static boolean isNullOrEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * Obtener fecha actual en formato ISO (YYYY-MM-DD)
     * */
    public static @NotNull String getCurrentDate() {
        return LocalDate.now().toString();
    }

    /**
     * Obtener fecha con formato personalizado
     * */
    public static @NotNull String getCurrentDate(@NotNull String pattern) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern(pattern);
        return LocalDate.now().format(fmt);
    }

    /**
     * Obtener hora actual en formato ISO (HH:MM:SS)
     * */
    public static @NotNull String getCurrentTime() {
        return LocalTime.now().withNano(0).toString();
    }

    /**
     * Obtener hora con formato personalizado
     * */
    public static @NotNull String getCurrentTime(@NotNull String pattern) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern(pattern);
        return LocalTime.now().withNano(0).format(fmt);
    }

    /**
     * Obtener fecha y hora actual en formato ISO (YYYY-MM-DDTHH:MM:SS)
     * */
    public static @NotNull String getCurrentDateTime() {
        return LocalDateTime.now().withNano(0).toString();
    }

    /**
     * Obtener fecha y hora con formato personalizado
     * */
    public static @NotNull String bcrypt(@NotNull String password) {
        Objects.requireNonNull(password, "password no puede ser null");
        return BCrypt.hashpw(password, BCrypt.gensalt());
    }

    /**
     * Obtener hash BCrypt con número de rondas personalizado
     * */
    public static @NotNull String bcrypt(@NotNull String password, int logRounds) {
        Objects.requireNonNull(password, "password no puede ser null");
        if (logRounds < 4 || logRounds > 31) logRounds = 5; // Valor por defecto si está fuera de rango
        return BCrypt.hashpw(password, BCrypt.gensalt(logRounds));
    }

    /**
     * Verificar si una contraseña coincide con un hash BCrypt
     * */
    public static boolean verifyBcrypt(String plainPassword, String hashed) {
        if (plainPassword == null || hashed == null) return false;
        try {
            return BCrypt.checkpw(plainPassword, hashed);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Generar una contraseña segura usando el conjunto de caracteres predeterminado
     * (mayúsculas, minúsculas, dígitos y símbolos especiales)
     * */
    public static @NotNull String generateRandomPassword(int length) {
        if (length <= 0) throw new IllegalArgumentException("length debe ser mayor que 0");
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int idx = RANDOM.nextInt(DEFAULT_PASSWORD_CHARS.length());
            sb.append(DEFAULT_PASSWORD_CHARS.charAt(idx));
        }
        return sb.toString();
    }

    /**
     * Generar una contraseña segura usando un conjunto de caracteres personalizado
     * */
    public static @NotNull String generateRandomPassword(int length, @NotNull String charset) {
        if (length <= 0) throw new IllegalArgumentException("length debe ser mayor que 0");
        if (isNullOrEmpty(charset)) throw new IllegalArgumentException("charset no puede ser null o vacío");
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int idx = RANDOM.nextInt(charset.length());
            sb.append(charset.charAt(idx));
        }
        return sb.toString();
    }

    /**
     * Mostrar una alerta de información con título y mensaje personalizados
     * */
    public static void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        // El dialogo aún no tiene Scene/Window hasta que se muestra; añadir icono solo si ya existe
        try {
            if (alert.getDialogPane().getScene() != null && alert.getDialogPane().getScene().getWindow() instanceof Stage) {
                Stage alertStage = (Stage) alert.getDialogPane().getScene().getWindow();
                alertStage.getIcons().add(new javafx.scene.image.Image(Objects.requireNonNull(Utils.class.getResourceAsStream("/images/logo.png"))));
            }
        } catch (Exception ignored) {
            // Si algo falla, seguimos sin icono para evitar bloquear la UI
        }
        alert.showAndWait();
    }

    /**
     * Mostrar un diálogo de confirmación con botones Sí/No
     * @return true si el usuario confirma, false si cancela
     */
    public static boolean showConfirmation(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);

        // Configurar botones
        alert.getButtonTypes().clear();
        alert.getButtonTypes().addAll(
            javafx.scene.control.ButtonType.YES,
            javafx.scene.control.ButtonType.NO
        );

        // Añadir icono si es posible
        try {
            if (alert.getDialogPane().getScene() != null && alert.getDialogPane().getScene().getWindow() instanceof Stage) {
                Stage alertStage = (Stage) alert.getDialogPane().getScene().getWindow();
                alertStage.getIcons().add(new javafx.scene.image.Image(Objects.requireNonNull(Utils.class.getResourceAsStream("/images/logo.png"))));
            }
        } catch (Exception ignored) {
            // Si algo falla, seguimos sin icono
        }

        java.util.Optional<javafx.scene.control.ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == javafx.scene.control.ButtonType.YES;
    }

    /**
     * Alias de showConfirmation para mantener compatibilidad con código existente
     * @deprecated Use showConfirmation instead
     */
    public static boolean confirmAction(String title, String message) {
        return showConfirmation(title, message);
    }

    public static void openWebpage(String s) {
        try {
            Desktop.getDesktop().browse(new java.net.URI(s));
        } catch (Exception e) {
            Logs.error(Utils.class, "No se pudo abrir la página web: {}", e.getMessage());
        }
    }

    public static void openDocument(String relativePath) {
        if (relativePath == null || relativePath.trim().isEmpty()) {
            Logs.error(Utils.class, "openDocument: ruta vacía");
            return;
        }

        try {
            // Si el usuario pasó una ruta absoluta (ej. C:\docs\file.pdf o /usr/share/...), abrirla directamente
            Path provided = Paths.get(relativePath);
            if (provided.isAbsolute()) {
                File absFile = provided.toFile();
                if (absFile.exists()) {
                    Desktop.getDesktop().open(absFile);
                } else {
                    Logs.error(Utils.class, "El archivo no existe: {}", absFile.getAbsolutePath());
                }
                return;
            }

            // Normalizar la ruta relativa: eliminar barras iniciales para evitar que Path la trate como absoluta
            String rel = relativePath.replaceFirst("^[\\\\/]+", "");

            // Obtener directorio base del ejecutable/jar o el working dir cuando se ejecuta desde IDE
            Path exePath = Paths.get(Utils.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI());

            Path baseDir = exePath.toFile().isFile()
                    ? exePath.getParent()
                    : Paths.get(System.getProperty("user.dir"));

            Path pdfPath = baseDir.resolve(rel).normalize();
            File pdf = pdfPath.toFile();

            // Primero intentar abrir desde el sistema de archivos (instalación)
            if (pdf.exists()) {
                Desktop.getDesktop().open(pdf);
                return;
            }

            // Si no existe en disco, intentar cargar desde classpath (archivo empaquetado en el JAR)
            String resourcePath = rel.startsWith("/") ? rel : "/" + rel;
            InputStream is = Utils.class.getResourceAsStream(resourcePath);
            if (is == null) {
                // Intentar sin la barra inicial por si el recurso está en el paquete raíz
                is = Utils.class.getResourceAsStream(rel);
            }

            if (is != null) {
                // Copiar a un archivo temporal y abrirlo
                Path tmp = Files.createTempFile("doc-", "-" + Paths.get(rel).getFileName().toString());
                tmp.toFile().deleteOnExit();
                Files.copy(is, tmp, StandardCopyOption.REPLACE_EXISTING);
                is.close();
                Desktop.getDesktop().open(tmp.toFile());
                return;
            }

            // No se encontró en ninguno de los lugares
            Logs.error(Utils.class, "El archivo no existe: {}", pdfPath.toAbsolutePath().toString());

        } catch (Exception e) {
            Logs.error(Utils.class, "No se pudo abrir el documento: {}", e.getMessage());
        }
    }
}
