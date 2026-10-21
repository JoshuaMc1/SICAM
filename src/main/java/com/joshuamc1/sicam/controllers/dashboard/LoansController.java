package com.joshuamc1.sicam.controllers.dashboard;

import com.joshuamc1.sicam.config.DatabaseConfig;
import com.joshuamc1.sicam.core.Logs;
import com.joshuamc1.sicam.core.dialog.PersonDialogHelper;
import com.joshuamc1.sicam.core.Session;
import com.joshuamc1.sicam.core.Utils;
import com.joshuamc1.sicam.models.Person;
import com.joshuamc1.sicam.models.Product;
import com.joshuamc1.sicam.models.Transaction;
import com.joshuamc1.sicam.utils.TransactionStatusBadge;
import com.joshuamc1.sicam.models.TransactionItem;
import com.joshuamc1.sicam.models.dto.TransactionItemDTO;
import com.joshuamc1.sicam.services.PersonService;
import com.joshuamc1.sicam.services.ProductService;
import com.joshuamc1.sicam.services.TransactionService;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class LoansController {
    @FXML
    private TableView<Transaction> tblTransactions;
    @FXML private Button btnNewLoan;
    @FXML private Button btnRefresh;
    @FXML private Button btnPendingLoans;
    @FXML private Button btnOverdueLoans;
    @FXML private Button btnAllLoans;
    @FXML private Button btnViewInvalidated;
    @FXML private TextField txtSearch;
    @FXML
    private TableColumn<Transaction, Integer> colId;
    @FXML
    private TableColumn<Transaction, String> colLoanCode;
    @FXML
    private TableColumn<Transaction, String> colLoanStatus;  // Estado del préstamo (pending, returned, etc)
    @FXML
    private TableColumn<Transaction, String> colRecordStatus; // Estado del registro (active, inactive)
    @FXML
    private TableColumn<Transaction, String> colQuantity;
    @FXML
    private TableColumn<Transaction, String> colItems;
    @FXML
    private TableColumn<Transaction, String> colDate;
    @FXML private TableColumn<Transaction, String> colExpectedReturn;
    @FXML
    private TableColumn<Transaction, String> colDni;
    @FXML
    private TableColumn<Transaction, String> colPerson;
    @FXML
    private TableColumn<Transaction, Void> colActions;

    // Controles UI
    @FXML private Button btnPrev;
    @FXML private Button btnNext;
    @FXML private Label lblPage;
    @FXML private ChoiceBox<Integer> choicePageSize;
    @FXML private javafx.scene.control.ChoiceBox<String> cmbFilter; // ComboBox de filtros

    // Estado de paginación
    private com.joshuamc1.sicam.core.PagedTableSupport<Transaction> pageOrder;
    private long loadRevision;
    private int currentPage = 1;
    private int pageSize = 25;
    private int totalItems = 0;
    private int totalPages = 1;
    private String currentFilter = "all";
    private boolean showingInvalidated = false;

    // Permisos del usuario
    private boolean canInvalidate = false;
    private boolean canRestore = false;
    private boolean canViewDeleted = false;

    private final ObservableList<Transaction> currentPageItems = FXCollections.observableArrayList();

    private final TransactionService transactionService = new TransactionService();
    private final ProductService productService = new ProductService();
    private final PersonService personService = new PersonService();

    @FXML
    public void initialize() {
        // Configurar permisos
        setupPermissions();

        // Configurar ComboBox de filtros
        cmbFilter.getItems().addAll("Todos", "Pendientes", "Vencidos");
        cmbFilter.setValue("Todos");
        cmbFilter.setOnAction(e -> {
            String selected = cmbFilter.getValue();
            currentFilter = switch (selected) {
                case "Pendientes" -> "pending";
                case "Vencidos" -> "overdue";
                default -> "all";
            };
            showingInvalidated = false;
            btnViewInvalidated.setText("Ver Invalidados");
            currentPage = 1;
            loadPageFromRepo(currentPage);
        });

        // Botón ver invalidados (solo si tiene permisos)
        if (btnViewInvalidated != null) {
            btnViewInvalidated.setVisible(canViewDeleted);
            btnViewInvalidated.setManaged(canViewDeleted);
            btnViewInvalidated.setOnAction(e -> {
                showingInvalidated = !showingInvalidated;
                btnViewInvalidated.setText(showingInvalidated ? "Ver Activos" : "Ver Invalidados");
                currentPage = 1;
                loadPageFromRepo(currentPage);
            });
        }

        colId.setCellValueFactory(data -> new javafx.beans.property.SimpleObjectProperty<>(data.getValue().getInteger("id")));

        // Columna de código de préstamo
        colLoanCode.setCellValueFactory(data -> {
            String loanCode = data.getValue().getString("loan_code");
            return new SimpleStringProperty(loanCode != null ? loanCode : "N/A");
        });

        colExpectedReturn.setCellValueFactory(data -> new SimpleStringProperty(
            data.getValue().getString("expected_return_date")));
        colLoanStatus.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getString("loan_status")));

        // Columna de estado del préstamo (pending, returned, etc)
        colLoanStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Transaction tx = getTableView().getItems().get(getIndex());
                    Label badge = createStatusBadge(tx);
                    setGraphic(badge);
                    setText(null);
                }
            }
        });

        // Columna de estado del registro (activo, inactivo o invalidado)
        colRecordStatus.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getString("status")));
        colRecordStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Transaction tx = getTableView().getItems().get(getIndex());
                    Label badge = TransactionStatusBadge.create(tx);
                    setGraphic(badge);
                    setText(null);
                }
            }
        });

        colQuantity.setCellValueFactory(data -> {
            List<TransactionItem> items = transactionService.getTransactionItems(data.getValue().getInteger("id"));
            if (items.isEmpty()) {
                return new SimpleStringProperty("0");
            }
            // Calcular suma total de cantidades
            double total = items.stream().mapToDouble(item -> item.getDouble("quantity")).sum();
            return new SimpleStringProperty(String.format("%.2f", total));
        });
        colDate.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getString("date")));

        colDni.setCellValueFactory(data -> {
            Integer personId = data.getValue().getInteger("person_id");
            if (personId != null) {
                Person person = personService.findById(personId);
                return new SimpleStringProperty(person != null ? person.getString("dni") : "N/A");
            }
            return new SimpleStringProperty("-");
        });
        colPerson.setCellValueFactory(data -> {
            Integer personId = data.getValue().getInteger("person_id");
            if (personId != null) {
                Person person = personService.findById(personId);
                return new SimpleStringProperty(person != null ? person.getFullName() : "N/A");
            }
            return new SimpleStringProperty("-");
        });

        // Cantidad de productos incluidos, independiente de la cantidad de unidades.
        colItems.setCellValueFactory(data -> {
            Integer count = data.getValue().getInteger("total_items");
            if (count == null) {
                count = transactionService.getTransactionItems(data.getValue().getInteger("id")).size();
            }
            return new SimpleStringProperty(count + (count == 1 ? " producto" : " productos"));
        });

        // Columna de acciones con botones según permisos y estado
        colActions.setSortable(false);
        colActions.setCellFactory(col -> new TableCell<>() {
            private final HBox container = new HBox(5);
            private final Button btnViewItems = new Button("Ver");
            private final Button btnInvalidate = new Button("Invalidar");
            private final Button btnRestore = new Button("Restaurar");
            private final Button btnPrintReceipt = new Button("Recibo");

            {
                btnViewItems.setStyle("-fx-font-size: 14; -fx-background-color: #9C27B0; -fx-text-fill: white; -fx-padding: 5 10;");
                btnInvalidate.setStyle("-fx-font-size: 14; -fx-background-color: #f44336; -fx-text-fill: white; -fx-padding: 5 10;");
                btnRestore.setStyle("-fx-font-size: 14; -fx-background-color: #4CAF50; -fx-text-fill: white; -fx-padding: 5 10;");
                btnPrintReceipt.setStyle("-fx-font-size: 14; -fx-background-color: #2196F3; -fx-text-fill: white; -fx-padding: 5 10;");
                btnPrintReceipt.setTooltip(new Tooltip("Emitir recibo del préstamo"));

                btnViewItems.setOnAction(e -> {
                    Transaction transaction = getTableView().getItems().get(getIndex());
                    showTransactionItemsDialog(transaction);
                });

                btnInvalidate.setOnAction(e -> {
                    Transaction transaction = getTableView().getItems().get(getIndex());
                    onInvalidateLoan(transaction);
                });

                btnRestore.setOnAction(e -> {
                    Transaction transaction = getTableView().getItems().get(getIndex());
                    onRestoreLoan(transaction);
                });

                btnPrintReceipt.setOnAction(e -> {
                    Transaction transaction = getTableView().getItems().get(getIndex());
                    com.joshuamc1.sicam.utils.PrintReceiptDialog.showPrintDialog(
                        transaction,
                        (Stage) getTableView().getScene().getWindow()
                    );
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    Transaction transaction = getTableView().getItems().get(getIndex());
                    boolean isActive = transaction.isActive();

                    container.getChildren().clear();
                    container.getChildren().add(btnViewItems);

                    // Botón de recibo siempre visible (se puede emitir tanto para activos como inactivos)
                    container.getChildren().add(btnPrintReceipt);

                    // Mostrar botón invalidar solo si está activo y tiene permiso
                    if (isActive && canInvalidate) {
                        container.getChildren().add(btnInvalidate);
                    }

                    // Mostrar botón restaurar solo si está inactivo y tiene permiso
                    if (!isActive && canRestore) {
                        container.getChildren().add(btnRestore);
                    }

                    setGraphic(container);
                }
            }
        });

        colId.setStyle("-fx-font-size: 17;");
        colLoanCode.setStyle("-fx-font-size: 17;");
        colLoanStatus.setStyle("-fx-font-size: 17;");
        colRecordStatus.setStyle("-fx-font-size: 17;");
        colQuantity.setStyle("-fx-font-size: 17;");
        colItems.setStyle("-fx-font-size: 17;");
        colDate.setStyle("-fx-font-size: 17;");
        colDni.setStyle("-fx-font-size: 17;");
        colPerson.setStyle("-fx-font-size: 17;");
        colActions.setStyle("-fx-font-size: 17;");
        colExpectedReturn.setStyle("-fx-font-size: 17;");

        // Inicializar paginación (similar a InventoryController)
        colQuantity.setComparator((left, right) -> Double.compare(
            Double.parseDouble(left.replace(',', '.')), Double.parseDouble(right.replace(',', '.'))));
        colItems.setComparator((left, right) -> Integer.compare(
            Integer.parseInt(left.split(" ")[0]), Integer.parseInt(right.split(" ")[0])));
        pageOrder = new com.joshuamc1.sicam.core.PagedTableSupport<>(tblTransactions, () -> loadPageFromRepo(1));
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

        // Botón refrescar
        if (btnRefresh != null) {
            btnRefresh.setOnAction(e -> loadPageFromRepo(currentPage));
        }

        // Botones de filtro
        if (btnAllLoans != null) {
            btnAllLoans.setOnAction(e -> {
                if ("Todos".equals(cmbFilter.getValue())) {
                    cmbFilter.fireEvent(new javafx.event.ActionEvent());
                } else {
                    cmbFilter.setValue("Todos");
                }
            });
        }

        if (btnPendingLoans != null) {
            btnPendingLoans.setOnAction(e -> {
                if ("Pendientes".equals(cmbFilter.getValue())) {
                    cmbFilter.fireEvent(new javafx.event.ActionEvent());
                } else {
                    cmbFilter.setValue("Pendientes");
                }
            });
        }

        if (btnOverdueLoans != null) {
            btnOverdueLoans.setOnAction(e -> {
                if ("Vencidos".equals(cmbFilter.getValue())) {
                    cmbFilter.fireEvent(new javafx.event.ActionEvent());
                } else {
                    cmbFilter.setValue("Vencidos");
                }
            });
        }

        if (btnViewInvalidated != null) {
            btnViewInvalidated.setVisible(com.joshuamc1.sicam.core.PermissionHelper.canAccess("SISTEMA", "VIEW_DELETED"));
            btnViewInvalidated.setManaged(com.joshuamc1.sicam.core.PermissionHelper.canAccess("SISTEMA", "VIEW_DELETED"));
            btnViewInvalidated.setOnAction(e -> {
                showingInvalidated = !showingInvalidated;
                btnViewInvalidated.setText(showingInvalidated ? "Ver Activos" : "Ver Invalidados");
                currentPage = 1;
                loadPageFromRepo(currentPage);
            });
        }

        // Búsqueda automática con debounce de 500ms
        if (txtSearch != null) {
            PauseTransition searchPause = new PauseTransition(Duration.millis(500));
            txtSearch.textProperty().addListener((obs, oldVal, newVal) -> {
                searchPause.setOnFinished(event -> {
                    currentPage = 1;
                    loadPageFromRepo(currentPage);
                });
                searchPause.playFromStart();
            });
        }

        loadPageFromRepo(1);

        configurePermissions();
    }

    private void configurePermissions() {
        if (btnNewLoan != null) {
            com.joshuamc1.sicam.core.PermissionHelper.hideIfNoPermission(btnNewLoan, "PRESTAMOS", "CREATE");
        }
    }

    @FXML
    public void onRefresh() {
        loadPageFromRepo(currentPage);
    }

    private void loadPageFromRepo(int page) {
        final long revision = ++loadRevision;
        final int requestedSize = pageSize;
        final java.util.Comparator<Transaction> order = pageOrder.comparator(java.util.Comparator.comparing(
            (Transaction row) -> row.getInteger("id")).reversed());
        final boolean customOrder = pageOrder.hasOrder();
        final int p = page;
        final String searchText = txtSearch != null ? txtSearch.getText() : "";
        final String filter = showingInvalidated ? "invalidated" : currentFilter;

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                try {
                    DatabaseConfig.openConnection();

                    long count;
                    List<Transaction> pageItems;
                    int offset = (Math.max(1, p) - 1) * requestedSize;

                    // Aplicar filtros según el estado actual
                    List<Transaction> allTransactions;
                    switch (filter) {
                        case "pending":
                            allTransactions = transactionService.findPendingLoans();
                            break;
                        case "overdue":
                            allTransactions = transactionService.findOverdueLoans();
                            break;
                        case "invalidated":
                            allTransactions = transactionService.findInvalidatedByType(Transaction.TYPE_LOAN);
                            break;
                        default: // "all"
                            allTransactions = transactionService.findByType(Transaction.TYPE_LOAN);
                            break;
                    }

                    // Aplicar búsqueda de texto si existe
                    if (searchText != null && !searchText.trim().isEmpty()) {
                        String search = searchText.trim().toLowerCase();
                        allTransactions = allTransactions.stream()
                            .filter(t -> {
                                // Buscar por ID de transacción
                                if (String.valueOf(t.getInteger("id")).contains(search)) return true;

                                // Buscar por código de préstamo
                                String loanCode = t.getString("loan_code");
                                if (loanCode != null && loanCode.toLowerCase().contains(search)) return true;

                                // Buscar por productos en los items
                                List<TransactionItem> items = transactionService.getTransactionItems(t.getInteger("id"));
                                for (TransactionItem item : items) {
                                    Product product = item.getProduct();
                                    if (product != null && product.getString("name").toLowerCase().contains(search)) {
                                        return true;
                                    }
                                }

                                // Buscar por persona
                                Integer personId = t.getInteger("person_id");
                                if (personId != null) {
                                    Person person = personService.findById(personId);
                                    if (person != null) {
                                        if (person.getString("dni").toLowerCase().contains(search)) return true;
                                        if (person.getFullName().toLowerCase().contains(search)) return true;
                                    }
                                }
                                return false;
                            })
                            .collect(java.util.stream.Collectors.toList());
                    }

                    allTransactions = new java.util.ArrayList<>(allTransactions);
                    allTransactions.sort(order);
                    count = allTransactions.size();
                    offset = (Math.min(Math.max(1, p), Math.max(1, (int) Math.ceil(allTransactions.size() / (double) requestedSize))) - 1) * requestedSize;
                    int fromIndex = Math.min(offset, allTransactions.size());
                    int toIndex = Math.min(offset + requestedSize, allTransactions.size());
                    pageItems = fromIndex < toIndex ? allTransactions.subList(fromIndex, toIndex) : List.of();

                    int total = (int) count;
                    int pages = Math.max(1, (int) Math.ceil(total / (double) requestedSize));
                    int pageToLoad = Math.min(Math.max(1, p), pages);

                    final List<Transaction> finalPageItems = pageItems == null ? List.of() : new java.util.ArrayList<>(pageItems);
                    Platform.runLater(() -> {
                        if (revision != loadRevision) return;
                        totalItems = total;
                        totalPages = pages;
                        currentPage = pageToLoad;
                        pageOrder.publish(currentPageItems, finalPageItems == null ? java.util.List.of() : finalPageItems);
                        updatePaginationControls();
                    });
                } catch (Throwable t) {
                    Logs.error(getClass(), "Error cargando página de PRESTAMOS: {}", t.getMessage());
                    Platform.runLater(() -> { if (revision == loadRevision) Utils.showAlert("Error", "No se pudo cargar PRESTAMOS: " + t.getMessage()); });
                } finally {
                    DatabaseConfig.closeConnection();
                }
                return null;
            }
        };

        new Thread(task, "loans-pager").start();
    }

    private void updatePaginationControls() {
        if (lblPage != null) lblPage.setText(String.format("Página %d/%d (%d)", currentPage, totalPages, totalItems));
        if (btnPrev != null) btnPrev.setDisable(currentPage <= 1);
        if (btnNext != null) btnNext.setDisable(currentPage >= totalPages);
    }


    @FXML
    public void onNew() {
        // Validar permiso específico del módulo PRESTAMOS antes de crear
        if (!com.joshuamc1.sicam.core.PermissionHelper.validateAndShow("PRESTAMOS", "CREATE")) {
            return;
        }

        Dialog<List<TransactionItemDTO>> dialog = createMultiProductDialog();
        dialog.showAndWait().ifPresent(items -> {
            try {
                // Obtener datos del diálogo almacenados en UserData
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> dialogData = (java.util.Map<String, Object>) dialog.getDialogPane().getUserData();

                Person selectedPerson = (Person) dialogData.get("person");
                LocalDate loanDate = (LocalDate) dialogData.get("date");
                LocalDate expectedReturnDate = (LocalDate) dialogData.get("expectedReturnDate");
                String notes = (String) dialogData.get("notes");

                // FASE 10: Usar createLoan que guarda el código en la misma transacción
                Transaction loan = transactionService.createLoan(
                        loanDate.toString(),
                        Session.getCurrentUserId(),
                        selectedPerson != null ? selectedPerson.getInteger("id") : null,
                        notes,
                        expectedReturnDate,
                        items
                );

                // Obtener el código generado para mostrarlo
                String loanCode = loan.getString("loan_code");
                Logs.info(getClass(), "Préstamo creado con código: {}", loanCode);

                loadPageFromRepo(currentPage);

                // Mostrar mensaje de éxito y preguntar si desea emitir recibo
                Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                successAlert.setTitle("Éxito");
                successAlert.setHeaderText("Préstamo registrado exitosamente");

                String message = "Código del préstamo: " + loanCode;
                if (expectedReturnDate != null) {
                    message += "\nFecha esperada de devolución: " + expectedReturnDate.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                }
                successAlert.setContentText(message);

                ButtonType emitReceiptButton = new ButtonType("Emitir Recibo");
                ButtonType closeButton = new ButtonType("Cerrar", ButtonBar.ButtonData.CANCEL_CLOSE);
                successAlert.getButtonTypes().setAll(emitReceiptButton, closeButton);

                Optional<ButtonType> result = successAlert.showAndWait();
                if (result.isPresent() && result.get() == emitReceiptButton) {
                    // Mostrar diálogo de impresión
                    com.joshuamc1.sicam.utils.PrintReceiptDialog.showPrintDialog(
                        loan,
                        (Stage) tblTransactions.getScene().getWindow()
                    );
                }
            } catch (Exception e) {
                Logs.error(getClass(), "Error creando préstamo multi-producto: {}", e.getMessage());
                e.printStackTrace();
                Utils.showAlert("Error", "No se pudo registrar el préstamo: " + e.getMessage());
            }
        });
    }

    private @NotNull Dialog<List<TransactionItemDTO>> createMultiProductDialog() {
        Dialog<List<TransactionItemDTO>> d = new Dialog<>();
        d.setResizable(true);
        d.getDialogPane().setPrefSize(1200, 700);
        d.setTitle("Nuevo Prestamo Multi-Producto");
        d.setHeaderText("Registrar prestamo de multiples productos");
        ButtonType save = new ButtonType("Guardar Prestamo", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        // ObservableList para los items que se van agregando
        ObservableList<TransactionItemDTO> items = FXCollections.observableArrayList();

        // === SECCIÓN 1: INFORMACIÓN GENERAL ===
        Label lblGeneral = new Label("Información General");
        lblGeneral.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #9C27B0;");

        DatePicker dpDate = new DatePicker(LocalDate.now());
        dpDate.setStyle("-fx-font-size: 17;");

        // ComboBox de persona con búsqueda
        var masterPeople = FXCollections.<Person>observableArrayList();
        var filteredPeople = new FilteredList<>(masterPeople, p -> true);
        ComboBox<Person> cmbPerson = new ComboBox<>(filteredPeople);
        cmbPerson.setPromptText("Seleccione la persona");
        cmbPerson.setStyle("-fx-font-size: 17;");
        cmbPerson.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Person it, boolean empty) {
                super.updateItem(it, empty);
                setText(empty || it == null ? null : it.getString("dni") + " - " + it.getFullName());
            }
        });
        cmbPerson.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Person it, boolean empty) {
                super.updateItem(it, empty);
                setText(empty || it == null ? null : it.getString("dni") + " - " + it.getFullName());
            }
        });

        TextField txtSearchPerson = new TextField();
        txtSearchPerson.setPromptText("Buscar persona por DNI o nombre...");
        txtSearchPerson.setStyle("-fx-font-size: 17;");

        PauseTransition personDebounce = new PauseTransition(Duration.millis(300));
        txtSearchPerson.textProperty().addListener((obs, old, val) -> {
            personDebounce.stop();
            personDebounce.setOnFinished(e -> {
                String query = val == null ? "" : val.trim();
                if (query.length() >= 2 || query.isEmpty()) {
                    new Thread(() -> {
                        List<Person> results = personService.searchPeople(query, 100);
                        Platform.runLater(() -> {
                            Person selected = cmbPerson.getValue();
                            masterPeople.setAll(results);
                            if (selected != null && results.contains(selected)) {
                                cmbPerson.setValue(selected);
                            }
                        });
                    }).start();
                }
            });
            personDebounce.playFromStart();
        });

        Button btnNewPerson = new Button("+");
        btnNewPerson.setStyle("-fx-font-size: 17; -fx-font-weight: bold; -fx-background-color: #4CAF50; -fx-text-fill: white; -fx-cursor: hand;");
        btnNewPerson.setTooltip(new Tooltip("Crear nueva persona"));
        btnNewPerson.setOnAction(e -> {
            if (!com.joshuamc1.sicam.core.PermissionHelper.validateAndShow("PERSONAS","CREATE")) return;
            Person newPerson = PersonDialogHelper.showCreatePersonDialog();
            if (newPerson != null) {
                masterPeople.add(newPerson);
                cmbPerson.getSelectionModel().select(newPerson);
            }
        });

        HBox personBox = new HBox(5, cmbPerson, btnNewPerson);
        com.joshuamc1.sicam.core.PermissionHelper.configureVisibleAction(btnNewPerson,
            com.joshuamc1.sicam.core.PermissionHelper.canAccess("PERSONAS","CREATE"));
        HBox.setHgrow(cmbPerson, Priority.ALWAYS);

        TextArea txtNotes = new TextArea();
        txtNotes.setPromptText("Notas generales del prestamo (opcional)");
        txtNotes.setPrefRowCount(2);
        txtNotes.setStyle("-fx-font-size: 17;");

        // FASE 10: Campo de fecha esperada de devolución
        DatePicker dpExpectedReturn = new DatePicker(LocalDate.now().plusDays(7)); // Por defecto 7 días
        dpExpectedReturn.setStyle("-fx-font-size: 17;");
        dpExpectedReturn.setPromptText("Fecha esperada de devolución");

        // === SECCIÓN 2: PRODUCTOS ===
        Label lblProducts = new Label("Productos");
        lblProducts.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #9C27B0;");

        // Tabla de items
        TableView<TransactionItemDTO> tblItems = new TableView<>(items);
        tblItems.setPrefHeight(200);

        TableColumn<TransactionItemDTO, String> colItemProduct = new TableColumn<>("Producto");
        colItemProduct.setPrefWidth(300);
        colItemProduct.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getProductName()));

        TableColumn<TransactionItemDTO, String> colItemQuantity = new TableColumn<>("Cantidad");
        colItemQuantity.setPrefWidth(100);
        colItemQuantity.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getQuantity())));

        TableColumn<TransactionItemDTO, String> colItemNotes = new TableColumn<>("Notas");
        colItemNotes.setPrefWidth(250);
        colItemNotes.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getNotes()));

        TableColumn<TransactionItemDTO, Void> colItemActions = new TableColumn<>("Acciones");
        colItemActions.setPrefWidth(100);
        colItemActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnRemove = new Button("Quitar");
            {
                btnRemove.setStyle("-fx-background-color: #f44336; -fx-text-fill: white;");
                btnRemove.setOnAction(e -> {
                    TransactionItemDTO item = getTableView().getItems().get(getIndex());
                    items.remove(item);
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btnRemove);
            }
        });

        colItemProduct.setStyle("-fx-font-size: 17;");
        colItemQuantity.setStyle("-fx-font-size: 17;");
        colItemNotes.setStyle("-fx-font-size: 17;");
        colItemActions.setStyle("-fx-font-size: 17;");

        tblItems.getColumns().addAll(colItemProduct, colItemQuantity, colItemNotes, colItemActions);

        // Botón para agregar producto
        Button btnAddProduct = new Button("+ Agregar Producto");
        btnAddProduct.setStyle("-fx-font-size: 14; -fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");
        btnAddProduct.setOnAction(e -> {
            showAddProductDialog().ifPresent(items::add);
        });

        // Layout
        GridPane gp = new GridPane();
        gp.setHgap(12);
        gp.setVgap(10);
        gp.setPadding(new Insets(15));

        String lblStyle = "-fx-font-weight: bold; -fx-font-size: 17;";
        Label lblDate = new Label("Fecha:");
        lblDate.setStyle(lblStyle);
        Label lblPerson = new Label("Persona:");
        lblPerson.setStyle(lblStyle);
        Label lblSearchPerson = new Label("Buscar:");
        lblSearchPerson.setStyle(lblStyle);
        Label lblExpectedReturn = new Label("Fecha Esperada Devolución:");
        lblExpectedReturn.setStyle(lblStyle);
        Label lblNotesGen = new Label("Notas:");
        lblNotesGen.setStyle(lblStyle);

        int row = 0;
        gp.add(lblGeneral, 0, row++, 4, 1);
        gp.add(lblDate, 0, row);
        gp.add(dpDate, 1, row++);
        gp.add(lblPerson, 0, row);
        gp.add(personBox, 1, row);
        gp.add(lblSearchPerson, 2, row);
        gp.add(txtSearchPerson, 3, row++);
        gp.add(lblExpectedReturn, 0, row);
        gp.add(dpExpectedReturn, 1, row++, 3, 1);
        gp.add(lblNotesGen, 0, row);
        gp.add(txtNotes, 1, row++, 3, 1);

        gp.add(new javafx.scene.control.Separator(), 0, row++, 4, 1);
        gp.add(lblProducts, 0, row++, 4, 1);
        gp.add(tblItems, 0, row++, 4, 1);
        gp.add(btnAddProduct, 0, row++, 4, 1);

        GridPane.setHgrow(dpDate, Priority.ALWAYS);
        GridPane.setHgrow(personBox, Priority.ALWAYS);
        GridPane.setHgrow(txtSearchPerson, Priority.ALWAYS);
        GridPane.setHgrow(dpExpectedReturn, Priority.ALWAYS);
        GridPane.setHgrow(txtNotes, Priority.ALWAYS);
        GridPane.setHgrow(tblItems, Priority.ALWAYS);
        GridPane.setHgrow(btnAddProduct, Priority.ALWAYS);

        ScrollPane scroll = new ScrollPane(gp);
        scroll.setFitToWidth(true);
        d.getDialogPane().setContent(scroll);

        // Cargar personas inicialmente
        new Thread(() -> {
            List<Person> people = personService.searchPeople("", 100);
            Platform.runLater(() -> masterPeople.setAll(people));
        }).start();

        // Deshabilitar guardar si no hay items
        Node btnSave = d.getDialogPane().lookupButton(save);
        btnSave.disableProperty().bind(javafx.beans.binding.Bindings.isEmpty(items));

        d.setResultConverter(bt -> {
            if (bt == save && !items.isEmpty()) {
                // FASE 10: Guardar todos los datos en UserData
                java.util.Map<String, Object> dialogData = new java.util.HashMap<>();
                dialogData.put("person", cmbPerson.getValue());
                dialogData.put("date", dpDate.getValue());
                dialogData.put("expectedReturnDate", dpExpectedReturn.getValue());
                dialogData.put("notes", txtNotes.getText());
                d.getDialogPane().setUserData(dialogData);

                return new java.util.ArrayList<>(items);
            }
            return null;
        });

        return d;
    }

    private java.util.Optional<TransactionItemDTO> showAddProductDialog() {
        Dialog<TransactionItemDTO> d = new Dialog<>();
        d.setTitle("Agregar Producto");
        d.setHeaderText("Seleccione el producto y la cantidad");
        ButtonType add = new ButtonType("Agregar", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(add, ButtonType.CANCEL);

        var masterProducts = FXCollections.<Product>observableArrayList();
        var filteredProducts = new FilteredList<>(masterProducts, p -> true);

        ComboBox<Product> cmbProduct = new ComboBox<>(filteredProducts);
        cmbProduct.setPromptText("Seleccione el producto");
        cmbProduct.setStyle("-fx-font-size: 17;");
        cmbProduct.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Product it, boolean empty) {
                super.updateItem(it, empty);
                setText(empty || it == null ? null : it.getInteger("id") + " - " + it.getString("name") + " (Stock: " + it.getDouble("current_stock") + ")");
            }
        });
        cmbProduct.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Product it, boolean empty) {
                super.updateItem(it, empty);
                setText(empty || it == null ? null : it.getInteger("id") + " - " + it.getString("name") + " (Stock: " + it.getDouble("current_stock") + ")");
            }
        });

        TextField txtSearchProduct = new TextField();
        txtSearchProduct.setPromptText("Buscar producto por nombre, ID o código (soporta escáner)...");
        txtSearchProduct.setStyle("-fx-font-size: 17;");

        // Prevenir que Enter active el botón predeterminado (excepto desde botones)
        d.getDialogPane().addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ENTER &&
                !(event.getTarget() instanceof javafx.scene.control.Button)) {
                event.consume();
            }
        });

        // Búsqueda automática mientras escribe (debounce)
        PauseTransition productDebounce = new PauseTransition(Duration.millis(300));
        txtSearchProduct.textProperty().addListener((obs, old, val) -> {
            productDebounce.stop();
            productDebounce.setOnFinished(e -> {
                String query = val == null ? "" : val.trim();
                if (query.length() >= 2 || query.isEmpty()) {
                    new Thread(() -> {
                        List<Product> results = productService.searchProducts(query, 100);
                        Platform.runLater(() -> {
                            Product selected = cmbProduct.getValue();
                            masterProducts.setAll(results);
                            if (selected != null && results.contains(selected)) {
                                cmbProduct.setValue(selected);
                            }
                        });
                    }).start();
                }
            });
            productDebounce.playFromStart();
        });

        TextField txtQuantity = new TextField();
        txtQuantity.setPromptText("Cantidad (máximo 2 decimales)");
        txtQuantity.setStyle("-fx-font-size: 17;");
        TextFormatter<String> quantityFormatter = new TextFormatter<>(change -> {
            String newText = change.getControlNewText();
            if (newText.isEmpty()) return change;
            if (newText.matches("\\d+(\\.\\d{0,4})?")) return change;
            return null;
        });
        txtQuantity.setTextFormatter(quantityFormatter);

        // Detectar Enter (lectores de códigos de barras envían Enter al final)
        // Usamos setOnAction en lugar de onKeyPressed para evitar propagación
        txtSearchProduct.setOnAction(event -> {
            String query = txtSearchProduct.getText().trim();
            if (!query.isEmpty()) {
                // Buscar primero por código exacto (para escáneres)
                new Thread(() -> {
                    Product exactMatch = productService.findByCode(query);
                    if (exactMatch != null) {
                        // Encontrado por código exacto
                        Platform.runLater(() -> {
                            masterProducts.setAll(List.of(exactMatch));
                            cmbProduct.setValue(exactMatch);
                            txtSearchProduct.clear();
                            txtQuantity.requestFocus(); // Mover foco a cantidad
                        });
                    } else {
                        // Buscar por nombre o ID
                        List<Product> results = productService.searchProducts(query, 100);
                        Platform.runLater(() -> {
                            masterProducts.setAll(results);
                            if (results.size() == 1) {
                                // Solo un resultado, seleccionarlo automáticamente
                                cmbProduct.setValue(results.get(0));
                                txtSearchProduct.clear();
                                txtQuantity.requestFocus();
                            } else if (!results.isEmpty()) {
                                // Múltiples resultados, abrir ComboBox para elegir
                                cmbProduct.show();
                            }
                        });
                    }
                }).start();
            }
        });

        TextArea txtNotes = new TextArea();
        txtNotes.setPromptText("Notas específicas del producto (opcional)");
        txtNotes.setPrefRowCount(2);
        txtNotes.setStyle("-fx-font-size: 17;");

        Label lblStockTitle = new Label("Stock Actual:");
        lblStockTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 17;");
        Label lblStockValue = new Label("-");
        lblStockValue.setStyle("-fx-font-size: 17;");

        cmbProduct.valueProperty().addListener((obs, old, val) -> {
            if (val == null) {
                lblStockValue.setText("-");
                lblStockValue.setStyle("-fx-font-size: 17;");
                return;
            }
            try {
                Double stock = val.getDouble("current_stock");
                lblStockValue.setText(stock == null ? "-" : String.format("%.2f", stock));
                if (val.isLowStock()) {
                    lblStockValue.setStyle("-fx-font-size: 17; -fx-text-fill: #d60000; -fx-font-weight: bold;");
                } else {
                    lblStockValue.setStyle("-fx-font-size: 17; -fx-text-fill: #1a1a1a;");
                }
            } catch (Exception ex) {
                lblStockValue.setText("-");
                lblStockValue.setStyle("-fx-font-size: 17;");
            }
        });

        GridPane gp = new GridPane();
        gp.setHgap(10);
        gp.setVgap(10);
        gp.setPadding(new Insets(15));

        String lblStyle = "-fx-font-weight: bold; -fx-font-size: 17;";
        Label lblProduct = new Label("Producto:");
        lblProduct.setStyle(lblStyle);
        Label lblSearch = new Label("Buscar:");
        lblSearch.setStyle(lblStyle);
        Label lblQuantity = new Label("Cantidad:");
        lblQuantity.setStyle(lblStyle);
        Label lblNotes = new Label("Notas:");
        lblNotes.setStyle(lblStyle);

        int row = 0;
        gp.add(lblProduct, 0, row);
        gp.add(cmbProduct, 1, row);
        gp.add(lblSearch, 2, row);
        gp.add(txtSearchProduct, 3, row++);
        gp.add(lblStockTitle, 0, row);
        gp.add(lblStockValue, 1, row++);
        gp.add(lblQuantity, 0, row);
        gp.add(txtQuantity, 1, row++);
        gp.add(lblNotes, 0, row);
        gp.add(txtNotes, 1, row++, 3, 1);

        GridPane.setHgrow(cmbProduct, Priority.ALWAYS);
        GridPane.setHgrow(txtSearchProduct, Priority.ALWAYS);
        GridPane.setHgrow(txtQuantity, Priority.ALWAYS);
        GridPane.setHgrow(txtNotes, Priority.ALWAYS);

        d.getDialogPane().setContent(gp);

        // Cargar productos inicialmente
        new Thread(() -> {
            List<Product> products = productService.searchProducts("", 100);
            Platform.runLater(() -> masterProducts.setAll(products));
        }).start();

        Node btnAdd = d.getDialogPane().lookupButton(add);
        BooleanBinding invalid = cmbProduct.valueProperty().isNull()
                .or(txtQuantity.textProperty().isEmpty());
        btnAdd.disableProperty().bind(invalid);

        d.setResultConverter(bt -> {
            if (bt == add) {
                try {
                    Product p = cmbProduct.getValue();
                    String qText = txtQuantity.getText();
                    if (p == null || qText == null || qText.isBlank()) {
                        return null;
                    }
                    double qty = com.joshuamc1.sicam.core.TransactionQuantity.parse(qText).doubleValue();
                    // Validar que la cantidad no exceda el stock disponible
                    Double currentStock = p.getDouble("current_stock");
                    if (currentStock == null) currentStock = 0.0;
                    if (qty > currentStock) {
                        Utils.showAlert("Stock Insuficiente",
                            String.format("No hay suficiente stock del producto '%s'.\n\nCantidad solicitada: %.2f\nStock disponible: %.2f\n\nPor favor, ajuste la cantidad.",
                                p.getString("name"), qty, currentStock));
                        return null;
                    }
                    return new TransactionItemDTO(
                            p.getInteger("id"),
                            qty,
                            txtNotes.getText(),
                            p.getString("name")
                    );
                } catch (IllegalArgumentException ex) {
                    Utils.showAlert("Validación", ex.getMessage());
                }
            }
            return null;
        });

        return d.showAndWait();
    }

    private void showTransactionItemsDialog(@NotNull Transaction transaction) {
        Dialog<Void> d = new Dialog<>();
        d.setTitle("Productos de la Transacción #" + transaction.getId());
        d.setHeaderText("Productos incluidos en esta transacción");
        d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        List<TransactionItem> transactionItems = transactionService.getTransactionItems(transaction.getInteger("id"));

        TableView<TransactionItem> tblItems = new TableView<>();
        ObservableList<TransactionItem> items = FXCollections.observableArrayList(transactionItems);
        tblItems.setItems(items);

        TableColumn<TransactionItem, String> colCode = new TableColumn<>("#Codigo");
        colCode.setPrefWidth(150);
        colCode.setCellValueFactory(data -> {
            Product product = data.getValue().getProduct();
            return new SimpleStringProperty(product != null ? product.getString("code") : "N/A");
        });

        TableColumn<TransactionItem, String> colProduct = new TableColumn<>("Producto");
        colProduct.setPrefWidth(300);
        colProduct.setCellValueFactory(data -> {
            Product product = data.getValue().getProduct();
            return new SimpleStringProperty(product != null ? product.getString("name") : "N/A");
        });

        TableColumn<TransactionItem, String> colQuantity = new TableColumn<>("Cantidad");
        colQuantity.setPrefWidth(100);
        colQuantity.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getDouble("quantity"))));

        TableColumn<TransactionItem, String> colNotes = new TableColumn<>("Notas");
        colNotes.setPrefWidth(300);
        colNotes.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getString("notes")));

        colCode.setStyle("-fx-font-size: 17;");
        colProduct.setStyle("-fx-font-size: 17;");
        colQuantity.setStyle("-fx-font-size: 17;");
        colNotes.setStyle("-fx-font-size: 17;");

        tblItems.getColumns().addAll(colCode, colProduct, colQuantity, colNotes);
        tblItems.setPrefHeight(400);

        VBox vbox = new VBox(10);
        String notes = transaction.getString("notes");
        if (notes != null && !notes.isBlank()) {
            Label notesLabel = new Label("Notas del préstamo");
            notesLabel.setStyle("-fx-font-weight: bold;");
            TextArea notesField = new TextArea(notes);
            notesField.setEditable(false);
            notesField.setWrapText(true);
            notesField.setPrefRowCount(3);
            vbox.getChildren().addAll(notesLabel, notesField);
        }
        vbox.getChildren().add(tblItems);
        VBox.setVgrow(tblItems, Priority.ALWAYS);
        vbox.setPadding(new Insets(15));
        d.getDialogPane().setContent(vbox);
        d.getDialogPane().setPrefSize(750, 500);

        d.showAndWait();
    }

    // ===== MÉTODOS PARA GESTIÓN DE PRÉSTAMOS (FASE 10) =====

    /**
     * Crea un badge visual según el estado del préstamo
     */
    private @NotNull Label createStatusBadge(@NotNull Transaction tx) {
        Label badge = new Label();
        badge.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");

        String loanStatus = tx.getString("loan_status");

        // La invalidación o inactividad tiene prioridad sobre el estado del préstamo.
        if (!tx.isActive()) {
            return TransactionStatusBadge.create(tx);
        }

        // Estados de préstamo
        if ("returned".equals(loanStatus)) {
            badge.setText("DEVUELTO");
            badge.setStyle(badge.getStyle() + "-fx-background-color: #28a745; -fx-text-fill: white; -fx-padding: 5 10; -fx-background-radius: 5;");
        } else if ("paid".equals(loanStatus)) {
            badge.setText("PAGADO");
            badge.setStyle(badge.getStyle() + "-fx-background-color: #17a2b8; -fx-text-fill: white; -fx-padding: 5 10; -fx-background-radius: 5;");
        } else if ("replaced".equals(loanStatus)) {
            badge.setText("REEMPLAZADO");
            badge.setStyle(badge.getStyle() + "-fx-background-color: #6c757d; -fx-text-fill: white; -fx-padding: 5 10; -fx-background-radius: 5;");
        } else if ("overdue".equals(loanStatus)) {
            // Estado vencido (actualizado por evento programado)
            badge.setText("VENCIDO");
            badge.setStyle(badge.getStyle() + "-fx-background-color: #dc3545; -fx-text-fill: white; -fx-padding: 5 10; -fx-background-radius: 5;");
        } else if ("pending".equals(loanStatus)) {
            // Verificar si está vencido (para el caso en que el evento aún no se haya ejecutado)
            if (tx.isLoanOverdue()) {
                badge.setText("VENCIDO");
                badge.setStyle(badge.getStyle() + "-fx-background-color: #dc3545; -fx-text-fill: white; -fx-padding: 5 10; -fx-background-radius: 5;");
            } else {
                java.sql.Date due = tx.getDate("expected_return_date");
                boolean dueToday = due != null && due.toLocalDate().equals(LocalDate.now());
                badge.setText(dueToday ? "VENCE HOY" : "PENDIENTE");
                badge.setStyle(badge.getStyle() + "-fx-background-color: "
                    + (dueToday ? "#F9A825" : "#28a745") + "; -fx-text-fill: "
                    + (dueToday ? "#212121" : "white") + "; -fx-padding: 5 10; -fx-background-radius: 5;");
            }
        } else {
            badge.setText("ACTIVO");
            badge.setStyle(badge.getStyle() + "-fx-background-color: #28a745; -fx-text-fill: white; -fx-padding: 5 10; -fx-background-radius: 5;");
        }

        return badge;
    }

    /**
     * Configurar permisos del usuario actual
     */
    private void setupPermissions() {
        // Verificar permiso de invalidar (DELETE en préstamos)
        canInvalidate = com.joshuamc1.sicam.core.PermissionHelper.canAccess("PRESTAMOS", "DELETE");

        // Verificar permiso de restaurar
        canRestore = com.joshuamc1.sicam.core.PermissionHelper.canAccess("PRESTAMOS", "RESTORE");

        // Verificar permiso de ver eliminados
        canViewDeleted = com.joshuamc1.sicam.core.PermissionHelper.canAccess("SISTEMA", "VIEW_DELETED");

        Logs.info(getClass(), "Permisos configurados - Invalidar: {}, Restaurar: {}, Ver eliminados: {}",
            canInvalidate, canRestore, canViewDeleted);
    }

    /**
     * Invalidar un préstamo
     */
    private void onInvalidateLoan(Transaction transaction) {
        if (!canInvalidate) {
            Utils.showAlert("Acceso Denegado", "No tiene permiso para invalidar préstamos");
            return;
        }

        if (!transaction.isActive()) {
            Utils.showAlert("Error", "Este préstamo ya está invalidado");
            return;
        }

        // Confirmar invalidación
        boolean confirmed = Utils.showConfirmation(
            "Confirmar Invalidación",
            "¿Está seguro de invalidar este préstamo?\n\n" +
            "Código: " + transaction.getString("loan_code") + "\n" +
            "Esta acción revertirá el stock de los productos prestados."
        );

        if (!confirmed) {
            return;
        }

        // Solicitar razón
        javafx.scene.control.TextInputDialog reasonDialog = new javafx.scene.control.TextInputDialog();
        reasonDialog.setTitle("Razón de Invalidación");
        reasonDialog.setHeaderText("Ingrese la razón por la cual invalida este préstamo:");
        reasonDialog.setContentText("Razón:");

        java.util.Optional<String> reasonResult = reasonDialog.showAndWait();
        if (reasonResult.isEmpty() || reasonResult.get().trim().isEmpty()) {
            Utils.showAlert("Cancelado", "Debe proporcionar una razón para invalidar");
            return;
        }

        try {
            transactionService.invalidate(transaction, reasonResult.get());
            Utils.showAlert("Éxito", "Préstamo invalidado correctamente");
            loadPageFromRepo(currentPage);
        } catch (Exception e) {
            Logs.error(getClass(), "Error invalidando préstamo: {}", e.getMessage());
            Utils.showAlert("Error", "No se pudo invalidar el préstamo: " + e.getMessage());
        }
    }

    /**
     * Restaurar un préstamo invalidado
     */
    private void onRestoreLoan(Transaction transaction) {
        if (!canRestore) {
            Utils.showAlert("Acceso Denegado", "No tiene permiso para restaurar préstamos");
            return;
        }

        if (transaction.isActive()) {
            Utils.showAlert("Error", "Este préstamo ya está activo");
            return;
        }

        // Confirmar restauración
        boolean confirmed = Utils.showConfirmation(
            "Confirmar Restauración",
            "¿Está seguro de restaurar este préstamo?\n\n" +
            "Código: " + transaction.getString("loan_code") + "\n" +
            "Esta acción volverá a restar el stock de los productos."
        );

        if (!confirmed) {
            return;
        }

        try {
            transactionService.restore(transaction);
            Utils.showAlert("Éxito", "Préstamo restaurado correctamente");
            loadPageFromRepo(currentPage);
        } catch (Exception e) {
            Logs.error(getClass(), "Error restaurando préstamo: {}", e.getMessage());
            Utils.showAlert("Error", "No se pudo restaurar el préstamo: " + e.getMessage());
        }
    }
}
