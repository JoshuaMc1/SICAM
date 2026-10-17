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
    @FXML
    private TableColumn<Person, String> colId;
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

    private int currentPage = 1;
    private int pageSize = 50;
    private int totalItems = 0;
    private int totalPages = 1;

    private final ObservableList<Person> currentPageItems = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        // Configurar permisos primero
        canRestore = com.joshuamc1.sicam.core.PermissionHelper.canAccess("PERSONAS", "RESTORE");

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
                    Person person = getTableView().getItems().get(getIndex());
                    boolean isActive = person.isActive();
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
        final int p = page;
        new Thread(() -> {
            try {
                com.joshuamc1.sicam.config.DatabaseConfig.openConnection();
                try {
                    String q = txtSearch == null ? null : txtSearch.getText();

                    // Si tiene permiso de restaurar, mostrar todos (incluidos eliminados)
                    // Si NO tiene permiso, solo mostrar activos
                    long count = personService.count();
                    int total = (int) count;
                    int pages = Math.max(1, (int) Math.ceil(total / (double) pageSize));
                    int pageToLoad = Math.min(Math.max(1, p), pages);
                    int offset = (pageToLoad - 1) * pageSize;
                    List<Person> pageItems = personService.listPaged(offset, pageSize, q);

                    // Si tiene permiso de restaurar, incluir personas eliminadas en la lista
                    if (canRestore && pageItems != null) {
                        // Obtener también las eliminadas
                        List<Person> deleted = personService.findDeleted();
                        // Combinar y paginar manualmente
                        List<Person> all = new java.util.ArrayList<>(personService.findAll());
                        all.addAll(deleted);

                        // Filtrar por búsqueda si hay query
                        if (q != null && !q.isBlank()) {
                            String search = q.toLowerCase();
                            all = all.stream()
                                .filter(person -> person.getString("dni").toLowerCase().contains(search) ||
                                           person.getString("first_name").toLowerCase().contains(search) ||
                                           person.getString("last_name").toLowerCase().contains(search) ||
                                           (person.getString("internal_code") != null && person.getString("internal_code").toLowerCase().contains(search)))
                                .collect(java.util.stream.Collectors.toList());
                        }

                        total = all.size();
                        pages = Math.max(1, (int) Math.ceil(total / (double) pageSize));
                        pageToLoad = Math.min(Math.max(1, p), pages);
                        offset = (pageToLoad - 1) * pageSize;
                        int fromIndex = Math.min(offset, all.size());
                        int toIndex = Math.min(offset + pageSize, all.size());
                        pageItems = fromIndex < toIndex ? all.subList(fromIndex, toIndex) : java.util.List.of();
                    }

                    final int finalTotal = total;
                    final int finalPages = pages;
                    final int finalPageToLoad = pageToLoad;
                    final List<Person> finalPageItems = pageItems;

                    Platform.runLater(() -> {
                        totalItems = finalTotal;
                        totalPages = finalPages;
                        currentPage = finalPageToLoad;
                        currentPageItems.setAll(finalPageItems == null ? java.util.List.of() : finalPageItems);
                        tblPeople.setItems(currentPageItems);
                        updatePaginationControls();
                    });
                } finally {
                    com.joshuamc1.sicam.config.DatabaseConfig.closeConnection();
                }
            } catch (Throwable t) {
                Logs.error(getClass(), "Error listando personas: {}", t.getMessage());
                Platform.runLater(() -> Utils.showAlert("Error", "No se pudo cargar personas: " + t.getMessage()));
            }
        }).start();
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
