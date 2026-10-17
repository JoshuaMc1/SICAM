package com.joshuamc1.sicam.config;

import com.joshuamc1.sicam.core.DatabaseManager;
import com.joshuamc1.sicam.core.Logs;
import com.joshuamc1.sicam.core.Utils;
import com.joshuamc1.sicam.models.User;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Diálogo para la configuración inicial en producción cuando no existe ningún usuario administrador.
 */
public class InitialSetupDialog {

    /**
     * Verifica si es necesario mostrar el diálogo de configuración inicial (no existe usuario root).
     * Si es necesario, lo muestra de forma bloqueante.
     * Retorna true si el setup fue exitoso o no fue necesario; false si el usuario canceló.
     */
    public static boolean checkAndShowIfNeeded() {
        // Si ya marcó en AppState que el root fue creado, no mostrar
        if (AppState.isRootCreated()) {
            Logs.info(InitialSetupDialog.class, "Usuario root ya creado según estado de la app. No mostrar diálogo inicial.");
            return true;
        }

        long rootCount = 0L;
        boolean needRetryAfterMigrations = false;

        try {
            rootCount = DatabaseManager.callInConnection(() -> {
                // Verificar si existe un usuario con rol ROOT (id=1)
                Long c = User.count("rol_id = ?", 1);
                return c != null ? c : 0L;
            });
        } catch (Exception ex) {
            String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
            if (msg.contains("doesn't exist") || msg.contains("does not exist") || msg.contains("no such table")) {
                Logs.warn(InitialSetupDialog.class, "Tabla faltante al verificar root. Ejecutando migraciones y reintentando...");
                needRetryAfterMigrations = true;
            } else {
                Logs.error(InitialSetupDialog.class, "Error verificando usuario root: {}", ex.getMessage());
                // Intentaremos aún así mostrar diálogo; si falla de nuevo, el usuario verá el error
            }
        }

        if (needRetryAfterMigrations) {
            try {
                DatabaseManager.runInConnection(MigrationRunner::runMigrations);
                rootCount = DatabaseManager.callInConnection(() -> {
                    // Verificar si existe un usuario con rol ROOT (id=1)
                    Long c = User.count("rol_id = ?", 1);
                    return c != null ? c : 0L;
                });
            } catch (Exception ex2) {
                Logs.error(InitialSetupDialog.class, "Fallo tras migraciones al verificar root: {}", ex2.getMessage());
            }
        }

        if (rootCount == 0) {
            Logs.info(InitialSetupDialog.class, "No existe usuario root. Mostrando diálogo de creación inicial.");
            boolean success = showDialog();
            if (success) AppState.setRootCreated(true);
            return success;
        }

        // Si existen root pero AppState no lo marcó, sincronizarlo
        if (rootCount > 0 && !AppState.isRootCreated()) {
            AppState.setRootCreated(true);
        }

        return true;
    }

    /**
     * Muestra el diálogo de configuración inicial y retorna true si se creó el usuario admin exitosamente.
     */
    private static boolean showDialog() {
        final boolean[] result = {false};

        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.UTILITY);
        dialog.setTitle("Configuración Inicial - SYCAM");
        dialog.setResizable(false);

        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.TOP_CENTER);

        Label titleLabel = new Label("Bienvenido al Sistema de SYCAM");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Label infoLabel = new Label(
                "Es la primera vez que ejecuta el sistema.\n" +
                "Por favor, cree el usuario ROOT (acceso completo al sistema):"
        );
        infoLabel.setWrapText(true);
        infoLabel.setStyle("-fx-text-alignment: center;");

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.setAlignment(Pos.CENTER);

        Label nameLabel = new Label("Nombre completo:");
        TextField nameField = new TextField();
        nameField.setPromptText("Ej: Juan Pérez");

        Label usernameLabel = new Label("Nombre de usuario:");
        TextField usernameField = new TextField();
        usernameField.setPromptText("Ej: admin");

        Label passwordLabel = new Label("Contraseña:");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Mínimo 6 caracteres");

        Label confirmPasswordLabel = new Label("Confirmar contraseña:");
        PasswordField confirmPasswordField = new PasswordField();

        form.add(nameLabel, 0, 0);
        form.add(nameField, 1, 0);
        form.add(usernameLabel, 0, 1);
        form.add(usernameField, 1, 1);
        form.add(passwordLabel, 0, 2);
        form.add(passwordField, 1, 2);
        form.add(confirmPasswordLabel, 0, 3);
        form.add(confirmPasswordField, 1, 3);

        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER);

        Button createButton = new Button("Crear Usuario ROOT");
        createButton.setDefaultButton(true);
        createButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");

        Button cancelButton = new Button("Cancelar");
        cancelButton.setCancelButton(true);

        buttonBox.getChildren().addAll(createButton, cancelButton);

        root.getChildren().addAll(titleLabel, infoLabel, form, buttonBox);

        createButton.setOnAction(e -> {
            String name = nameField.getText().trim();
            String username = usernameField.getText().trim();
            String password = passwordField.getText();
            String confirmPassword = confirmPasswordField.getText();

            // Validaciones
            if (name.isEmpty() || username.isEmpty() || password.isEmpty()) {
                Utils.showAlert("Error de validación", "Todos los campos son obligatorios.");
                return;
            }

            if (password.length() < 6) {
                Utils.showAlert("Error de validación", "La contraseña debe tener al menos 6 caracteres.");
                return;
            }

            if (!password.equals(confirmPassword)) {
                Utils.showAlert("Error de validación", "Las contraseñas no coinciden.");
                return;
            }

            // Crear el usuario root
            try {
                DatabaseManager.callInConnection(() -> {
                    User rootUser = new User();
                    rootUser.set("name", name);
                    rootUser.set("username", username);
                    rootUser.setPassword(password);
                    rootUser.set("rol_id", 1); // Rol ROOT
                    rootUser.set("must_change_password", 0);
                    rootUser.saveIt();
                    Logs.info(InitialSetupDialog.class, "Usuario root inicial creado: {}", username);
                    return rootUser;
                });

                Utils.showAlert("Éxito", "Usuario ROOT creado exitosamente.\nYa puede iniciar sesión.");
                result[0] = true;
                dialog.close();
            } catch (Exception ex) {
                Logs.error(InitialSetupDialog.class, "Error creando usuario root inicial: {}", ex.getMessage());
                Utils.showAlert("Error", "Error al crear el usuario root: " + ex.getMessage());
            }
        });

        cancelButton.setOnAction(e -> {
            boolean confirm = Utils.confirmAction(
                    "Cancelar configuración",
                    "Si cancela, no podrá usar el sistema hasta crear el usuario root.\n¿Está seguro?"
            );
            if (confirm) {
                result[0] = false;
                dialog.close();
            }
        });

        dialog.setOnCloseRequest(e -> {
            if (!result[0]) {
                e.consume();
                cancelButton.fire();
            }
        });

        Scene scene = new Scene(root, 520, 420);
        dialog.setScene(scene);
        dialog.showAndWait();

        return result[0];
    }
}
