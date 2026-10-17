package com.joshuamc1.sicam.controllers.dashboard;

import com.joshuamc1.sicam.config.DatabaseConfig;
import com.joshuamc1.sicam.core.Logs;
import com.joshuamc1.sicam.core.Utils;
import com.joshuamc1.sicam.models.User;
import com.joshuamc1.sicam.services.UserService;
import javafx.application.Platform;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import org.jetbrains.annotations.NotNull;

import java.time.Year;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class UsersController {
    @FXML
    private TableView<User> tblUsers;
    @FXML
    private TableColumn<User, String> colId;
    @FXML
    private TableColumn<User, String> colStatus;
    @FXML
    private TableColumn<User, String> colName;
    @FXML
    private TableColumn<User, String> colUsername;
    @FXML
    private TableColumn<User, String> colRole;
    @FXML
    private TableColumn<User, String> colCreated;
    @FXML
    private TableColumn<User, Void> colActions;
    @FXML
    private TextField txtSearch;

    // Botones de acción
    @FXML private Button btnNew;
    @FXML private Button btnEdit;
    @FXML private Button btnDelete;
    @FXML private Button btnResetPassword;
    @FXML private Button btnToggleStatus;

    private final UserService userService = new UserService();

    // Permisos
    private boolean canRestore = false;

    // Paginación
    @FXML private Button btnPrev;
    @FXML private Button btnNext;
    @FXML private Label lblPage;
    @FXML private ChoiceBox<Integer> choicePageSize;

    private int currentPage = 1;
    private int pageSize = 50;
    private int totalItems = 0;
    private int totalPages = 1;

    private final ObservableList<User> currentPageItems = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        // Configurar permisos primero
        canRestore = com.joshuamc1.sicam.core.PermissionHelper.canAccess("USUARIOS", "RESTORE");

        colId.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getInteger("id"))));

        // Columna de estado con badge visual
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                    setText(null);
                } else {
                    User user = getTableView().getItems().get(getIndex());
                    boolean isActive = user.isActiveStatus();
                    Label badge = new Label(isActive ? "Activo" : "Inactivo");
                    badge.setStyle(isActive
                        ? "-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-padding: 5 10; -fx-background-radius: 5;"
                        : "-fx-background-color: #9E9E9E; -fx-text-fill: white; -fx-padding: 5 10; -fx-background-radius: 5;");
                    setGraphic(badge);
                    setText(null);
                }
            }
        });

        // Columna de acciones - Mostrar botón Restaurar solo si está eliminado y tiene permiso
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnRestore = new Button("Restaurar");
            {
                btnRestore.setStyle("-fx-font-size: 14; -fx-background-color: #4CAF50; -fx-text-fill: white; -fx-padding: 5 10;");
                btnRestore.setOnAction(e -> {
                    User user = getTableView().getItems().get(getIndex());
                    restoreUser(user);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    User user = getTableView().getItems().get(getIndex());
                    // Solo mostrar botón si está eliminado Y el usuario tiene permiso de restaurar
                    boolean isDeleted = user.get("deleted_at") != null;
                    if (isDeleted && canRestore) {
                        setGraphic(btnRestore);
                    } else {
                        setGraphic(null);
                    }
                }
            }
        });

        colName.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getString("name")));
        colUsername.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getString("username")));
        colRole.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getRolNombre()));
        colCreated.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().get("created_at"))));

        colId.setStyle("-fx-font-size: 17;");
        colStatus.setStyle("-fx-font-size: 17;");
        colName.setStyle("-fx-font-size: 17;");
        colUsername.setStyle("-fx-font-size: 17;");
        colRole.setStyle("-fx-font-size: 17;");
        colCreated.setStyle("-fx-font-size: 17;");
        colActions.setStyle("-fx-font-size: 17;");

        // Inicializar paginación
        choicePageSize.setItems(FXCollections.observableArrayList(10,25,50,100));
        choicePageSize.setValue(pageSize);
        choicePageSize.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                pageSize = newV;
                currentPage = 1;
                loadPageFromRepo(currentPage);
            }
        });
        btnPrev.setOnAction(e -> {
            if (currentPage > 1) {
                currentPage--;
                loadPageFromRepo(currentPage);
            }
        });
        btnNext.setOnAction(e -> {
            if (currentPage < totalPages) {
                currentPage++;
                loadPageFromRepo(currentPage);
            }
        });

        // Búsqueda automática con debounce de 500ms
        javafx.animation.PauseTransition searchPause = new javafx.animation.PauseTransition(javafx.util.Duration.millis(500));
        txtSearch.textProperty().addListener((obs, oldVal, newVal) -> {
            searchPause.setOnFinished(event -> {
                currentPage = 1;
                loadPageFromRepo(currentPage);
            });
            searchPause.playFromStart();
        });

        loadPageFromRepo(1);
        configurePermissions();
    }

    private void configurePermissions() {
        Logs.info(getClass(), "Configurando permisos de usuarios para: {}",
            com.joshuamc1.sicam.core.Session.getCurrentUser().getString("username"));

        // Configurar visibilidad de botones según permisos
        if (btnNew != null) {
            com.joshuamc1.sicam.core.PermissionHelper.hideIfNoPermission(btnNew, "USUARIOS", "CREATE");
        }
        if (btnEdit != null) {
            com.joshuamc1.sicam.core.PermissionHelper.hideIfNoPermission(btnEdit, "USUARIOS", "UPDATE");
        }
        if (btnDelete != null) {
            com.joshuamc1.sicam.core.PermissionHelper.hideIfNoPermission(btnDelete, "USUARIOS", "DELETE");
        }
        if (btnResetPassword != null) {
            com.joshuamc1.sicam.core.PermissionHelper.hideIfNoPermission(btnResetPassword, "USUARIOS", "UPDATE");
        }

        Logs.info(getClass(), "Permisos de usuarios configurados");
    }


    @FXML
    public void onPrev() {
        if (currentPage > 1) {
            currentPage--;
            loadPageFromRepo(currentPage);
        }
    }

    @FXML
    public void onNext() {
        if (currentPage < totalPages) {
            currentPage++;
            loadPageFromRepo(currentPage);
        }
    }

    @FXML
    public void onNew() {
        // Validar permiso USUARIOS.CREATE
        if (!com.joshuamc1.sicam.core.PermissionHelper.validateAndShow("USUARIOS", "CREATE")) {
            return;
        }

        Dialog<User> dialog = userDialog(null);
        dialog.showAndWait().ifPresent(u -> {
            try {
                // Usar contraseña por defecto: "contraseña" + año actual
                String defaultPwd = "contraseña" + Year.now().getValue();

                // Crear usuario directamente con el modelo
                User newUser = new User();
                newUser.set("name", u.getString("name"));
                newUser.set("username", u.getString("username"));
                newUser.setPassword(defaultPwd);
                newUser.set("rol_id", u.get("rol_id"));
                newUser.set("must_change_password", 1);
                newUser.saveIt();

                loadPageFromRepo(currentPage);
                Utils.showAlert("Éxito", "Usuario creado. Contraseña por defecto: " + defaultPwd);
            } catch (Exception e) {
                Logs.error(getClass(), "Error creando usuario: {}", e.getMessage());
                Utils.showAlert("Error", "No se pudo crear el usuario: " + e.getMessage());
            }
        });
    }

    @FXML
    public void onEdit() {
        // Validar permiso USUARIOS.UPDATE
        if (!com.joshuamc1.sicam.core.PermissionHelper.validateAndShow("USUARIOS", "UPDATE")) {
            return;
        }

        User selected = tblUsers.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Utils.showAlert("Aviso", "Seleccione un usuario");
            return;
        }
        Dialog<User> dialog = userDialog(selected);
        dialog.showAndWait().ifPresent(u -> {
            try {
                // Actualizar directamente
                selected.set("name", u.getString("name"));
                selected.set("username", u.getString("username"));
                selected.set("rol_id", u.get("rol_id"));
                selected.saveIt();
                loadPageFromRepo(currentPage);
            } catch (Exception e) {
                Logs.error(getClass(), "Error actualizando usuario: {}", e.getMessage());
                Utils.showAlert("Error", "No se pudo actualizar el usuario: " + e.getMessage());
            }
        });
    }

    @FXML
    public void onDelete() {
        deleteSelectedUser();
    }

    @FXML
    public void onResetPassword() {
        // Validar permiso USUARIOS.UPDATE (resetear contraseña es una actualización)
        if (!com.joshuamc1.sicam.core.PermissionHelper.validateAndShow("USUARIOS", "UPDATE")) {
            return;
        }

        User selected = tblUsers.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Utils.showAlert("Aviso", "Seleccione un usuario");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "¿Restablecer la contraseña del usuario " + selected.getString("username") + " a la contraseña por defecto?", ButtonType.YES, ButtonType.NO);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                try {
                    String defaultPwd = "contraseña" + Year.now().getValue();
                    boolean ok = userService.resetPassword(selected.getInteger("id"), defaultPwd, true);
                    if (ok) {
                        Utils.showAlert("Éxito", "Contraseña restablecida a: " + defaultPwd);
                        loadPageFromRepo(currentPage);
                    } else {
                        Utils.showAlert("Error", "No se pudo restablecer la contraseña");
                    }
                } catch (Exception e) {
                    Logs.error(getClass(), "Error restableciendo contraseña: {}", e.getMessage());
                    Utils.showAlert("Error", "No se pudo restablecer la contraseña: " + e.getMessage());
                }
            }
        });
    }

    private void loadPageFromRepo(int page) {
        final int p = page;
        new Thread(() -> {
            try {
                DatabaseConfig.openConnection();
                String q = txtSearch == null ? null : txtSearch.getText();

                // Si tiene permiso de restaurar, mostrar todos (incluidos eliminados)
                // Si NO tiene permiso, solo mostrar activos
                long count = userService.countUsers();
                int total = (int) count;
                int pages = Math.max(1, (int) Math.ceil(total / (double) pageSize));
                int pageToLoad = Math.min(Math.max(1, p), pages);
                int offset = (pageToLoad - 1) * pageSize;
                List<User> pageItems = userService.listPaged(offset, pageSize, q);

                // Si tiene permiso de restaurar, incluir usuarios eliminados en la lista
                if (canRestore && pageItems != null) {
                    // Obtener también los eliminados
                    List<User> deleted = userService.findDeleted();
                    // Obtener todos los usuarios activos
                    List<User> allActive = userService.listPaged(0, 9999, q);
                    // Combinar
                    List<User> all = new java.util.ArrayList<>(allActive);
                    all.addAll(deleted);

                    // Filtrar usuario SYSTEM
                    all.removeIf(user -> {
                        String username = user.getString("username");
                        return username != null && username.equalsIgnoreCase("SYSTEM");
                    });

                    total = all.size();
                    pages = Math.max(1, (int) Math.ceil(total / (double) pageSize));
                    pageToLoad = Math.min(Math.max(1, p), pages);
                    offset = (pageToLoad - 1) * pageSize;
                    int fromIndex = Math.min(offset, all.size());
                    int toIndex = Math.min(offset + pageSize, all.size());
                    pageItems = fromIndex < toIndex ? all.subList(fromIndex, toIndex) : java.util.List.of();
                } else {
                    // Filtrar usuario SYSTEM (no debe aparecer en la lista)
                    if (pageItems != null) {
                        pageItems.removeIf(user -> {
                            String username = user.getString("username");
                            return username != null && username.equalsIgnoreCase("SYSTEM");
                        });
                    }
                }

                final int finalTotal = total;
                final int finalPages = pages;
                final int finalPageToLoad = pageToLoad;
                final List<User> finalPageItems = pageItems;

                Platform.runLater(() -> {
                    totalItems = finalTotal;
                    totalPages = finalPages;
                    currentPage = finalPageToLoad;
                    currentPageItems.setAll(finalPageItems == null ? java.util.List.of() : finalPageItems);
                    tblUsers.setItems(currentPageItems);
                    updatePaginationControls();
                });
            } catch (Throwable t) {
                Logs.error(getClass(), "Error listando usuarios: {}", t.getMessage());
                Platform.runLater(() -> Utils.showAlert("Error", "No se pudo cargar usuarios: " + t.getMessage()));
            } finally {
                DatabaseConfig.closeConnection();
            }
        }).start();
    }

    private void updatePaginationControls() {
        if (lblPage != null) lblPage.setText(String.format("Página %d/%d (%d)", currentPage, totalPages, totalItems));
        if (btnPrev != null) btnPrev.setDisable(currentPage <= 1);
        if (btnNext != null) btnNext.setDisable(currentPage >= totalPages);
    }

    private @NotNull Dialog<User> userDialog(User base) {
        Dialog<User> d = new Dialog<>();
        String lblStyle = "-fx-font-weight: bold; -fx-font-size: 17;";
        String inputStyle = "-fx-font-size: 17;";

        // Hacer el diálogo redimensionable y darle un tamaño inicial un poco mayor
        d.setResizable(true);
        d.getDialogPane().setPrefSize(680, 380);

        d.setTitle(base == null ? "Nuevo usuario" : "Editar usuario");
        ButtonType save = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        TextField txtName = new TextField();
        txtName.setPromptText("Nombre");
        TextField txtUsername = new TextField();
        txtUsername.setPromptText("Usuario");

        // ComboBox con los roles disponibles de la base de datos (excluyendo ROOT)
        ComboBox<com.joshuamc1.sicam.models.Rol> cmbRole = new ComboBox<>();

        // Cargar roles desde la base de datos
        try {
            List<com.joshuamc1.sicam.models.Rol> roles =
                com.joshuamc1.sicam.models.Rol.where("activo = ? AND id != ?", 1, 1).orderBy("nombre");
            cmbRole.getItems().addAll(roles);
        } catch (Exception e) {
            Logs.error(getClass(), "Error cargando roles: {}", e.getMessage());
        }

        // Configurar cómo se muestra cada rol
        cmbRole.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(com.joshuamc1.sicam.models.Rol role, boolean empty) {
                super.updateItem(role, empty);
                if (empty || role == null) {
                    setText(null);
                } else {
                    setText(role.getString("nombre") + " - " + role.getString("descripcion"));
                }
            }
        });
        cmbRole.setButtonCell(new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(com.joshuamc1.sicam.models.Rol role, boolean empty) {
                super.updateItem(role, empty);
                if (empty || role == null) {
                    setText(null);
                } else {
                    setText(role.getString("nombre"));
                }
            }
        });

        txtName.setStyle(inputStyle);
        txtUsername.setStyle(inputStyle);
        cmbRole.setStyle(inputStyle);

        // Helper texts para campos obligatorios
        Label helperName = new Label("Obligatorio");
        helperName.setStyle("-fx-text-fill: #d60000; -fx-font-size: 12px;");
        Label helperUsername = new Label("Obligatorio");
        helperUsername.setStyle("-fx-text-fill: #d60000; -fx-font-size: 12px;");
        Label helperRole = new Label("Obligatorio");
        helperRole.setStyle("-fx-text-fill: #d60000; -fx-font-size: 12px;");

        // Verificar si el usuario actual es root
        User currentUser = com.joshuamc1.sicam.core.Session.getCurrentUser();
        boolean isRoot = currentUser != null && "root".equalsIgnoreCase(currentUser.getRolNombre());

        if (base != null) {
            txtName.setText(base.getString("name"));
            txtUsername.setText(base.getString("username"));

            // Deshabilitar username al editar si NO es root
            if (!isRoot) {
                txtUsername.setDisable(true);
                txtUsername.setStyle(inputStyle + "; -fx-opacity: 0.6;");
            }

            // Seleccionar el rol actual
            com.joshuamc1.sicam.models.Rol rolActual = base.getRol();
            if (rolActual != null) {
                for (com.joshuamc1.sicam.models.Rol r : cmbRole.getItems()) {
                    if (r.getId().equals(rolActual.getId())) {
                        cmbRole.getSelectionModel().select(r);
                        break;
                    }
                }
            }
        } else {
            // Por defecto seleccionar el primer rol disponible (usualmente OPERADOR)
            if (!cmbRole.getItems().isEmpty()) {
                cmbRole.getSelectionModel().select(0);
            }
        }

        // Validación de username duplicado
        javafx.beans.property.BooleanProperty usernameExists = new javafx.beans.property.SimpleBooleanProperty(false);
        if (base == null) {
            // Solo validar en creación
            com.joshuamc1.sicam.utils.ValidationHelper.validateUniqueField(
                txtUsername,
                userService::existsByUsername,
                helperUsername,
                "El nombre de usuario ya existe"
            );

            // Actualizar el binding cuando cambie el estado de validación
            txtUsername.focusedProperty().addListener((obs, was, is) -> {
                if (!is && !txtUsername.getText().trim().isEmpty()) {
                    usernameExists.set(userService.existsByUsername(txtUsername.getText().trim()));
                }
            });
        } else {
            // Validar en edición excluyendo el usuario actual
            Integer currentId = base.getInteger("id");
            com.joshuamc1.sicam.utils.ValidationHelper.validateUniqueFieldWithId(
                txtUsername,
                currentId,
                userService::getIdByUsername,
                helperUsername,
                "El nombre de usuario ya existe"
            );

            txtUsername.focusedProperty().addListener((obs, was, is) -> {
                if (!is && !txtUsername.getText().trim().isEmpty()) {
                    Integer foundId = userService.getIdByUsername(txtUsername.getText().trim());
                    usernameExists.set(foundId != null && !foundId.equals(currentId));
                }
            });
        }

        Label lblName = new Label("Nombre");
        lblName.setStyle(lblStyle);
        Label lblUsername = new Label("Usuario");
        lblUsername.setStyle(lblStyle);
        Label lblRole = new Label("Rol");
        lblRole.setStyle(lblStyle);

        GridPane gp = new GridPane();
        gp.setHgap(10);
        gp.setVgap(10);
        gp.setPadding(new javafx.geometry.Insets(10));
        gp.add(lblName, 0, 0);
        gp.add(txtName, 1, 0);
        gp.add(helperName, 1, 1);
        gp.add(lblUsername, 0, 2);
        gp.add(txtUsername, 1, 2);
        gp.add(helperUsername, 1, 3);
        gp.add(lblRole, 0, 4);
        gp.add(cmbRole, 1, 4);
        gp.add(helperRole, 1, 5);

        // Permitir que los controles ocupen el espacio horizontal disponible
        gp.setPrefWidth(440);
        GridPane.setHgrow(txtName, javafx.scene.layout.Priority.ALWAYS);
        GridPane.setHgrow(txtUsername, javafx.scene.layout.Priority.ALWAYS);
        GridPane.setHgrow(cmbRole, javafx.scene.layout.Priority.ALWAYS);

        d.getDialogPane().setContent(gp);

        Node btnSave = d.getDialogPane().lookupButton(save);
        BooleanBinding invalid = txtName.textProperty().isEmpty()
                .or(txtUsername.textProperty().isEmpty())
                .or(cmbRole.getSelectionModel().selectedItemProperty().isNull())
                .or(usernameExists);
        btnSave.disableProperty().bind(invalid);

        d.setResultConverter(bt -> {
            if (bt == save) {
                String username = txtUsername.getText().trim();

                // Validar que no se intente crear/editar un usuario con nombre SYSTEM
                if (username.equalsIgnoreCase("SYSTEM")) {
                    Platform.runLater(() ->
                        Utils.showAlert("Error",
                            "El nombre de usuario 'SYSTEM' está reservado y no puede ser utilizado.\n\n" +
                            "Este usuario es exclusivo del sistema para operaciones automáticas.")
                    );
                    return null;
                }

                // Verificar una última vez antes de guardar
                if (!txtUsername.isDisabled()) {
                    Integer existingId = userService.getIdByUsername(username);
                    if (existingId != null) {
                        Integer currentId = base != null ? base.getInteger("id") : null;
                        if (currentId == null || !existingId.equals(currentId)) {
                            Platform.runLater(() -> Utils.showAlert("Error", "El nombre de usuario ya existe"));
                            return null;
                        }
                    }
                }

                User u = new User();
                u.set("name", txtName.getText());
                u.set("username", username);
                com.joshuamc1.sicam.models.Rol selectedRole = cmbRole.getValue();
                if (selectedRole != null) {
                    u.set("rol_id", selectedRole.getId());
                }
                return u;
            }
            return null;
        });

        return d;
    }

    // ===== MÉTODOS DE SOFT DELETE Y RESTAURACIÓN =====

    /**
     * Elimina un usuario (soft delete o hard delete según permisos)
     */
    private void deleteSelectedUser() {
        if (!com.joshuamc1.sicam.core.PermissionHelper.validateAndShow("USUARIOS", "DELETE")) {
            return;
        }

        User sel = tblUsers.getSelectionModel().getSelectedItem();
        if (sel == null) {
            Utils.showAlert("Info", "Seleccione un usuario para eliminar");
            return;
        }

        // No permitir eliminar usuarios ROOT
        if (sel.isRootRole()) {
            Utils.showAlert("Error", "No se permite eliminar usuarios con rol ROOT");
            return;
        }

        // No permitir eliminar usuario SYSTEM
        String username = sel.getString("username");
        if ("SYSTEM".equalsIgnoreCase(username)) {
            Utils.showAlert("Error", "No se permite eliminar el usuario SYSTEM");
            return;
        }

        boolean canHardDelete = com.joshuamc1.sicam.core.PermissionHelper.canAccess("SISTEMA", "DESTRUCTIVE_OPERATIONS");

        if (canHardDelete) {
            showDeleteOptionsDialog(sel);
        } else {
            boolean ok = Utils.confirmAction("Confirmar Eliminación",
                "¿Desea eliminar el usuario: " + sel.getString("username") + "?");
            if (!ok) return;

            try {
                userService.softDelete(sel);
                Utils.showAlert("Éxito", "Usuario eliminado correctamente");
                loadPageFromRepo(currentPage);
            } catch (Exception e) {
                Logs.error(getClass(), "Error eliminando usuario", e);
                Utils.showAlert("Error", "No se pudo eliminar el usuario: " + e.getMessage());
            }
        }
    }

    /**
     * Muestra dialog con opciones de eliminación (solo para ROOT)
     */
    private void showDeleteOptionsDialog(User user) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Opciones de Eliminación");
        dialog.setHeaderText("Usuario: " + user.getString("username"));

        ButtonType softDeleteBtn = new ButtonType("Eliminación Reversible", ButtonBar.ButtonData.OK_DONE);
        ButtonType hardDeleteBtn = new ButtonType("Eliminación Permanente", ButtonBar.ButtonData.OTHER);
        dialog.getDialogPane().getButtonTypes().addAll(softDeleteBtn, hardDeleteBtn, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(15);
        grid.setPadding(new javafx.geometry.Insets(20));

        Label lblInfo = new Label("Seleccione el tipo de eliminación:");
        lblInfo.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        Label lblSoft = new Label("• Eliminación Reversible: Se puede restaurar posteriormente");
        lblSoft.setStyle("-fx-font-size: 13px;");
        Label lblHard = new Label("• Eliminación Permanente: No se puede deshacer");
        lblHard.setStyle("-fx-font-size: 13px; -fx-text-fill: #d60000;");

        grid.add(lblInfo, 0, 0);
        grid.add(lblSoft, 0, 1);
        grid.add(lblHard, 0, 2);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn == softDeleteBtn) return "soft";
            if (btn == hardDeleteBtn) return "hard";
            return null;
        });

        dialog.showAndWait().ifPresent(type -> {
            try {
                if ("soft".equals(type)) {
                    userService.softDelete(user);
                    Utils.showAlert("Éxito", "Usuario eliminado correctamente");
                } else if ("hard".equals(type)) {
                    boolean confirm = Utils.confirmAction("⚠️ CONFIRMACIÓN FINAL",
                        "¿Está COMPLETAMENTE SEGURO de eliminar permanentemente?\n\n" +
                        "Esta acción NO SE PUEDE DESHACER.\n\n" +
                        "Usuario: " + user.getString("username"));
                    if (confirm) {
                        userService.hardDelete(user);
                        Utils.showAlert("Éxito", "Usuario eliminado permanentemente");
                    } else {
                        return;
                    }
                }
                loadPageFromRepo(currentPage);
            } catch (Exception e) {
                Logs.error(getClass(), "Error eliminando usuario", e);
                Utils.showAlert("Error", "No se pudo eliminar el usuario: " + e.getMessage());
            }
        });
    }

    /**
     * Restaura un usuario eliminado (desde columna de acciones)
     */
    private void restoreUser(User user) {
        boolean ok = Utils.confirmAction("Confirmar Restauración",
            "¿Desea restaurar el usuario: " + user.getString("username") + "?");
        if (!ok) return;

        try {
            userService.restore(user);
            Utils.showAlert("Éxito", "Usuario restaurado correctamente");
            loadPageFromRepo(currentPage);
        } catch (Exception e) {
            Logs.error(getClass(), "Error restaurando usuario", e);
            Utils.showAlert("Error", "No se pudo restaurar el usuario: " + e.getMessage());
        }
    }

    /**
     * Activa o desactiva un usuario
     */
    @FXML
    private void toggleUserStatus() {
        if (!com.joshuamc1.sicam.core.PermissionHelper.validateAndShow("USUARIOS", "UPDATE")) {
            return;
        }

        User sel = tblUsers.getSelectionModel().getSelectedItem();
        if (sel == null) {
            Utils.showAlert("Info", "Seleccione un usuario");
            return;
        }

        // No permitir desactivar usuario ROOT
        if (sel.isRootRole()) {
            Utils.showAlert("Error", "No se permite desactivar usuarios ROOT");
            return;
        }

        // No permitir desactivar usuario SYSTEM
        String username = sel.getString("username");
        if ("SYSTEM".equalsIgnoreCase(username)) {
            Utils.showAlert("Error", "No se permite desactivar el usuario SYSTEM");
            return;
        }

        try {
            if (sel.isActiveStatus()) {
                boolean ok = Utils.confirmAction("Confirmar",
                    "¿Desea desactivar al usuario: " + sel.getString("username") + "?\n\n" +
                    "El usuario no podrá iniciar sesión.");
                if (!ok) return;
                userService.markAsInactive(sel);
                Utils.showAlert("Éxito", "Usuario desactivado");
            } else {
                boolean ok = Utils.confirmAction("Confirmar",
                    "¿Desea activar al usuario: " + sel.getString("username") + "?");
                if (!ok) return;
                userService.markAsActive(sel);
                Utils.showAlert("Éxito", "Usuario activado");
            }
            loadPageFromRepo(currentPage);
        } catch (Exception e) {
            Logs.error(getClass(), "Error cambiando estado del usuario", e);
            Utils.showAlert("Error", e.getMessage());
        }
    }
}
