package com.joshuamc1.sicam.controllers.dashboard;

import com.joshuamc1.sicam.core.AsyncLoadable;
import com.joshuamc1.sicam.core.Logs;
import com.joshuamc1.sicam.models.dto.AuditDTO;
import com.joshuamc1.sicam.services.AuditService;
import com.joshuamc1.sicam.export.PdfAuditExporter;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class AuditController implements AsyncLoadable {
    @FXML private TableView<AuditDTO> tblAudit;
    @FXML private TableColumn<AuditDTO, Integer> colId;
    @FXML private TableColumn<AuditDTO, String> colUser;
    @FXML private TableColumn<AuditDTO, String> colAction;
    @FXML private TableColumn<AuditDTO, String> colTable;
    @FXML private TableColumn<AuditDTO, Integer> colRecordId;
    @FXML private TableColumn<AuditDTO, String> colDetails;
    @FXML private TableColumn<AuditDTO, String> colCreatedAt;

    @FXML private Button btnRefresh;
    @FXML private Button btnExportCsv;
    @FXML private Button btnExportPdf;
    @FXML private TextField txtSearch;

    // pagination controls
    @FXML private Button btnPrev;
    @FXML private Button btnNext;
    @FXML private Label lblPage;
    @FXML private ChoiceBox<Integer> choicePageSize;

    private final AuditService auditService = new AuditService();
    private final ObservableList<AuditDTO> data = FXCollections.observableArrayList();
    private volatile boolean loading = true;
    private Runnable onLoaded;

    private final DateTimeFormatter dbFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final DateTimeFormatter outFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // pagination state
    private int currentPage = 1; // 1-based
    private int pageSize = 25;
    private int totalItems = 0;
    private int totalPages = 1;

    @FXML
    public void initialize() {
        // Configurar permisos primero
        configurePermissions();

        // column mappings
        colId.setCellValueFactory(c -> javafx.beans.binding.Bindings.createObjectBinding(() -> c.getValue().getId()));
        colUser.setCellValueFactory(c -> javafx.beans.binding.Bindings.createObjectBinding(() -> {
            String n = c.getValue().getUsuarioName();
            return n == null || n.isEmpty() ? String.valueOf(c.getValue().getUsuarioId()) : n;
        }));
        colAction.setCellValueFactory(c -> javafx.beans.binding.Bindings.createObjectBinding(() -> c.getValue().getAccion()));
        colTable.setCellValueFactory(c -> javafx.beans.binding.Bindings.createObjectBinding(() -> c.getValue().getTablaAfectada()));
        colRecordId.setCellValueFactory(c -> javafx.beans.binding.Bindings.createObjectBinding(() -> c.getValue().getRegistroAfectadoId()));
        colDetails.setCellValueFactory(c -> javafx.beans.binding.Bindings.createObjectBinding(() -> c.getValue().getDetalles()));
        colCreatedAt.setCellValueFactory(c -> javafx.beans.binding.Bindings.createObjectBinding(() -> {
            String s = c.getValue().getFecha();
            if (s == null) return "";
            try {
                LocalDateTime dt = LocalDateTime.parse(s, dbFormatter);
                return dt.format(outFormatter);
            } catch (Exception ex) {
                return s;
            }
        }));

        // detalles cell factory (truncado)
        colDetails.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    String display = item.length() > 120 ? item.substring(0, 117) + "..." : item;
                    setText(display);
                    setTooltip(item.length() > 120 ? new Tooltip(item) : null);
                }
            }
        });

        colId.setStyle("-fx-font-size: 17;");
        colUser.setStyle("-fx-font-size: 17;");
        colAction.setStyle("-fx-font-size: 17;");
        colTable.setStyle("-fx-font-size: 17;");
        colRecordId.setStyle("-fx-font-size: 17;");
        colDetails.setStyle("-fx-font-size: 17;");
        colCreatedAt.setStyle("-fx-font-size: 17;");

        tblAudit.setItems(data);

        // pagination UI
        choicePageSize.setItems(FXCollections.observableArrayList(10,25,50,100));
        choicePageSize.setValue(pageSize);
        choicePageSize.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                pageSize = newV;
                currentPage = 1;
                loadPageAsync(currentPage);
            }
        });

        btnPrev.setOnAction(e -> {
            if (currentPage > 1) {
                currentPage--;
                loadPageAsync(currentPage);
            }
        });

        btnNext.setOnAction(e -> {
            if (currentPage < totalPages) {
                currentPage++;
                loadPageAsync(currentPage);
            }
        });

        btnRefresh.setOnAction(e -> {
            currentPage = 1;
            loadPageAsync(currentPage);
        });

        // Búsqueda automática con debounce de 500ms
        javafx.animation.PauseTransition searchPause = new javafx.animation.PauseTransition(javafx.util.Duration.millis(500));
        txtSearch.textProperty().addListener((obs, oldV, newVal) -> {
            searchPause.setOnFinished(event -> {
                currentPage = 1;
                loadPageAsync(currentPage);
            });
            searchPause.playFromStart();
        });

        btnExportCsv.setOnAction(e -> exportCsv());
        btnExportPdf.setOnAction(e -> exportPdf());

        // initial load
        loadPageAsync(currentPage);
    }

    private void configurePermissions() {
        com.joshuamc1.sicam.core.Logs.info(getClass(), "Configurando permisos de auditoría para: {}",
            com.joshuamc1.sicam.core.Session.getCurrentUser().getString("username"));

        // Los botones de exportación requieren permiso de EXPORT (si existe) o READ
        // Por ahora, si tiene acceso al módulo (AUDITORIA.READ), puede ver y exportar
        if (btnExportCsv != null) {
            com.joshuamc1.sicam.core.PermissionHelper.hideIfNoPermission(btnExportCsv, "AUDITORIA", "READ");
        }
        if (btnExportPdf != null) {
            com.joshuamc1.sicam.core.PermissionHelper.hideIfNoPermission(btnExportPdf, "AUDITORIA", "READ");
        }

        com.joshuamc1.sicam.core.Logs.info(getClass(), "Permisos de auditoría configurados - Solo lectura y exportación");
    }

    private void loadPageAsync(int page) {
        loading = true;
        int pageToLoad = Math.max(1, page);
        new Thread(() -> {
            try {
                String search = txtSearch.getText();
                int offset = (pageToLoad - 1) * pageSize;
                List<AuditDTO> items = auditService.getPageWithUser(offset, pageSize, search);
                int total = auditService.countWithFilter(search);

                Platform.runLater(() -> {
                    data.setAll(items);
                    totalItems = total;
                    totalPages = Math.max(1, (int) Math.ceil((double) totalItems / pageSize));
                    currentPage = Math.min(pageToLoad, totalPages);
                    updatePaginationControls();
                    loading = false;
                    if (onLoaded != null) onLoaded.run();
                });
            } catch (Throwable t) {
                Logs.error(this.getClass(), "Error cargando auditoría", t);
                Platform.runLater(() -> {
                    loading = false;
                    if (onLoaded != null) onLoaded.run();
                    showError("No se pudieron cargar registros de auditoría: " + t.getMessage());
                });
            }
        }).start();
    }

    private void updatePaginationControls() {
        lblPage.setText(String.format("Página %d/%d (%d)", currentPage, totalPages, totalItems));
        btnPrev.setDisable(currentPage <= 1);
        btnNext.setDisable(currentPage >= totalPages);
    }

    private void exportCsv() {
        // Validar permiso de auditoría para exportar
        if (!com.joshuamc1.sicam.core.PermissionHelper.validateAndShow("AUDITORIA", "READ")) {
            return;
        }

        // Mostrar diálogo de opciones
        var options = com.joshuamc1.sicam.core.dialog.AuditExportDialog.showDialog();
        if (options.isEmpty()) {
            return;
        }

        var exportOpts = options.get();

        // Obtener datos según la opción seleccionada
        List<AuditDTO> dataToExport;
        if (exportOpts.isExportAll()) {
            dataToExport = auditService.getAllWithUser();
        } else {
            dataToExport = auditService.getAllWithUserByDateRange(exportOpts.getFromDate(), exportOpts.getToDate());
        }

        if (dataToExport.isEmpty()) {
            showInfo("Sin datos", "No hay registros para exportar con los criterios seleccionados");
            return;
        }

        FileChooser fc = new FileChooser();
        fc.setTitle("Exportar auditoría a CSV");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        File f = fc.showSaveDialog(tblAudit.getScene().getWindow());
        if (f == null) return;

        try (BufferedWriter bw = Files.newBufferedWriter(f.toPath(), StandardCharsets.UTF_8)) {
            // header
            bw.write("id,usuario_id,usuario_name,accion,tabla_afectada,registro_afectado_id,detalles,fecha\n");
            for (AuditDTO r : dataToExport) {
                String line = String.format("%d,%s,%s,%s,%s,%s,%s,%s\n",
                        r.getId() != null ? r.getId() : 0,
                        r.getUsuarioId() != null ? r.getUsuarioId() : "",
                        quoteCsv(r.getUsuarioName()),
                        quoteCsv(r.getAccion()),
                        quoteCsv(r.getTablaAfectada()),
                        r.getRegistroAfectadoId() != null ? r.getRegistroAfectadoId() : "",
                        quoteCsv(r.getDetalles()),
                        quoteCsv(r.getFecha())
                );
                bw.write(line);
            }
            bw.flush();
            showInfo("Exportado", String.format("CSV exportado: %s%n%d registros exportados",
                f.getAbsolutePath(), dataToExport.size()));
        } catch (IOException ex) {
            Logs.error(this.getClass(), "Error exportando CSV", ex);
            showError("No se pudo exportar CSV: " + ex.getMessage());
        }
    }

    private @NotNull String quoteCsv(String s) {
        if (s == null) return "";
        String esc = s.replace("\"", "\"\"");
        if (esc.contains(",") || esc.contains("\n") || esc.contains("\r")) {
            return "\"" + esc + "\"";
        }
        return esc;
    }

    private void exportPdf() {
        // Validar permiso de auditoría para exportar
        if (!com.joshuamc1.sicam.core.PermissionHelper.validateAndShow("AUDITORIA", "READ")) {
            return;
        }

        // Mostrar diálogo de opciones
        var options = com.joshuamc1.sicam.core.dialog.AuditExportDialog.showDialog();
        if (options.isEmpty()) {
            return;
        }

        var exportOpts = options.get();

        // Obtener datos según la opción seleccionada
        List<AuditDTO> dataToExport;
        if (exportOpts.isExportAll()) {
            dataToExport = auditService.getAllWithUser();
        } else {
            dataToExport = auditService.getAllWithUserByDateRange(exportOpts.getFromDate(), exportOpts.getToDate());
        }

        if (dataToExport.isEmpty()) {
            showInfo("Sin datos", "No hay registros para exportar con los criterios seleccionados");
            return;
        }

        FileChooser fc = new FileChooser();
        fc.setTitle("Exportar auditoría a PDF");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
        File f = fc.showSaveDialog(tblAudit.getScene().getWindow());
        if (f == null) return;

        // Crear diálogo de progreso
        Dialog<Void> progressDialog = new Dialog<>();
        progressDialog.setTitle("Exportando PDF");
        ProgressBar pb = new ProgressBar(0);
        pb.setPrefWidth(300);
        Label lblStatus = new Label("Inicializando...");
        ButtonType cancelType = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        progressDialog.getDialogPane().getButtonTypes().add(cancelType);
        VBox box = new VBox(10, lblStatus, pb);
        box.setPrefWidth(320);
        progressDialog.getDialogPane().setContent(box);
        progressDialog.getDialogPane().setHeaderText(null);
        progressDialog.setResizable(false);
        progressDialog.getDialogPane().lookupButton(cancelType).addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            // marcar cancelación
            lblStatus.setText("Cancelando...");
        });

        // Task de exportación
        final boolean[] cancelled = {false};
        final List<AuditDTO> finalDataToExport = dataToExport;
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                try {
                    updateMessage("Preparando documento...");
                    PdfAuditExporter.export(finalDataToExport, f, p -> {
                        updateProgress(p, 1.0);
                        updateMessage(String.format("Procesando %.0f%%", p * 100));
                        if (cancelled[0]) {
                            updateMessage("Cancelado por el usuario");
                        }
                    }, () -> cancelled[0], com.joshuamc1.sicam.core.Session.getCurrentUsername());
                } catch (IOException ex) {
                    Logs.error(this.getClass(), "Error exportando PDF", ex);
                    Platform.runLater(() -> showError("No se pudo exportar PDF: " + ex.getMessage()));
                }
                return null;
            }
        };

        pb.progressProperty().bind(task.progressProperty());
        lblStatus.textProperty().bind(task.messageProperty());

        task.setOnSucceeded(ev -> {
            if (!cancelled[0]) {
                showInfo("Exportado", String.format("PDF exportado: %s%n%d registros exportados",
                    f.getAbsolutePath(), finalDataToExport.size()));
            }
            progressDialog.close();
        });
        task.setOnFailed(ev -> {
            showError("Fallo exportación PDF");
            progressDialog.close();
        });
        task.setOnCancelled(ev -> {
            cancelled[0] = true;
            showInfo("Cancelado", "Exportación cancelada");
            progressDialog.close();
        });

        // Botón cancelar efectúa cancel
        progressDialog.setOnCloseRequest(e -> {
            if (!task.isDone()) {
                cancelled[0] = true;
                task.cancel();
            }
        });

        Thread th = new Thread(task, "pdf-export-task");
        th.setDaemon(true);
        th.start();
        Stage owner = (Stage) tblAudit.getScene().getWindow();
        progressDialog.initOwner(owner);
        progressDialog.show();
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    private void showInfo(String title, String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    @Override
    public boolean isLoading() {
        return loading;
    }

    @Override
    public void setOnLoaded(Runnable callback) {
        this.onLoaded = callback;
    }
}
