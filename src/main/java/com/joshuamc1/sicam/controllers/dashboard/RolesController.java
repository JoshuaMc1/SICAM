package com.joshuamc1.sicam.controllers.dashboard;

import com.joshuamc1.sicam.core.Logs;
import com.joshuamc1.sicam.core.Utils;
import com.joshuamc1.sicam.models.Permiso;
import com.joshuamc1.sicam.models.Rol;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RolesController {
    @FXML
    private TableView<Rol> tableRoles;
    @FXML
    private TableColumn<Rol, Number> colId;
    @FXML
    private TableColumn<Rol, String> colNombre;
    @FXML
    private TableColumn<Rol, String> colDescripcion;
    @FXML
    private TableColumn<Rol, Boolean> colActivo;
    @FXML
    private TableColumn<Rol, Number> colUsuarios;
    @FXML
    private TableColumn<Rol, Void> colAcciones;
    @FXML
    private Button btnNuevoRol;

    @FXML
    public void initialize() {
        configurarTabla();
        cargarRoles();
        configurePermissions();

        btnNuevoRol.setOnAction(e -> onNuevoRol());
    }

    private void configurePermissions() {
        Logs.info(getClass(), "Configurando permisos de roles para: {}",
            com.joshuamc1.sicam.core.Session.getCurrentUser().getString("username"));

        // Solo ROOT puede gestionar roles
        if (btnNuevoRol != null) {
            com.joshuamc1.sicam.core.PermissionHelper.hideIfNoPermission(btnNuevoRol, "ROLES", "CREATE");
        }

        Logs.info(getClass(), "Permisos de roles configurados - Solo ROOT tiene acceso");
    }

    private void configurarTabla() {
        colId.setCellValueFactory(data ->
            new SimpleIntegerProperty(data.getValue().getInteger("id")));

        colNombre.setCellValueFactory(data ->
            new SimpleStringProperty(data.getValue().getString("nombre")));

        colDescripcion.setCellValueFactory(data ->
            new SimpleStringProperty(data.getValue().getString("descripcion")));

        colActivo.setCellValueFactory(data ->
            new SimpleBooleanProperty(data.getValue().isActivo()));

        colActivo.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item ? "Sí" : "No");
                    setStyle(item ? "-fx-text-fill: green; -fx-font-size: 17;" : "-fx-text-fill: red; -fx-font-size: 17;");
                }
            }
        });

        colUsuarios.setCellValueFactory(data ->
            new SimpleIntegerProperty(data.getValue().getUsuarios().size()));

        colAcciones.setCellFactory(col -> new TableCell<>() {
            private final Button btnPermisos = new Button("Permisos");
            private final Button btnEditar = new Button("Editar");
            private final Button btnEliminar = new Button("Eliminar");
            private final HBox hbox = new HBox(5, btnPermisos, btnEditar, btnEliminar);

            {
                btnPermisos.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-padding: 5 10; -fx-font-size: 14;");
                btnEditar.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white; -fx-padding: 5 10; -fx-font-size: 14;");
                btnEliminar.setStyle("-fx-background-color: #f44336; -fx-text-fill: white; -fx-padding: 5 10; -fx-font-size: 14;");

                btnPermisos.setOnAction(e -> {
                    Rol rol = getTableView().getItems().get(getIndex());
                    onGestionarPermisos(rol);
                });

                btnEditar.setOnAction(e -> {
                    Rol rol = getTableView().getItems().get(getIndex());
                    onEditarRol(rol);
                });

                btnEliminar.setOnAction(e -> {
                    Rol rol = getTableView().getItems().get(getIndex());
                    onEliminarRol(rol);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    Rol rol = getTableView().getItems().get(getIndex());
                    // No permitir eliminar o editar el rol ROOT
                    btnEliminar.setDisable(rol.isRoot());
                    btnEditar.setDisable(rol.isRoot());
                    setGraphic(hbox);
                }
            }
        });

        colId.setStyle("-fx-font-size: 17;");
        colNombre.setStyle("-fx-font-size: 17;");
        colDescripcion.setStyle("-fx-font-size: 17;");
        colActivo.setStyle("-fx-font-size: 17;");
        colUsuarios.setStyle("-fx-font-size: 17;");
        colAcciones.setStyle("-fx-font-size: 17;");
    }

    private void cargarRoles() {
        new Thread(() -> {
            try {
                List<Rol> roles = Rol.findAll().orderBy("id");
                Platform.runLater(() -> tableRoles.getItems().setAll(roles));
            } catch (Exception e) {
                Logs.error(getClass(), "Error cargando roles: {}", e.getMessage());
                Platform.runLater(() ->
                    Utils.showAlert("Error", "No se pudieron cargar los roles: " + e.getMessage()));
            }
        }).start();
    }

    private void onNuevoRol() {
        // Validar permiso ROLES.CREATE (Solo ROOT)
        if (!com.joshuamc1.sicam.core.PermissionHelper.validateAndShow("ROLES", "CREATE")) {
            return;
        }

        Dialog<Rol> dialog = crearDialogoRol(null);
        dialog.showAndWait().ifPresent(rol -> {
            try {
                rol.saveIt();
                cargarRoles();
                Utils.showAlert("Éxito", "Rol creado correctamente");
            } catch (Exception e) {
                Logs.error(getClass(), "Error creando rol: {}", e.getMessage());
                Utils.showAlert("Error", "No se pudo crear el rol: " + e.getMessage());
            }
        });
    }

    private void onEditarRol(@NotNull Rol rol) {
        // Validar permiso ROLES.UPDATE (Solo ROOT)
        if (!com.joshuamc1.sicam.core.PermissionHelper.validateAndShow("ROLES", "UPDATE")) {
            return;
        }

        // VALIDACIÓN ESPECIAL: No editar roles del sistema
        if (rol.isRoot()) {
            Utils.showAlert("Error", "No se puede editar el rol ROOT del sistema");
            Logs.warn(getClass(), "Intento de editar rol ROOT bloqueado");
            return;
        }

        // Validación adicional para otros roles del sistema
        String nombreRol = rol.getString("nombre");
        if (nombreRol != null && (nombreRol.equals("ADMIN") || nombreRol.equals("AUDITOR") ||
            nombreRol.equals("SUPERVISOR") || nombreRol.equals("OPERADOR") || nombreRol.equals("CONSULTOR"))) {
            Alert confirm = new Alert(Alert.AlertType.WARNING,
                "Estás editando un rol del sistema. Los cambios pueden afectar el funcionamiento. ¿Continuar?",
                ButtonType.YES, ButtonType.NO);
            if (confirm.showAndWait().orElse(ButtonType.NO) == ButtonType.NO) {
                return;
            }
        }

        Dialog<Rol> dialog = crearDialogoRol(rol);
        dialog.showAndWait().ifPresent(rolEditado -> {
            try {
                rol.set("nombre", rolEditado.getString("nombre"));
                rol.set("descripcion", rolEditado.getString("descripcion"));
                rol.set("activo", rolEditado.get("activo"));
                rol.saveIt();
                cargarRoles();
                Utils.showAlert("Éxito", "Rol actualizado correctamente");
            } catch (Exception e) {
                Logs.error(getClass(), "Error actualizando rol: {}", e.getMessage());
                Utils.showAlert("Error", "No se pudo actualizar el rol: " + e.getMessage());
            }
        });
    }

    private void onEliminarRol(@NotNull Rol rol) {
        deleteSelectedRole(rol);
    }

    private void onGestionarPermisos(Rol rol) {
        // Validar permiso ROLES.UPDATE (gestionar permisos es una actualización)
        if (!com.joshuamc1.sicam.core.PermissionHelper.validateAndShow("ROLES", "UPDATE")) {
            return;
        }

        Dialog<List<Integer>> dialog = crearDialogoPermisos(rol);
        dialog.showAndWait().ifPresent(permisosIds -> {
            try {
                rol.sincronizarPermisos(permisosIds);
                Utils.showAlert("Éxito", "Permisos actualizados correctamente");
            } catch (Exception e) {
                Logs.error(getClass(), "Error actualizando permisos: {}", e.getMessage());
                Utils.showAlert("Error", "No se pudieron actualizar los permisos: " + e.getMessage());
            }
        });
    }

    private @NotNull Dialog<Rol> crearDialogoRol(Rol base) {
        Dialog<Rol> dialog = new Dialog<>();
        dialog.setTitle(base == null ? "Nuevo Rol" : "Editar Rol");
        dialog.setResizable(true);
        dialog.getDialogPane().setPrefSize(830, 380);
        String lblStyle = "-fx-font-weight: bold; -fx-font-size: 17;";
        String inputStyle = "-fx-font-size: 17;";

        ButtonType saveButton = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButton, ButtonType.CANCEL);

        TextField txtNombre = new TextField();
        txtNombre.setPromptText("Nombre del rol (ej: OPERADOR)");

        TextArea txtDescripcion = new TextArea();
        txtDescripcion.setPromptText("Descripción del rol");
        txtDescripcion.setPrefRowCount(3);

        CheckBox chkActivo = new CheckBox("Activo");
        chkActivo.setSelected(true);

        txtNombre.setStyle(inputStyle);
        txtDescripcion.setStyle(inputStyle);

        // Helper texts para campos obligatorios
        Label helperNombre = new Label("Obligatorio");
        helperNombre.setStyle("-fx-text-fill: #d60000; -fx-font-size: 12px;");
        Label helperDescripcion = new Label("Obligatorio");
        helperDescripcion.setStyle("-fx-text-fill: #d60000; -fx-font-size: 12px;");

        // Label para validación de nombre duplicado
        Label helperNombreDuplicado = new Label("");
        helperNombreDuplicado.setStyle("-fx-text-fill: #d60000; -fx-font-size: 12px;");
        helperNombreDuplicado.setVisible(false);

        // Validación de nombre duplicado
        javafx.beans.property.BooleanProperty nombreDuplicado = new javafx.beans.property.SimpleBooleanProperty(false);

        if (base != null) {
            txtNombre.setText(base.getString("nombre"));
            txtDescripcion.setText(base.getString("descripcion"));
            chkActivo.setSelected(base.isActivo());

            // Validar al editar (excluir rol actual)
            Integer currentId = base.getInteger("id");
            txtNombre.focusedProperty().addListener((obs, was, is) -> {
                if (!is && !txtNombre.getText().trim().isEmpty()) {
                    String nombreBuscar = txtNombre.getText().trim().toUpperCase();
                    try {
                        Rol existente = Rol.findFirst("nombre = ?", nombreBuscar);
                        if (existente != null && !existente.getInteger("id").equals(currentId)) {
                            helperNombreDuplicado.setText("Ya existe un rol con este nombre");
                            helperNombreDuplicado.setVisible(true);
                            nombreDuplicado.set(true);
                        } else {
                            helperNombreDuplicado.setVisible(false);
                            nombreDuplicado.set(false);
                        }
                    } catch (Exception e) {
                        Logs.error(getClass(), "Error validando nombre de rol: {}", e.getMessage());
                    }
                }
            });
        } else {
            // Validar al crear
            txtNombre.focusedProperty().addListener((obs, was, is) -> {
                if (!is && !txtNombre.getText().trim().isEmpty()) {
                    String nombreBuscar = txtNombre.getText().trim().toUpperCase();
                    try {
                        Rol existente = Rol.findFirst("nombre = ?", nombreBuscar);
                        if (existente != null) {
                            helperNombreDuplicado.setText("Ya existe un rol con este nombre");
                            helperNombreDuplicado.setVisible(true);
                            nombreDuplicado.set(true);
                        } else {
                            helperNombreDuplicado.setVisible(false);
                            nombreDuplicado.set(false);
                        }
                    } catch (Exception e) {
                        Logs.error(getClass(), "Error validando nombre de rol: {}", e.getMessage());
                    }
                }
            });
        }

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        Label lblNombre = new Label("Nombre:");
        lblNombre.setStyle(lblStyle);
        Label lblDescripcion = new Label("Descripción:");
        lblDescripcion.setStyle(lblStyle);

        grid.add(lblNombre, 0, 0);
        grid.add(txtNombre, 1, 0);
        grid.add(helperNombre, 1, 1);
        grid.add(helperNombreDuplicado, 1, 2);
        grid.add(lblDescripcion, 0, 3);
        grid.add(txtDescripcion, 1, 3);
        grid.add(helperDescripcion, 1, 4);
        grid.add(chkActivo, 1, 5);

        dialog.getDialogPane().setContent(grid);

        // BooleanBinding para deshabilitar botón guardar si hay errores
        javafx.scene.Node btnSave = dialog.getDialogPane().lookupButton(saveButton);
        javafx.beans.binding.BooleanBinding isInvalid =
            com.joshuamc1.sicam.utils.ValidationHelper.isTextFieldEmpty(txtNombre)
            .or(txtDescripcion.textProperty().isEmpty()) // TextArea no usa ValidationHelper
            .or(nombreDuplicado);

        btnSave.disableProperty().bind(isInvalid);

        dialog.setResultConverter(buttonType -> {
            if (buttonType == saveButton) {
                // Validación final antes de guardar
                String nombreFinal = txtNombre.getText().trim().toUpperCase();
                try {
                    Rol existente = Rol.findFirst("nombre = ?", nombreFinal);
                    if (existente != null) {
                        Integer currentId = base != null ? base.getInteger("id") : null;
                        if (currentId == null || !existente.getInteger("id").equals(currentId)) {
                            Platform.runLater(() -> Utils.showAlert("Error", "Ya existe un rol con este nombre"));
                            return null;
                        }
                    }
                } catch (Exception e) {
                    Logs.error(getClass(), "Error validando nombre final: {}", e.getMessage());
                }

                Rol rol = base == null ? new Rol() : base;
                rol.set("nombre", nombreFinal);
                rol.set("descripcion", txtDescripcion.getText().trim());
                rol.set("activo", chkActivo.isSelected() ? 1 : 0);
                return rol;
            }
            return null;
        });

        return dialog;
    }

    private @NotNull Dialog<List<Integer>> crearDialogoPermisos(@NotNull Rol rol) {
        Dialog<List<Integer>> dialog = new Dialog<>();
        dialog.setTitle("Gestionar Permisos - " + rol.getString("nombre"));
        dialog.setResizable(true);
        dialog.getDialogPane().setPrefSize(780, 600);

        ButtonType saveButton = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButton, ButtonType.CANCEL);

        // Obtener permisos actuales del rol
        List<Permiso> permisosActuales = rol.getPermisos();
        List<Integer> idsActuales = new ArrayList<>();
        for (Permiso p : permisosActuales) {
            idsActuales.add(p.getInteger("id"));
        }

        // Crear checkboxes agrupados por módulo
        VBox content = new VBox(10);
        content.setPadding(new Insets(20));

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);

        Map<String, List<Permiso>> permisosPorModulo = Permiso.agruparPorModulo();
        List<CheckBox> checkBoxes = new ArrayList<>();

        for (Map.Entry<String, List<Permiso>> entry : permisosPorModulo.entrySet()) {
            String modulo = entry.getKey();
            List<Permiso> permisos = entry.getValue();

            Label lblModulo = new Label(modulo);
            lblModulo.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #2196F3;");
            content.getChildren().add(lblModulo);

            VBox permisoBox = new VBox(5);
            permisoBox.setPadding(new Insets(0, 0, 0, 20));

            for (Permiso permiso : permisos) {
                CheckBox cb = new CheckBox(permiso.getString("accion") + " - " +
                    permiso.getString("descripcion"));
                cb.setUserData(permiso.getInteger("id"));
                cb.setSelected(idsActuales.contains(permiso.getInteger("id")));
                checkBoxes.add(cb);
                permisoBox.getChildren().add(cb);
            }

            content.getChildren().add(permisoBox);
            content.getChildren().add(new Separator());
        }

        dialog.getDialogPane().setContent(scrollPane);

        dialog.setResultConverter(buttonType -> {
            if (buttonType == saveButton) {
                List<Integer> permisosSeleccionados = new ArrayList<>();
                for (CheckBox cb : checkBoxes) {
                    if (cb.isSelected()) {
                        permisosSeleccionados.add((Integer) cb.getUserData());
                    }
                }
                return permisosSeleccionados;
            }
            return null;
        });

        return dialog;
    }

    // ===== MÉTODOS DE SOFT DELETE Y RESTAURACIÓN =====

    /**
     * Elimina un rol (soft delete o hard delete según permisos)
     */
    private void deleteSelectedRole(Rol rol) {
        if (!com.joshuamc1.sicam.core.PermissionHelper.validateAndShow("ROLES", "DELETE")) {
            return;
        }

        // VALIDACIÓN ESPECIAL 1: No eliminar roles del sistema
        String nombreRol = rol.getString("nombre");
        java.util.List<String> rolesDelSistema = java.util.Arrays.asList("ROOT", "ADMIN", "AUDITOR", "SUPERVISOR", "OPERADOR", "CONSULTOR");

        if (nombreRol != null && rolesDelSistema.contains(nombreRol)) {
            Utils.showAlert("Error", "No se puede eliminar un rol del sistema: " + nombreRol);
            Logs.warn(getClass(), "Intento de eliminar rol del sistema bloqueado: {}", nombreRol);
            return;
        }

        // VALIDACIÓN ESPECIAL 2: No eliminar roles con usuarios activos asignados
        com.joshuamc1.sicam.services.RolService rolService = new com.joshuamc1.sicam.services.RolService();
        if (rolService.hasActiveUsers(rol.getInteger("id"))) {
            Utils.showAlert("Error",
                "No se puede eliminar el rol porque tiene usuarios activos asignados.\n\n" +
                "Debe primero reasignar o eliminar los usuarios de este rol.");
            Logs.warn(getClass(), "Intento de eliminar rol con usuarios activos bloqueado");
            return;
        }

        boolean canHardDelete = com.joshuamc1.sicam.core.PermissionHelper.canAccess("SISTEMA", "DESTRUCTIVE_OPERATIONS");

        if (canHardDelete) {
            showDeleteOptionsDialog(rol);
        } else {
            boolean ok = Utils.confirmAction("Confirmar Eliminación",
                "¿Desea eliminar el rol: " + rol.getString("nombre") + "?\n\n" +
                "Nota: Esta eliminación es reversible.");
            if (!ok) return;

            try {
                rolService.softDelete(rol);
                Utils.showAlert("Éxito", "Rol eliminado correctamente");
                cargarRoles();
            } catch (Exception e) {
                Logs.error(getClass(), "Error eliminando rol", e);
                Utils.showAlert("Error", "No se pudo eliminar el rol: " + e.getMessage());
            }
        }
    }

    /**
     * Muestra dialog con opciones de eliminación (solo para ROOT)
     */
    private void showDeleteOptionsDialog(Rol rol) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Opciones de Eliminación");
        dialog.setHeaderText("Rol: " + rol.getString("nombre"));

        ButtonType softDeleteBtn = new ButtonType("Eliminación Lógica", ButtonBar.ButtonData.OK_DONE);
        ButtonType hardDeleteBtn = new ButtonType("Eliminación Permanente", ButtonBar.ButtonData.OTHER);
        dialog.getDialogPane().getButtonTypes().addAll(softDeleteBtn, hardDeleteBtn, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(15);
        grid.setPadding(new Insets(20));

        Label lblInfo = new Label("Seleccione el tipo de eliminación:");
        lblInfo.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        Label lblSoft = new Label("• Eliminación Lógica: Reversible, se puede restaurar");
        lblSoft.setStyle("-fx-font-size: 13px;");
        Label lblHard = new Label("• Eliminación Permanente: No reversible, se borra definitivamente");
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

        com.joshuamc1.sicam.services.RolService rolService = new com.joshuamc1.sicam.services.RolService();

        dialog.showAndWait().ifPresent(type -> {
            try {
                if ("soft".equals(type)) {
                    rolService.softDelete(rol);
                    Utils.showAlert("Éxito", "Rol eliminado lógicamente");
                } else if ("hard".equals(type)) {
                    boolean confirm = Utils.confirmAction("⚠️ CONFIRMACIÓN FINAL",
                        "¿Está COMPLETAMENTE SEGURO de eliminar permanentemente?\n\n" +
                        "Esta acción NO SE PUEDE DESHACER.\n\n" +
                        "Rol: " + rol.getString("nombre"));
                    if (confirm) {
                        rolService.hardDelete(rol);
                        Utils.showAlert("Éxito", "Rol eliminado permanentemente");
                    } else {
                        return;
                    }
                }
                cargarRoles();
            } catch (Exception e) {
                Logs.error(getClass(), "Error eliminando rol", e);
                Utils.showAlert("Error", "No se pudo eliminar el rol: " + e.getMessage());
            }
        });
    }

    /**
     * Muestra vista de roles eliminados
     */
    @FXML
    private void showDeletedRoles() {
        if (!com.joshuamc1.sicam.core.PermissionHelper.canAccess("SISTEMA", "VIEW_DELETED")) {
            Utils.showAlert("Acceso Denegado", "No tiene permiso para ver roles eliminados");
            return;
        }

        javafx.concurrent.Task<List<Rol>> task = new javafx.concurrent.Task<>() {
            @Override
            protected List<Rol> call() {
                com.joshuamc1.sicam.config.DatabaseConfig.openConnection();
                try {
                    com.joshuamc1.sicam.services.RolService rolService = new com.joshuamc1.sicam.services.RolService();
                    return rolService.findDeleted();
                } finally {
                    com.joshuamc1.sicam.config.DatabaseConfig.closeConnection();
                }
            }
        };

        task.setOnSucceeded(evt -> {
            List<Rol> deletedRoles = task.getValue();
            if (deletedRoles.isEmpty()) {
                Utils.showAlert("Información", "No hay roles eliminados");
                return;
            }
            showDeletedRolesDialog(deletedRoles);
        });
        task.setOnFailed(evt -> {
            Utils.showAlert("Error", "No se pudieron cargar los roles eliminados");
        });

        Thread th = new Thread(task);
        th.setDaemon(true);
        th.start();
    }

    /**
     * Dialog para mostrar y restaurar roles eliminados
     */
    private void showDeletedRolesDialog(List<Rol> deletedRoles) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Roles Eliminados");
        dialog.setHeaderText("Total: " + deletedRoles.size() + " rol(es)");

        TableView<Rol> table = new TableView<>();
        table.setItems(javafx.collections.FXCollections.observableArrayList(deletedRoles));

        TableColumn<Rol, Number> colId = new TableColumn<>("ID");
        colId.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getInteger("id")));

        TableColumn<Rol, String> colNombre = new TableColumn<>("Nombre");
        colNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getString("nombre")));

        TableColumn<Rol, String> colDeletedAt = new TableColumn<>("Eliminado");
        colDeletedAt.setCellValueFactory(c -> {
            Object deletedAt = c.getValue().get("deleted_at");
            return new SimpleStringProperty(deletedAt != null ? deletedAt.toString() : "N/A");
        });

        TableColumn<Rol, Void> colRestore = new TableColumn<>("Acción");
        colRestore.setCellFactory(col -> new TableCell<>() {
            private final Button btnRestore = new Button("Restaurar");
            {
                btnRestore.setStyle("-fx-background-color: #28a745; -fx-text-fill: white;");
                btnRestore.setOnAction(e -> {
                    Rol r = getTableView().getItems().get(getIndex());
                    restoreRole(r, dialog);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btnRestore);
            }
        });

        table.getColumns().addAll(colId, colNombre, colDeletedAt, colRestore);
        table.setPrefHeight(400);

        dialog.getDialogPane().setContent(table);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    /**
     * Restaura un rol eliminado
     */
    private void restoreRole(Rol rol, Dialog<Void> parentDialog) {
        if (!com.joshuamc1.sicam.core.PermissionHelper.canAccess("ROLES", "RESTORE")) {
            Utils.showAlert("Acceso Denegado", "No tiene permiso para restaurar roles");
            return;
        }

        boolean ok = Utils.confirmAction("Confirmar Restauración",
            "¿Desea restaurar el rol: " + rol.getString("nombre") + "?");
        if (!ok) return;

        try {
            com.joshuamc1.sicam.services.RolService rolService = new com.joshuamc1.sicam.services.RolService();
            rolService.restore(rol);
            Utils.showAlert("Éxito", "Rol restaurado correctamente");
            parentDialog.close();
            cargarRoles();
        } catch (Exception e) {
            Logs.error(getClass(), "Error restaurando rol", e);
            Utils.showAlert("Error", "No se pudo restaurar el rol: " + e.getMessage());
        }
    }
}

