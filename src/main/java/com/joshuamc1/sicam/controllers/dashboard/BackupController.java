package com.joshuamc1.sicam.controllers.dashboard;

import com.joshuamc1.sicam.core.Logs;
import com.joshuamc1.sicam.core.PermissionHelper;
import com.joshuamc1.sicam.core.Session;
import com.joshuamc1.sicam.core.Utils;
import com.joshuamc1.sicam.models.Backup;
import com.joshuamc1.sicam.services.BackupService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Region;

import java.awt.Desktop;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Controlador para el módulo de Backups
 */
public class BackupController {

    @FXML private TableView<Backup> tblBackups;
    @FXML private TableColumn<Backup, Integer> colId;
    @FXML private TableColumn<Backup, String> colFilename;
    @FXML private TableColumn<Backup, String> colSize;
    @FXML private TableColumn<Backup, String> colUsuario;
    @FXML private TableColumn<Backup, String> colMensaje;
    @FXML private TableColumn<Backup, String> colFecha;
    @FXML private TableColumn<Backup, String> colStatus;

    @FXML private Button btnCreateBackup;
    @FXML private Button btnOpenFolder;
    @FXML private Button btnRefresh;
    @FXML private Button btnDelete;
    @FXML private Button btnCleanOld;
    @FXML private Button btnAutoBackupSettings;

    @FXML private Button btnPrev;
    @FXML private Button btnNext;
    @FXML private Label lblPage;
    @FXML private ChoiceBox<Integer> choicePageSize;

    private final BackupService backupService = new BackupService();
    private final ObservableList<Backup> currentPageItems = FXCollections.observableArrayList();

    private com.joshuamc1.sicam.core.PagedTableSupport<Backup> pageOrder;
    private long loadRevision;
    private int currentPage = 1;
    private int pageSize = 25;
    private int totalItems = 0;
    private int totalPages = 1;

    private final DecimalFormat sizeFormat = new DecimalFormat("#0.00");
    private final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    @FXML
    public void initialize() {
        configurePermissions();
        configureTable();
        configurePagination();
        loadPage(1);
    }

    private void configurePermissions() {
        Logs.info(getClass(), "Configurando permisos de backup para: {}",
            Session.getCurrentUser().getString("username"));

        // Solo ROOT y ADMIN pueden crear backups
        if (btnCreateBackup != null) {
            PermissionHelper.hideIfNoPermission(btnCreateBackup, "BACKUP", "CREATE");
        }

        // Solo ROOT y ADMIN pueden eliminar backups
        if (btnDelete != null) {
            PermissionHelper.hideIfNoPermission(btnDelete, "BACKUP", "DELETE");
        }

        if (btnCleanOld != null) {
            PermissionHelper.hideIfNoPermission(btnCleanOld, "BACKUP", "DELETE");
        }
    }

