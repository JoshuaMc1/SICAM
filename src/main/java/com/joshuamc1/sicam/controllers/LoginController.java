package com.joshuamc1.sicam.controllers;

import com.joshuamc1.sicam.controllers.dashboard.MainController;
import com.joshuamc1.sicam.core.Logs;
import com.joshuamc1.sicam.core.Session;
import com.joshuamc1.sicam.core.Authorization;
import com.joshuamc1.sicam.core.Utils;
import com.joshuamc1.sicam.models.User;
import com.joshuamc1.sicam.services.UserService;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Objects;

public class LoginController {
    private final UserService userService = new UserService();

    @FXML private TextField txtUsername;

    @FXML private PasswordField txtPassword;

    @FXML
    private void handleLogin(ActionEvent event) {
        String username = txtUsername.getText().trim();
        String password = txtPassword.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            Utils.showAlert("Error", "El usuario y la contraseña no pueden estar vacíos");
            return;
        }

        boolean sessionStarted = false;
        try {
            User user = userService.login(username, password);

            if (user == null) {
                Utils.showAlert("Error", "Usuario o contraseña incorrectos");
                Logs.error(this.getClass(), "Intento de inicio de sesión fallido para el usuario: " + username);
                return;
            }

            // FASE 8: Verificar si el usuario está bloqueado
            Integer userId = user.getInteger("id");
            if (userId != null && userId == -1) {
                // Usuario bloqueado (inactivo o eliminado)
                String reason = user.getString("blocked_reason");
                String status = user.getString("status");

                String message = "Acceso Denegado\n\n" + reason + "\n\n";
                if ("deleted".equals(status)) {
                    message += "Este usuario ha sido eliminado del sistema.\n" +
                              "Contacte al administrador para más información.";
                } else if ("inactive".equals(status)) {
                    message += "Este usuario ha sido desactivado temporalmente.\n" +
                              "Contacte al administrador para reactivar su cuenta.";
                } else {
                    message += "Contacte al administrador del sistema.";
                }

                Utils.showAlert("Acceso Denegado", message);
                Logs.warn(this.getClass(), "Intento de login bloqueado: username={}, status={}", username, status);
                return;
            }

            // Establecer sesión antes de cualquier auditoría (p.ej., cambio de contraseña forzado)
            Session.setCurrentUser(user);
            sessionStarted = true;
            Session.startHeartbeat();

            // Leer must_change_password de forma robusta (MySQL puede devolver boolean o tinyint)
            boolean mustChange = false;
            try {
                Object mcp = user.get("must_change_password");
                if (mcp instanceof Number) {
                    mustChange = ((Number) mcp).intValue() != 0;
                } else if (mcp instanceof Boolean) {
                    mustChange = (Boolean) mcp;
                } else if (mcp != null) {
                    String s = mcp.toString().trim().toLowerCase();
                    mustChange = s.equals("1") || s.equals("true") || s.equals("t") || s.equals("yes");
                }
            } catch (Exception ignore) {
                mustChange = false;
            }

            // Si el usuario debe cambiar la contraseña, forzar el cambio antes de abrir el dashboard
            if (mustChange) {
                boolean changed = forceChangePassword(user);
                if (!changed) {
                    Utils.showAlert("Aviso", "Debe cambiar la contraseña para continuar");
                    // Limpiar sesión si no completó el cambio
                    Session.setCurrentUser(null);
                    return;
                }
            }

            Authorization.requireActiveSession();
            Utils.showAlert("Success", "Bienvenido " + user.getString("name") + "!");
            openMainWindow(user);
        } catch (SecurityException e) {
            if (sessionStarted) Session.setCurrentUser(null);
            // Un segundo login rechazado no es un error genérico ni toma la sesión anterior.
            Utils.showAlert("Acceso denegado", e.getMessage());
        } catch (Exception e) {
            if (sessionStarted) Session.setCurrentUser(null);
            Logs.error(this.getClass(), "Error during login: {}", e.getMessage());
            Utils.showAlert("Error", "Ocurrió un error durante el inicio de sesión");
        }
    }

    private boolean forceChangePassword(User user) {
        // Mostrar un diálogo simple pidiendo nueva contraseña y confirmación
        javafx.scene.control.Dialog<String> d = new javafx.scene.control.Dialog<>();
        d.setTitle("Cambiar contraseña");
        d.setHeaderText("Debe cambiar su contraseña antes de continuar");
        d.getDialogPane().getButtonTypes().addAll(javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);

        PasswordField pf1 = new PasswordField();
        pf1.setPromptText("Nueva contraseña");
        PasswordField pf2 = new PasswordField();
        pf2.setPromptText("Confirmar contraseña");

        javafx.scene.layout.GridPane gp = new javafx.scene.layout.GridPane();
        gp.setHgap(10);
        gp.setVgap(10);
        gp.setPadding(new javafx.geometry.Insets(10));
        gp.addRow(0, new javafx.scene.control.Label("Nueva contraseña"), pf1);
        gp.addRow(1, new javafx.scene.control.Label("Confirmar contraseña"), pf2);

        javafx.scene.Node okButton = d.getDialogPane().lookupButton(javafx.scene.control.ButtonType.OK);
        okButton.setDisable(true);

        javafx.beans.binding.BooleanBinding invalid = pf1.textProperty().isEmpty().or(pf2.textProperty().isEmpty()).or(new javafx.beans.binding.BooleanBinding() {
            {
                bind(pf1.textProperty(), pf2.textProperty());
            }

            @Override
            protected boolean computeValue() {
                return !pf1.getText().equals(pf2.getText());
            }
        });
        okButton.disableProperty().bind(invalid);

        d.getDialogPane().setContent(gp);

        java.util.concurrent.atomic.AtomicBoolean result = new java.util.concurrent.atomic.AtomicBoolean(false);
        d.setResultConverter(bt -> {
            if (bt == javafx.scene.control.ButtonType.OK) {
                return pf1.getText();
            }
            return null;
        });

        java.util.Optional<String> res = d.showAndWait();
        if (res.isPresent()) {
            String newPwd = res.get();
            try {
                // Actualizar contraseña directamente
                Authorization.requireActiveSession();
                user.setPassword(newPwd);
                user.set("must_change_password", 0);
                user.saveIt();
                result.set(true);
            } catch (Exception e) {
                Logs.error(this.getClass(), "Error cambiando contraseña forzada: {}", e.getMessage());
                Utils.showAlert("Error", "Ocurrió un error al cambiar la contraseña: " + e.getMessage());
            }
        }

        return result.get();
    }

    private void openMainWindow(User user) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/dashboard/main.fxml"));
            Scene scene = new Scene(loader.load());
            Stage stage = new Stage();
            stage.setScene(scene);
            stage.setTitle("SICAM - Sistema de Control de Activos Municipales - Dashboard");
            stage.getIcons().add(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/images/logo.png"))));
            MainController controller = loader.getController();
            controller.setLoggedUser(user);
            stage.setMaximized(true);

            stage.setOnCloseRequest(evt -> {
                boolean ok = Utils.confirmAction("Confirmar salida", "¿Desea cerrar la aplicación?");
                if (!ok) {
                    evt.consume();
                }
            });

            stage.show();

            Stage loginStage = (Stage) txtUsername.getScene().getWindow();
            loginStage.close();
        } catch (IOException e) {
            Session.setCurrentUser(null);
            Utils.showAlert("Error", "No se pudo abrir la ventana principal");
            Logs.error(this.getClass(), "Failed to open main window: {}", e.getMessage());
            e.printStackTrace();
        }
    }
}
