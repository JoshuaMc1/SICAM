package com.joshuamc1.sicam.controllers.dashboard;

import com.joshuamc1.sicam.core.Logs;
import com.joshuamc1.sicam.core.Session;
import com.joshuamc1.sicam.core.Utils;
import com.joshuamc1.sicam.models.User;
import com.joshuamc1.sicam.services.UserService;
import javafx.fxml.FXML;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class ProfileController {
    @FXML private TextField txtUsername;
    @FXML private TextField txtRole;
    @FXML private TextField txtName;
    @FXML private PasswordField txtCurrentPassword;
    @FXML private PasswordField txtNewPassword;
    @FXML private PasswordField txtConfirmPassword;

    private final UserService userService = new UserService();
    private User currentUser;

    @FXML
    public void initialize() {
        configurePermissions();
        loadCurrentUserData();
    }

    private void configurePermissions() {
        com.joshuamc1.sicam.core.Logs.info(getClass(), "Configurando perfil para: {}",
            com.joshuamc1.sicam.core.Session.getCurrentUser().getString("username"));

        // El perfil es accesible para todos los usuarios
        // Solo pueden editar su propio perfil (validación en onSave())

        com.joshuamc1.sicam.core.Logs.info(getClass(), "Perfil configurado - Acceso universal para editar perfil propio");
    }

    private void loadCurrentUserData() {
        try {
            Integer userId = Session.getCurrentUserId();
            if (userId == null) {
                Utils.showAlert("Error", "No hay sesión activa");
                return;
            }

            currentUser = userService.findById(userId);
            if (currentUser == null) {
                Utils.showAlert("Error", "No se pudo cargar la información del usuario");
                return;
            }

            // Cargar datos en los campos
            txtUsername.setText(currentUser.getString("username"));
            txtRole.setText(currentUser.getRolNombre());
            txtName.setText(currentUser.getString("name"));

        } catch (Exception e) {
            Logs.error(getClass(), "Error cargando datos del perfil: {}", e.getMessage());
            Utils.showAlert("Error", "Error al cargar los datos del perfil: " + e.getMessage());
        }
    }

    @FXML
    public void onSave() {
        try {
            // Validar nombre completo
            String name = txtName.getText().trim();
            if (name.isEmpty()) {
                Utils.showAlert("Error de validación", "El nombre completo es obligatorio");
                return;
            }

            // Verificar si quiere cambiar contraseña
            String currentPassword = txtCurrentPassword.getText();
            String newPassword = txtNewPassword.getText();
            String confirmPassword = txtConfirmPassword.getText();

            boolean changingPassword = !currentPassword.isEmpty() || !newPassword.isEmpty() || !confirmPassword.isEmpty();

            if (changingPassword) {
                // Validar contraseña actual
                if (currentPassword.isEmpty()) {
                    Utils.showAlert("Error de validación", "Debe ingresar su contraseña actual para cambiarla");
                    return;
                }

                // Verificar que la contraseña actual sea correcta
                if (!currentUser.validatePassword(currentPassword)) {
                    Utils.showAlert("Error de validación", "La contraseña actual es incorrecta");
                    return;
                }

                // Validar nueva contraseña
                if (newPassword.length() < 6) {
                    Utils.showAlert("Error de validación", "La nueva contraseña debe tener al menos 6 caracteres");
                    return;
                }

                if (!newPassword.equals(confirmPassword)) {
                    Utils.showAlert("Error de validación", "Las contraseñas nuevas no coinciden");
                    return;
                }
            }

            // Actualizar usuario
            String username = currentUser.getString("username");
            String passwordToUpdate = changingPassword ? newPassword : null;

            boolean success;
            if (currentUser.isRootRole()) {
                // Permitir que root modifique su propio nombre y contraseña
                currentUser.set("name", name);
                if (passwordToUpdate != null) {
                    currentUser.setPassword(passwordToUpdate);
                }
                currentUser.saveIt();
                success = true;
            } else {
                currentUser.set("name", name);
                if (passwordToUpdate != null) {
                    currentUser.setPassword(passwordToUpdate);
                }
                currentUser.saveIt();
                success = true;
            }

            if (success) {
                // Recargar el usuario actualizado en la sesión
                currentUser = userService.findById(currentUser.getInteger("id"));
                Session.setCurrentUser(currentUser);

                Utils.showAlert("Éxito", "Perfil actualizado correctamente");

                // Limpiar campos de contraseña
                txtCurrentPassword.clear();
                txtNewPassword.clear();
                txtConfirmPassword.clear();

                // Recargar datos
                loadCurrentUserData();
            } else {
                Utils.showAlert("Error", "No se pudo actualizar el perfil");
            }

        } catch (Exception e) {
            Logs.error(getClass(), "Error guardando perfil: {}", e.getMessage());
            Utils.showAlert("Error", "Error al guardar el perfil: " + e.getMessage());
        }
    }

    @FXML
    public void onCancel() {
        // Recargar datos originales y limpiar campos de contraseña
        loadCurrentUserData();
        txtCurrentPassword.clear();
        txtNewPassword.clear();
        txtConfirmPassword.clear();
    }
}