    private void configureTable() {
        colId.setCellValueFactory(data ->
            new javafx.beans.property.SimpleObjectProperty<>(data.getValue().getInteger("id")));

        colFilename.setCellValueFactory(data ->
            new SimpleStringProperty(data.getValue().getString("filename")));

        colSize.setCellValueFactory(data ->
            new SimpleStringProperty(data.getValue().getSizeFormatted()));

        colUsuario.setCellValueFactory(data -> {
            String usuario = data.getValue().getUsuarioNombre();
            // Si el usuario es SYSTEM o está vacío, mostrar como automático
            if (usuario == null || usuario.isEmpty() || "SYSTEM".equals(usuario)) {
                return new SimpleStringProperty("SYSTEM (Automático)");
            }
            return new SimpleStringProperty(usuario);
        });

        colMensaje.setCellValueFactory(data ->
            new SimpleStringProperty(data.getValue().getString("error_message")));

        colFecha.setCellValueFactory(data -> {
            try {
                Object createdAt = data.getValue().get("created_at");
                if (createdAt instanceof java.sql.Timestamp) {
                    LocalDateTime ldt = ((java.sql.Timestamp) createdAt).toLocalDateTime();
                    return new SimpleStringProperty(ldt.format(dateFormat));
                }
                return new SimpleStringProperty(createdAt != null ? createdAt.toString() : "");
            } catch (Exception e) {
                return new SimpleStringProperty("");
            }
        });

        colStatus.setCellValueFactory(data ->
            new SimpleStringProperty(data.getValue().getString("status")));

        // Estilo para status
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if ("SUCCESS".equals(item)) {
                        setStyle("-fx-text-fill: green; -fx-font-weight: bold; -fx-font-size: 17;");
                    } else {
                        setStyle("-fx-text-fill: red; -fx-font-weight: bold; -fx-font-size: 17;");
                    }
                }
            }
        });

        colId.setStyle("-fx-font-size: 17;");
        colFilename.setStyle("-fx-font-size: 17;");
        colSize.setStyle("-fx-font-size: 17;");
        colUsuario.setStyle("-fx-font-size: 17;");
        colMensaje.setStyle("-fx-font-size: 17;");
        colFecha.setStyle("-fx-font-size: 17;");
        colStatus.setStyle("-fx-font-size: 17;");

        tblBackups.setItems(currentPageItems);
        pageOrder = new com.joshuamc1.sicam.core.PagedTableSupport<>(tblBackups, () -> loadPage(1));
    }

    private void configurePagination() {
        choicePageSize.setItems(FXCollections.observableArrayList(10, 25, 50, 100));
        choicePageSize.setValue(pageSize);
        choicePageSize.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                pageSize = newV;
                currentPage = 1;
                loadPage(currentPage);
            }
        });

        btnPrev.setOnAction(e -> {
            if (currentPage > 1) {
                currentPage--;
                loadPage(currentPage);
            }
        });

        btnNext.setOnAction(e -> {
            if (currentPage < totalPages) {
                currentPage++;
                loadPage(currentPage);
            }
        });
    }

    @FXML
    public void onCreateBackup() {
        // Validar permiso
        if (!PermissionHelper.validateAndShow("BACKUP", "CREATE")) {
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "¿Crear backup de la base de datos?\n\nEsto puede tomar unos momentos.",
            ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirmar Backup");
        confirm.setHeaderText("Crear Backup");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.YES) {
            return;
        }

        // Crear diálogo de progreso
        Dialog<Void> progressDialog = new Dialog<>();
        progressDialog.setTitle("Creando Backup");
        progressDialog.setHeaderText("Por favor espere...");

        ProgressIndicator progress = new ProgressIndicator();
        progress.setPrefSize(100, 100);
        Label lblStatus = new Label("Creando backup de la base de datos...");
        lblStatus.setStyle("-fx-font-size: 14px;");

        VBox vbox = new VBox(15, progress, lblStatus);
        vbox.setStyle("-fx-padding: 20; -fx-alignment: center;");
        progressDialog.getDialogPane().setContent(vbox);
        progressDialog.getDialogPane().setPrefSize(400, 200);

        // Agregar botón de cancelar
        ButtonType cancelButton = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        progressDialog.getDialogPane().getButtonTypes().add(cancelButton);

        // Task asíncrono
        Task<Path> task = new Task<>() {
            @Override
            protected Path call() throws Exception {
                return backupService.createBackup();
            }
        };

        task.setOnSucceeded(e -> {
            if (progressDialog.isShowing()) {
                progressDialog.close();
            }
            Path backupPath = task.getValue();

            Logs.info(getClass(), "Task de backup completado exitosamente. Path: {}", backupPath);

            // Construir mensaje de éxito con información del backup
            if (backupPath != null && backupPath.getFileName() != null) {
                String filename = backupPath.getFileName().toString();
                long sizeBytes = 0;
                try {
                    sizeBytes = java.nio.file.Files.size(backupPath);
                } catch (Exception ex) {
                    Logs.warn(getClass(), "No se pudo obtener tamaño del archivo", ex);
                }

                double sizeMB = sizeBytes / (1024.0 * 1024.0);

                // Crear mensaje de texto simple que funcionará con Platform.runLater
                String mensaje = String.format(
                    "Archivo: %s\n" +
                    "Tamaño: %.2f MB (%.0f bytes)\n\n" +
                    "Ubicación:\n%s",
                    filename,
                    sizeMB,
                    (double) sizeBytes,
                    backupPath.getParent().toString()
                );

                // Usar Platform.runLater para asegurar que estamos en el hilo correcto
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Éxito");
                    alert.setHeaderText("Backup creado exitosamente");
                    alert.setContentText(mensaje);

                    // Expandir el diálogo para que quepa todo el contenido
                    alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
                    alert.getDialogPane().setMinWidth(550);

                    alert.showAndWait();
                });
            } else {
                Logs.warn(getClass(), "backupPath es null o no tiene nombre de archivo");
                Platform.runLater(() ->
                    Utils.showAlert("Éxito", "Backup creado exitosamente.\n\nPuede encontrarlo en el directorio de backups.")
                );
            }

            loadPage(currentPage);
        });

        task.setOnFailed(e -> {
            if (progressDialog.isShowing()) {
                progressDialog.close();
            }
            Throwable ex = task.getException();
            Logs.error(getClass(), "Error creando backup", ex);

            // Construir mensaje de error más detallado
            StringBuilder errorMsg = new StringBuilder("No se pudo crear el backup:\n\n");
            if (ex != null) {
                errorMsg.append("Error: ").append(ex.getMessage()).append("\n");
                if (ex.getCause() != null) {
                    errorMsg.append("\nCausa: ").append(ex.getCause().getMessage());
                }
            } else {
                errorMsg.append("Error desconocido");
            }

            Utils.showAlert("Error", errorMsg.toString());
        });

        task.setOnCancelled(e -> {
            if (progressDialog.isShowing()) {
                progressDialog.close();
            }
            Logs.info(getClass(), "Backup cancelado por el usuario");
            Utils.showAlert("Información", "Creación de backup cancelada");
        });

        // Manejar cancelación por el botón
        progressDialog.setOnCloseRequest(e -> {
            if (task.isRunning()) {
                task.cancel();
                Logs.info(getClass(), "Task de backup cancelado");
            }
        });

        // Ejecutar task
        Thread backupThread = new Thread(task, "backup-creator");
        backupThread.setDaemon(true); // Permitir que la aplicación cierre si el hilo está corriendo
        backupThread.start();
        progressDialog.showAndWait();
    }

    @FXML
    public void onOpenFolder() {
        if (!PermissionHelper.validateAndShow("BACKUP","READ")) return;
        try {
            Path backupDir = backupService.getBackupDirectory();
            if (Files.exists(backupDir)) {
                Desktop.getDesktop().open(backupDir.toFile());
            } else {
                Utils.showAlert("Aviso", "El directorio de backups no existe aún.");
            }
        } catch (Exception e) {
            Logs.error(getClass(), "Error abriendo directorio de backups", e);
            Utils.showAlert("Error", "No se pudo abrir el directorio: " + e.getMessage());
        }
    }

    @FXML
    public void onRefresh() {
        loadPage(currentPage);
    }

    @FXML
    public void onDelete() {
        // Validar permiso
        if (!PermissionHelper.validateAndShow("BACKUP", "DELETE")) {
            return;
        }

        Backup selected = tblBackups.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Utils.showAlert("Aviso", "Seleccione un backup para eliminar");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "¿Eliminar el backup '" + selected.getString("filename") + "'?\n\nEsta acción no se puede deshacer.",
            ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirmar Eliminación");
        confirm.setHeaderText("Eliminar Backup");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.YES) {
            return;
        }

        // Ejecutar en hilo separado para no bloquear UI
        final Integer backupId = selected.getInteger("id");
        new Thread(() -> {
            try {
                com.joshuamc1.sicam.config.DatabaseConfig.openConnection();
                backupService.deleteBackup(backupId);
                com.joshuamc1.sicam.config.DatabaseConfig.closeConnection();

                Platform.runLater(() -> {
                    Utils.showAlert("Éxito", "Backup eliminado exitosamente");
                    loadPage(currentPage);
                });
            } catch (Exception e) {
                com.joshuamc1.sicam.config.DatabaseConfig.closeConnection();
                Logs.error(getClass(), "Error eliminando backup", e);
                Platform.runLater(() ->
                    Utils.showAlert("Error", "No se pudo eliminar el backup: " + e.getMessage())
                );
            }
        }, "backup-delete").start();
    }

    @FXML
    public void onCleanOld() {
        // Validar permiso
        if (!PermissionHelper.validateAndShow("BACKUP", "DELETE")) {
            return;
        }

        // Diálogo para seleccionar cuántos mantener
        TextInputDialog dialog = new TextInputDialog("10");
        dialog.setTitle("Limpiar Backups Antiguos");
        dialog.setHeaderText("Mantener últimos backups");
        dialog.setContentText("¿Cuántos backups recientes desea mantener?");

        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty()) {
            return;
        }

        try {
            int keepLast = Integer.parseInt(result.get());

            if (keepLast < 1) {
                Utils.showAlert("Error", "Debe mantener al menos 1 backup");
                return;
            }

            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Se mantendrán los últimos " + keepLast + " backups y se eliminarán los demás.\n\n¿Continuar?",
                ButtonType.YES, ButtonType.NO);
            confirm.setTitle("Confirmar Limpieza");

            if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) {
                return;
            }

            // Ejecutar en hilo separado para no bloquear UI
            new Thread(() -> {
                try {
                    com.joshuamc1.sicam.config.DatabaseConfig.openConnection();
                    int deleted = backupService.cleanOldBackups(keepLast);
                    com.joshuamc1.sicam.config.DatabaseConfig.closeConnection();

                    Platform.runLater(() -> {
                        Utils.showAlert("Éxito", "Se eliminaron " + deleted + " backup(s) antiguo(s)");
                        loadPage(currentPage);
                    });
                } catch (Exception e) {
                    com.joshuamc1.sicam.config.DatabaseConfig.closeConnection();
                    Logs.error(getClass(), "Error limpiando backups antiguos", e);
                    Platform.runLater(() ->
                        Utils.showAlert("Error", "No se pudo limpiar backups: " + e.getMessage())
                    );
                }
            }, "backup-clean").start();

        } catch (NumberFormatException e) {
            Utils.showAlert("Error", "Ingrese un número válido");
        }
    }

    @FXML
    public void onAutoBackupSettings() {
        com.joshuamc1.sicam.core.AutoBackupManager autoBackup =
            com.joshuamc1.sicam.core.AutoBackupManager.getInstance();

        boolean currentlyEnabled = autoBackup.isAutoBackupEnabled();
        String lastStartup = autoBackup.getLastStartupBackupInfo();
        String lastShutdown = autoBackup.getLastShutdownBackupInfo();

        // Crear diálogo con información y opciones
        Alert dialog = new Alert(Alert.AlertType.INFORMATION);
        dialog.setTitle("Configuración de Backups Automáticos");
        dialog.setHeaderText("Estado de Backups Automáticos");

        String statusText = String.format(
            "Estado actual: %s\n\n" +
            "Funcionamiento:\n" +
            "• Backup al iniciar: Se crea automáticamente una vez por día\n" +
            "  la primera vez que se inicia la aplicación.\n\n" +
            "• Backup al cerrar: Se crea automáticamente cada vez que\n" +
            "  se cierra la aplicación (mantiene el más reciente del día).\n\n" +
            "Últimos backups automáticos:\n" +
            "• Inicio: %s\n" +
            "• Cierre: %s\n\n" +
            "¿Desea cambiar el estado de los backups automáticos?",
            currentlyEnabled ? "✅ HABILITADO" : "❌ DESHABILITADO",
            lastStartup,
            lastShutdown
        );

        dialog.setContentText(statusText);
        dialog.getDialogPane().setMinWidth(550);
        dialog.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);

        // Botones
        ButtonType btnToggle = new ButtonType(
            currentlyEnabled ? "Deshabilitar" : "Habilitar",
            ButtonBar.ButtonData.OK_DONE
        );
        ButtonType btnCancel = new ButtonType("Cerrar", ButtonBar.ButtonData.CANCEL_CLOSE);

        dialog.getButtonTypes().setAll(btnToggle, btnCancel);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == btnToggle) {
            // Cambiar estado
            boolean newState = !currentlyEnabled;
            autoBackup.setAutoBackupEnabled(newState);

            Utils.showAlert(
                "Configuración Actualizada",
                String.format(
                    "Backups automáticos %s exitosamente.\n\n" +
                    "%s",
                    newState ? "habilitados" : "deshabilitados",
                    newState
                        ? "• Al iniciar la app por primera vez cada día se creará un backup.\n" +
                          "• Al cerrar la app siempre se creará un backup."
                        : "• No se crearán backups automáticos.\n" +
                          "• Puede crear backups manualmente cuando lo necesite."
                )
            );
        }
    }

    private void loadPage(int page) {
        final long revision = ++loadRevision;
        final int requestedSize = pageSize;
        final var sort = pageOrder.sqlOrder(java.util.Map.of(
            colId, "id", colFilename, "filename", colSize, "size", colUsuario, "user",
            colMensaje, "message", colFecha, "date", colStatus, "status"));
        new Thread(() -> {
            try {
                com.joshuamc1.sicam.config.DatabaseConfig.openConnection();
                long count = backupService.countBackups();
                int pages = Math.max(1, (int) Math.ceil(count / (double) requestedSize));
                int pageToLoad = Math.min(Math.max(1, page), pages);
                List<Backup> items = backupService.listBackupsPaged((pageToLoad - 1) * requestedSize, requestedSize, sort);
                Platform.runLater(() -> {
                    if (revision != loadRevision) return;
                    totalItems = (int) count;
                    totalPages = pages;
                    currentPage = pageToLoad;
                    pageOrder.publish(currentPageItems, items);
                    updatePaginationControls();
                });
            } catch (Exception e) {
                Logs.error(getClass(), "Error cargando backups", e);
                Platform.runLater(() -> {
                    if (revision == loadRevision) Utils.showAlert("Error", "No se pudieron cargar los backups: " + e.getMessage());
                });
            } finally {
                com.joshuamc1.sicam.config.DatabaseConfig.closeConnection();
            }
        }, "backup-loader").start();
    }

    private void updatePaginationControls() {
        if (lblPage != null) {
            lblPage.setText(String.format("Página %d/%d (%d backups)", currentPage, totalPages, totalItems));
        }
        if (btnPrev != null) {
            btnPrev.setDisable(currentPage <= 1);
        }
        if (btnNext != null) {
            btnNext.setDisable(currentPage >= totalPages);
        }
    }
}

