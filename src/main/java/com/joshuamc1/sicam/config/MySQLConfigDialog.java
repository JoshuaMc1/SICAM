package com.joshuamc1.sicam.config;

import com.joshuamc1.sicam.core.Logs;
import com.joshuamc1.sicam.core.Utils;
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

import java.sql.Connection;
import java.sql.DriverManager;

/**
 * Diálogo para la configuración inicial de la conexión a MySQL.
 * Se muestra la primera vez que se inicia la aplicación.
 */
public class MySQLConfigDialog {

    private static String host = "localhost";
    private static String port = "3306";
    private static String database = "sicam";
    private static String username = "root";
    private static String password = "";
    private static boolean connectionTested = false;
    private static boolean migrationsExecuted = false;

    /**
     * Muestra el diálogo de configuración de MySQL.
     * Retorna true si la configuración fue exitosa y las migraciones se ejecutaron.
     */
    public static boolean showConfigDialog() {
        final boolean[] result = {false};
        connectionTested = false;
        migrationsExecuted = false;

        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.UTILITY);
        dialog.setTitle("Configuración Inicial - Base de Datos MySQL");
        dialog.setResizable(false);

        VBox root = new VBox(20);
        root.setPadding(new Insets(25));
        root.setAlignment(Pos.TOP_CENTER);

        Label titleLabel = new Label("Configuración de Base de Datos MySQL");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        Label infoLabel = new Label(
                "Configure la conexión a la base de datos MySQL.\n" +
                "Asegúrese de que el servidor MySQL esté en ejecución y tenga una base de datos creada."
        );
        infoLabel.setWrapText(true);
        infoLabel.setStyle("-fx-text-alignment: center;");

        GridPane form = new GridPane();
        form.setHgap(15);
        form.setVgap(15);
        form.setAlignment(Pos.CENTER);

        // Host
        Label hostLabel = new Label("Host:");
        TextField hostField = new TextField(host);
        hostField.setPromptText("localhost o IP del servidor");

        // Puerto
        Label portLabel = new Label("Puerto:");
        TextField portField = new TextField(port);
        portField.setPromptText("3306");

        // Base de datos
        Label dbLabel = new Label("Base de datos:");
        TextField dbField = new TextField(database);
        dbField.setPromptText("Nombre de la base de datos");

        // Usuario
        Label userLabel = new Label("Usuario MySQL:");
        TextField userField = new TextField(username);
        userField.setPromptText("root");

        // Contraseña
        Label passLabel = new Label("Contraseña MySQL:");
        PasswordField passField = new PasswordField();
        passField.setText(password);
        passField.setPromptText("Contraseña del usuario MySQL");

        form.add(hostLabel, 0, 0);
        form.add(hostField, 1, 0);
        form.add(portLabel, 0, 1);
        form.add(portField, 1, 1);
        form.add(dbLabel, 0, 2);
        form.add(dbField, 1, 2);
        form.add(userLabel, 0, 3);
        form.add(userField, 1, 3);
        form.add(passLabel, 0, 4);
        form.add(passField, 1, 4);

        // Etiqueta de estado
        Label statusLabel = new Label("");
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(500);
        statusLabel.setAlignment(Pos.CENTER);

