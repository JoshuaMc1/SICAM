package com.joshuamc1.sicam.controllers.dashboard;

import com.joshuamc1.sicam.core.Logs;
import com.joshuamc1.sicam.core.dialog.PersonDialogHelper;
import com.joshuamc1.sicam.core.Utils;
import com.joshuamc1.sicam.core.PermissionHelper;
import com.joshuamc1.sicam.models.Person;
import com.joshuamc1.sicam.services.PersonService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.util.List;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class PeopleController {
    @FXML
    private TableView<Person> tblPeople;
    @FXML @SuppressWarnings("unused") private Button btnNew;
    @FXML @SuppressWarnings("unused") private Button btnEdit;
    @FXML @SuppressWarnings("unused") private Button btnDelete;
    @FXML private Button btnRefresh;
    @FXML private ToggleButton btnViewDeleted;
    private boolean showDeleted;
    private long loadRevision;
    @FXML
    private TableColumn<Person, Integer> colId;
    @FXML
    private TableColumn<Person, String> colStatus;
    @FXML
    private TableColumn<Person, String> colDni;
    @FXML
    private TableColumn<Person, String> colFirstName;
    @FXML
    private TableColumn<Person, String> colLastName;
    @FXML
    private TableColumn<Person, String> colPhone;
    @FXML
    private TableColumn<Person, String> colInternalCode;
    @FXML
    private TableColumn<Person, Void> colActions;
    @FXML
    private TextField txtSearch;

    // Controles de paginación
    @FXML private Button btnPrev;
    @FXML private Button btnNext;
    @FXML private Label lblPage;
    @FXML private ChoiceBox<Integer> choicePageSize;

    private final PersonService personService = new PersonService();

    // Permisos
    private boolean canRestore = false;

    private com.joshuamc1.sicam.core.PagedTableSupport<Person> pageOrder;
    private int currentPage = 1;
    private int pageSize = 50;
    private int totalItems = 0;
    private int totalPages = 1;

    private final ObservableList<Person> currentPageItems = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        // Configurar permisos primero
        canRestore = com.joshuamc1.sicam.core.PermissionHelper.canAccess("PERSONAS", "RESTORE");

        colId.setCellValueFactory(data -> new javafx.beans.property.SimpleObjectProperty<>(data.getValue().getInteger("id")));

        configureStatusColumn();

        // Columna de acciones - Mostrar botón Restaurar solo si está eliminado y tiene permiso
        colActions.setSortable(false);
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnRestore = new Button("Restaurar");
            {
                btnRestore.setStyle("-fx-font-size: 14; -fx-background-color: #4CAF50; -fx-text-fill: white; -fx-padding: 5 10;");
                btnRestore.setOnAction(e -> {
                    Person person = getTableView().getItems().get(getIndex());
                    restorePerson(person);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    Person person = getTableView().getItems().get(getIndex());
                    // Solo mostrar botón si está eliminado Y el usuario tiene permiso de restaurar
                    boolean isDeleted = person.get("deleted_at") != null;
                    if (isDeleted && canRestore) {
                        setGraphic(btnRestore);
                    } else {
                        setGraphic(null);
                    }
                }
            }
        });

        colDni.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getString("dni")));
        colFirstName.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getString("first_name")));
        colLastName.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getString("last_name")));
        colPhone.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getString("phone")));
        colInternalCode.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getString("internal_code")));

        colId.setStyle("-fx-font-size: 17;");
        colStatus.setStyle("-fx-font-size: 17;");
        colDni.setStyle("-fx-font-size: 17;");
        colFirstName.setStyle("-fx-font-size: 17;");
        colLastName.setStyle("-fx-font-size: 17;");
        colPhone.setStyle("-fx-font-size: 17;");
        colInternalCode.setStyle("-fx-font-size: 17;");
        colActions.setStyle("-fx-font-size: 17;");

        // Configurar visibilidad/estado de botones según permisos
        if (btnNew != null) PermissionHelper.hideIfNoPermission(btnNew, "PERSONAS", "CREATE");
        if (btnEdit != null) PermissionHelper.hideIfNoPermission(btnEdit, "PERSONAS", "UPDATE");
        if (btnDelete != null) PermissionHelper.hideIfNoPermission(btnDelete, "PERSONAS", "DELETE");

        Logs.info(getClass(), "Permisos de personas configurados");

        configurePermissions();
        updateDeletedView();
        btnRefresh.setOnAction(e -> loadPageFromRepo(1));
        txtSearch.textProperty().addListener((obs, oldValue, newValue) -> loadPageFromRepo(1));

        pageOrder = new com.joshuamc1.sicam.core.PagedTableSupport<>(tblPeople, () -> loadPageFromRepo(1));
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

        loadPageFromRepo(1);
    }

    private void configureStatusColumn() {
        // Columna de estado con badge visual
        colStatus.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getString("status")));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableView() == null || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Person person = getTableView().getItems().get(getIndex());
                    boolean deleted = person.isDeleted();
                    String text = deleted ? "Eliminado" : person.isActive() ? "Activo" : "Inactivo";
                    String color = deleted ? "#D32F2F" : person.isActive() ? "#4CAF50" : "#9E9E9E";
                    Label badge = new Label(text);
                    badge.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; -fx-padding: 5 10; -fx-background-radius: 5;");
                    setGraphic(badge);
                    setText(null);
                }
            }
        });

    }

    private void configurePermissions() {
        // Ya se configuró en initialize(), este método es para consistencia con otros controladores
        Logs.info(getClass(), "Configurando permisos de personas para: {}",
            com.joshuamc1.sicam.core.Session.getCurrentUser().getString("username"));

        // Los botones ya fueron configurados con hideIfNoPermission en initialize()
        // Este método adicional permite logging y futuras extensiones
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
        // Validar permiso en UI
        if (!PermissionHelper.validateAndShow("PERSONAS", "CREATE")) return;
        Person created = PersonDialogHelper.showCreatePersonDialog();
        if (created != null) {
            loadPageFromRepo(currentPage);
        }
    }

    @FXML
    public void onEdit() {
        // Validar permiso en UI
        if (!PermissionHelper.validateAndShow("PERSONAS", "UPDATE")) return;
        Person selected = tblPeople.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Utils.showAlert("Aviso", "Seleccione una persona");
            return;
        }
        Dialog<Person> dialog = PersonDialogHelper.createPersonDialog(selected);
        dialog.showAndWait().ifPresent(p -> {
            try {
                personService.update(
                    selected.getInteger("id"),
                    p.getString("dni"),
                    p.getString("first_name"),
                    p.getString("last_name"),
                    p.getString("phone"),
                    p.getString("internal_code")
                );
                loadPageFromRepo(currentPage);
                Utils.showAlert("Éxito", "Persona actualizada exitosamente");
            } catch (Exception e) {
                Logs.error(getClass(), "Error actualizando persona: {}", e.getMessage());
                Utils.showAlert("Error", "No se pudo actualizar la persona: " + e.getMessage());
            }
        });
    }

    @FXML
    public void onDelete() {
        deleteSelectedPerson();
    }

    private void loadPageFromRepo(int page) {
        final java.util.Comparator<Person> order = pageOrder.comparator(java.util.Comparator.comparing(
            (Person row) -> row.getInteger("id")).reversed());
        final int p = page;
        final String q = txtSearch == null ? "" : txtSearch.getText();
        final boolean deletedView = showDeleted;
        final int requestedSize = pageSize;
        final long revision = ++loadRevision;
        new Thread(() -> {
            try {
                com.joshuamc1.sicam.config.DatabaseConfig.openConnection();
                try {
                    List<Person> source = deletedView ? personService.findDeleted() : personService.findAll();
                    List<Person> all = filterPeople(source, deletedView, q);
                    all.sort(order);
                    int total = all.size();
                    int pages = Math.max(1, (int) Math.ceil(total / (double) requestedSize));
                    int pageToLoad = Math.min(Math.max(1, p), pages);
                    int offset = (pageToLoad - 1) * requestedSize;
                    List<Person> pageItems = new java.util.ArrayList<>(all.subList(offset, Math.min(offset + requestedSize, total)));

                    final int finalTotal = total;
                    final int finalPages = pages;
                    final int finalPageToLoad = pageToLoad;
                    final List<Person> finalPageItems = pageItems;

                    Platform.runLater(() -> {
                        if (revision != loadRevision) return;
                        totalItems = finalTotal;
                        totalPages = finalPages;
                        currentPage = finalPageToLoad;
                        pageOrder.publish(currentPageItems, finalPageItems == null ? java.util.List.of() : finalPageItems);
                        updatePaginationControls();
                    });
                } finally {
                    com.joshuamc1.sicam.config.DatabaseConfig.closeConnection();
                }
            } catch (Throwable t) {
                Logs.error(getClass(), "Error listando personas: {}", t.getMessage());
                Platform.runLater(() -> {
                    if (revision == loadRevision) Utils.showAlert("Error", "No se pudo cargar personas.");
                });
            }
        }).start();
    }

    static List<Person> filterPeople(List<Person> source, boolean deletedView, String query) {
        String search = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        return source.stream()
            .filter(person -> person.isDeleted() == deletedView)
            .filter(person -> search.isEmpty() || java.util.stream.Stream.of("dni", "first_name", "last_name", "internal_code")
                .map(person::getString).filter(java.util.Objects::nonNull)
                .anyMatch(value -> value.toLowerCase(java.util.Locale.ROOT).contains(search)))
            .sorted(java.util.Comparator.comparing((Person person) -> person.getInteger("id")).reversed())
            .collect(java.util.stream.Collectors.toList());
    }

    @FXML
    private void onViewDeleted() {
        if (!PermissionHelper.validateAndShow("SISTEMA", "VIEW_DELETED")) {
            showDeleted = false;
        } else {
            showDeleted = btnViewDeleted.isSelected();
        }
        updateDeletedView();
        loadPageFromRepo(1);
    }

    private void updateDeletedView() {
        boolean allowed = PermissionHelper.canAccess("SISTEMA", "VIEW_DELETED");
        if (!allowed) showDeleted = false;
        btnViewDeleted.setVisible(allowed);
        btnViewDeleted.setManaged(allowed);
        btnViewDeleted.setSelected(showDeleted);
        btnViewDeleted.setText(showDeleted ? "Ver personas" : "Ver eliminados");
        btnEdit.setDisable(showDeleted);
        btnDelete.setDisable(showDeleted && !PermissionHelper.canAccess("SISTEMA", "DESTRUCTIVE_OPERATIONS"));
        tblPeople.getSelectionModel().clearSelection();
    }

    private void updatePaginationControls() {
        if (lblPage != null) lblPage.setText(String.format("Página %d/%d (%d)", currentPage, totalPages, totalItems));
        if (btnPrev != null) btnPrev.setDisable(currentPage <= 1);
        if (btnNext != null) btnNext.setDisable(currentPage >= totalPages);
    }

    // ===== MÉTODOS DE SOFT DELETE Y RESTAURACIÓN =====

    /**
     * Elimina una persona (soft delete o hard delete según permisos)
     */
    private void deleteSelectedPerson() {
        if (!PermissionHelper.validateAndShow("PERSONAS", "DELETE")) {
            return;
        }

        Person sel = tblPeople.getSelectionModel().getSelectedItem();
        if (sel == null) {
            Utils.showAlert("Info", "Seleccione una persona para eliminar");
            return;
        }

        boolean canHardDelete = PermissionHelper.canAccess("SISTEMA", "DESTRUCTIVE_OPERATIONS");

        if (canHardDelete) {
            showDeleteOptionsDialog(sel);
        } else {
            boolean ok = Utils.confirmAction("Confirmar Eliminación",
                "¿Desea eliminar a: " + sel.getFullName() + "?");
            if (!ok) return;

            try {
                personService.softDelete(sel);
                Utils.showAlert("Éxito", "Persona eliminada correctamente");
                loadPageFromRepo(currentPage);
            } catch (Exception e) {
                Logs.error(getClass(), "Error eliminando persona", e);
                Utils.showAlert("Error", "No se pudo eliminar la persona: " + e.getMessage());
            }
        }
    }

    /**
     * Muestra dialog con opciones de eliminación (solo para ROOT)
     */
    private void showDeleteOptionsDialog(Person person) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Opciones de Eliminación");
        dialog.setHeaderText("Persona: " + person.getFullName());

        ButtonType softDeleteBtn = new ButtonType("Eliminación Reversible", ButtonBar.ButtonData.OK_DONE);
        ButtonType hardDeleteBtn = new ButtonType("Eliminación Permanente", ButtonBar.ButtonData.OTHER);
        dialog.getDialogPane().getButtonTypes().addAll(softDeleteBtn, hardDeleteBtn, ButtonType.CANCEL);

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
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
                    personService.softDelete(person);
                    Utils.showAlert("Éxito", "Persona eliminada correctamente");
                } else if ("hard".equals(type)) {
                    boolean confirm = Utils.confirmAction("⚠️ CONFIRMACIÓN FINAL",
                        "¿Está COMPLETAMENTE SEGURO de eliminar permanentemente?\n\n" +
                        "Esta acción NO SE PUEDE DESHACER.\n\n" +
                        "Persona: " + person.getFullName());
                    if (confirm) {
                        personService.hardDelete(person);
                        Utils.showAlert("Éxito", "Persona eliminada permanentemente");
                    } else {
                        return;
                    }
                }
                loadPageFromRepo(currentPage);
            } catch (Exception e) {
                Logs.error(getClass(), "Error eliminando persona", e);
                Utils.showAlert("Error", "No se pudo eliminar la persona: " + e.getMessage());
            }
        });
    }

    /**
     * Restaura una persona eliminada (desde columna de acciones)
     */
    private void restorePerson(Person person) {
        if (!PermissionHelper.validateAndShow("PERSONAS", "RESTORE")) return;
        boolean ok = Utils.confirmAction("Confirmar Restauración",
            "¿Desea restaurar a: " + person.getFullName() + "?");
        if (!ok) return;

        try {
            personService.restore(person);
            Utils.showAlert("Éxito", "Persona restaurada correctamente");
            loadPageFromRepo(currentPage);
        } catch (Exception e) {
            Logs.error(getClass(), "Error restaurando persona", e);
            Utils.showAlert("Error", "No se pudo restaurar la persona: " + e.getMessage());
        }
    }
}
