package com.joshuamc1.sicam;

import com.joshuamc1.sicam.config.DatabaseConfig;
import com.joshuamc1.sicam.config.InitialSetupDialog;
import com.joshuamc1.sicam.config.MigrationRunner;
import com.joshuamc1.sicam.config.MySQLConfigDialog;
import com.joshuamc1.sicam.core.AutoBackupManager;
import com.joshuamc1.sicam.core.Logs;
import com.joshuamc1.sicam.core.Utils;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ButtonBar;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

public class App extends Application {

    public void start(@NotNull Stage stage) {
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            Logs.error(App.class, "Uncaught exception in thread " + t.getName(), e);
            javafx.application.Platform.runLater(() -> Utils.showAlert("Error inesperado",
                "No se pudo completar la operación. Si el problema continúa, revise el registro de errores."));
        });

        // Verificar si la base de datos MySQL está configurada
        if (!MySQLConfigDialog.isConfigured()) {
            Logs.info(App.class, "Base de datos no configurada. Mostrando diálogo de configuración MySQL...");
            boolean configured = MySQLConfigDialog.showConfigDialog();

            if (!configured) {
                Logs.info(App.class, "Configuración de MySQL cancelada por el usuario. Cerrando aplicación.");
                System.exit(0);
                return;
            }
        }

        // Intentar abrir conexión a la base de datos con manejo controlado de errores
        boolean connected = false;
        while (!connected) {
            try {
                DatabaseConfig.openConnection();
                connected = true;
            } catch (RuntimeException e) {
                Logs.error(App.class, "Error abriendo la conexión a la base de datos: {}", e.getMessage());

                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error de conexión a la base de datos");
                alert.setHeaderText("No se pudo conectar al servidor de base de datos");
                alert.setContentText("Hubo un problema conectando con la base de datos:\n" + e.getMessage() + "\n\nPuede reconfigurar la conexión, reintentar o salir. Si el problema persiste, contacte al soporte técnico.");

                ButtonType reconfig = new ButtonType("Reconfigurar");
                ButtonType retry = new ButtonType("Reintentar");
                ButtonType exit = new ButtonType("Salir", ButtonBar.ButtonData.CANCEL_CLOSE);

                alert.getButtonTypes().setAll(reconfig, retry, exit);

                Optional<ButtonType> result = alert.showAndWait();
                if (result.isPresent() && result.get() == reconfig) {
                    boolean conf = MySQLConfigDialog.showConfigDialog();
                    if (!conf) {
                        Logs.info(App.class, "Reconfiguración cancelada por el usuario. Cerrando aplicación.");
                        DatabaseConfig.closeConnection();
                        System.exit(0);
                        return;
                    }
                    // si reconfiguró, el bucle intentará conectar de nuevo
                } else if (result.isPresent() && result.get() == retry) {
                    // intentar de nuevo
                    Logs.info(App.class, "Usuario eligió reintentar la conexión");
                } else {
                    Logs.info(App.class, "Usuario eligió salir tras fallo de conexión. Cerrando aplicación.");
                    DatabaseConfig.closeConnection();
                    System.exit(0);
                    return;
                }
            }
        }

        // Asegurar esquema mínimo (idempotente) antes de proceder
        try {
            MigrationRunner.runMigrations();
        } catch (Exception ex) {
            Logs.error(App.class, "Error ejecutando migraciones en inicio: {}", ex.getMessage());
        }

        // Verificar si es necesario mostrar el diálogo de configuración inicial para crear usuario root
        if (!InitialSetupDialog.checkAndShowIfNeeded()) {
            // Si el usuario canceló la configuración inicial, cerrar la aplicación
            Logs.info(App.class, "Configuración inicial cancelada por el usuario. Cerrando aplicación.");
            DatabaseConfig.closeConnection();
            System.exit(0);
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Scene scene = new Scene(loader.load());
            stage.setScene(scene);
            stage.setTitle("SICAM - Sistema de Control de Activos Municipales - Login");
            stage.getIcons().add(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/images/logo.png"))));

            stage.setOnCloseRequest(evt -> {
                boolean ok = Utils.confirmAction("Confirmar salida", "¿Desea cerrar la aplicación?");
                if (!ok) {
                    evt.consume();
                }
            });

            // Verificar y crear backup automático de inicio (solo una vez por día)
            // Se ejecuta después del login cuando ya hay sesión activa
            AutoBackupManager.getInstance().checkStartupBackup();

            stage.show();
            Logs.info(App.class, "Interfaz mostrada");
        } catch (IOException e) {
            Logs.error(App.class, "Error loading FXML file: {}", e.getMessage());
            Utils.showAlert("Error", "No se pudo cargar la interfaz principal: " + e.getMessage());
        }
    }

    public void stop() {
        // Crear backup automático de cierre (siempre que se cierre)
        Logs.info(App.class, "Aplicación cerrándose, creando backup automático...");
        AutoBackupManager.getInstance().performShutdownBackup();

        DatabaseConfig.closeConnection();
        Logs.info(App.class, "App stopped");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