        // Botones
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER);

        Button testButton = new Button("Probar Conexión");
        testButton.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-size: 13px;");

        Button migrateButton = new Button("Ejecutar Migraciones");
        migrateButton.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white; -fx-font-size: 13px;");
        migrateButton.setDisable(true);

        Button saveButton = new Button("Guardar y Continuar");
        saveButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-size: 13px;");
        saveButton.setDisable(true);

        Button cancelButton = new Button("Cancelar");
        cancelButton.setStyle("-fx-background-color: #f44336; -fx-text-fill: white; -fx-font-size: 13px;");

        buttonBox.getChildren().addAll(testButton, migrateButton, saveButton, cancelButton);

        root.getChildren().addAll(titleLabel, infoLabel, form, statusLabel, buttonBox);

        // Evento: Probar conexión
        testButton.setOnAction(e -> {
            host = hostField.getText().trim();
            port = portField.getText().trim();
            database = dbField.getText().trim();
            username = userField.getText().trim();
            password = passField.getText();

            if (host.isEmpty() || port.isEmpty() || database.isEmpty() || username.isEmpty()) {
                statusLabel.setText("❌ Error: Todos los campos son obligatorios excepto la contraseña");
                statusLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                return;
            }

            statusLabel.setText("⏳ Probando conexión...");
            statusLabel.setStyle("-fx-text-fill: #FF9800; -fx-font-weight: bold;");

            new Thread(() -> {
                boolean success = testConnection();
                boolean hasExistingMigrations = success && hasMigrationsApplied();
                
                javafx.application.Platform.runLater(() -> {
                    if (success) {
                        connectionTested = true;
                        
                        if (hasExistingMigrations) {
                            // Base de datos ya tiene migraciones aplicadas
                            statusLabel.setText("✅ Conexión exitosa! La base de datos ya está configurada.\nPuede guardar la configuración directamente.");
                            statusLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                            migrationsExecuted = true; // Marcar como si ya se hubieran ejecutado
                            migrateButton.setDisable(true); // Deshabilitar botón de migraciones
                            migrateButton.setText("Migraciones Ya Aplicadas");
                            saveButton.setDisable(false); // Habilitar guardar directamente
                            AppState.setMigrationsApplied(true);
                        } else {
                            // Base de datos vacía, necesita migraciones
                            statusLabel.setText("✅ Conexión exitosa! La base de datos está vacía.\nDebe ejecutar las migraciones.");
                            statusLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                            migrateButton.setDisable(false);
                            migrateButton.setText("Ejecutar Migraciones");
                        }
                    } else {
                        statusLabel.setText("❌ Error de conexión. Verifique los datos e intente nuevamente.");
                        statusLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                        connectionTested = false;
                        migrateButton.setDisable(true);
                        saveButton.setDisable(true);
                    }
                });
            }).start();
        });

        // Evento: Ejecutar migraciones
        migrateButton.setOnAction(e -> {
            if (!connectionTested) {
                Utils.showAlert("Aviso", "Primero debe probar la conexión exitosamente");
                return;
            }

            Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION,
                    "¿Está seguro de ejecutar las migraciones en la base de datos?\n\n" +
                    "Esto creará las tablas necesarias para el sistema.",
                    ButtonType.YES, ButtonType.NO);
            confirmAlert.showAndWait().ifPresent(btn -> {
                if (btn == ButtonType.YES) {
                    statusLabel.setText("⏳ Ejecutando migraciones...");
                    statusLabel.setStyle("-fx-text-fill: #FF9800; -fx-font-weight: bold;");

                    new Thread(() -> {
                        boolean success = executeMigrations();
                        javafx.application.Platform.runLater(() -> {
                            if (success) {
                                statusLabel.setText("✅ Migraciones ejecutadas exitosamente! Puede guardar la configuración.");
                                statusLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                                migrationsExecuted = true;
                                saveButton.setDisable(false);
                                AppState.setMigrationsApplied(true);
                            } else {
                                statusLabel.setText("❌ Error ejecutando migraciones. Revise los logs para más información.");
                                statusLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                            }
                        });
                    }).start();
                }
            });
        });

        // Evento: Guardar configuración
        saveButton.setOnAction(e -> {
            if (!connectionTested) {
                Utils.showAlert("Aviso", "Debe probar la conexión antes de continuar");
                return;
            }
            
            if (!migrationsExecuted) {
                Utils.showAlert("Aviso", "Debe ejecutar las migraciones antes de continuar");
                return;
            }

            // Guardar configuración
            MySQLConfigManager.saveConfig(host, port, database, username, password);
            AppState.setDatabaseConfigured(true);

            Utils.showAlert("Éxito", "Configuración guardada exitosamente");
            result[0] = true;
            dialog.close();
        });

        // Evento: Cancelar
        cancelButton.setOnAction(e -> {
            boolean confirm = Utils.confirmAction(
                    "Cancelar configuración",
                    "Si cancela, no podrá usar el sistema hasta configurar la base de datos.\n¿Está seguro?"
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

        Scene scene = new Scene(root, 600, 550);
        dialog.setScene(scene);
        dialog.showAndWait();

        return result[0];
    }

    /**
     * Prueba la conexión a MySQL con los datos proporcionados.
     */
    private static boolean testConnection() {
        String url = String.format("jdbc:mysql://%s:%s/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                host, port, database);

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            try (Connection conn = DriverManager.getConnection(url, username, password)) {
                Logs.info(MySQLConfigDialog.class, "Conexión a MySQL probada exitosamente");
                return conn != null && !conn.isClosed();
            }
        } catch (Exception e) {
            Logs.error(MySQLConfigDialog.class, "Error probando conexión MySQL: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Verifica si la base de datos ya tiene migraciones aplicadas (tablas existentes).
     */
    private static boolean hasMigrationsApplied() {
        String url = String.format("jdbc:mysql://%s:%s/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                host, port, database);

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            try (Connection conn = DriverManager.getConnection(url, username, password)) {
                var meta = conn.getMetaData();
                var rs = meta.getTables(database, null, "users", new String[]{"TABLE"});
                boolean tablesExist = rs.next();
                Logs.info(MySQLConfigDialog.class, "Verificación de migraciones: tablas {} encontradas", 
                    tablesExist ? "" : "NO");
                return tablesExist;
            }
        } catch (Exception e) {
            Logs.error(MySQLConfigDialog.class, "Error verificando migraciones: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Ejecuta las migraciones en la base de datos MySQL.
     */
    private static boolean executeMigrations() {
        try {
            // Guardar temporalmente la configuración para que MigrationRunner la use
            MySQLConfigManager.saveConfig(host, port, database, username, password);

            // Abrir conexión y ejecutar migraciones
            DatabaseConfig.openConnection();
            MigrationRunner.runMigrations();
            DatabaseConfig.closeConnection();

            Logs.info(MySQLConfigDialog.class, "Migraciones ejecutadas exitosamente");
            return true;
        } catch (Exception e) {
            Logs.error(MySQLConfigDialog.class, "Error ejecutando migraciones: {}", e.getMessage());
            Logs.error(MySQLConfigDialog.class, "Stack: ", e);
            return false;
        }
    }

    public static boolean isConfigured() {
        return MySQLConfigManager.hasConfig() && AppState.isDatabaseConfigured();
    }
}
