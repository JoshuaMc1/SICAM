package com.joshuamc1.sicam.controllers.dashboard;

import com.joshuamc1.sicam.config.DatabaseConfig;
import com.joshuamc1.sicam.core.Logs;
import com.joshuamc1.sicam.core.PermissionHelper;
import com.joshuamc1.sicam.core.dialog.PersonDialogHelper;
import com.joshuamc1.sicam.core.Session;
import com.joshuamc1.sicam.core.Utils;
import com.joshuamc1.sicam.models.Person;
import com.joshuamc1.sicam.models.Product;
import com.joshuamc1.sicam.models.Transaction;
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
import javafx.util.Duration;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;
import java.util.List;

@SuppressWarnings("ALL")
public class IncomesController {
    @FXML
    private TableView<Transaction> tblTransactions;
    @FXML private Button btnNewIncome;
    @FXML private Button btnRefresh;
    @FXML private Button btnViewInvalidated;
    @FXML private TextField txtSearch;
    @FXML
    private TableColumn<Transaction, String> colId;
    @FXML
    private TableColumn<Transaction, String> colStatus;
    @FXML
    private TableColumn<Transaction, String> colProduct;
    @FXML
    private TableColumn<Transaction, String> colQuantity;
    @FXML
    private TableColumn<Transaction, String> colItems;
    @FXML
    private TableColumn<Transaction, String> colDate;
    @FXML
    private TableColumn<Transaction, String> colDni;
    @FXML
    private TableColumn<Transaction, String> colPerson;
    @FXML
    private TableColumn<Transaction, String> colNotes;
    @FXML
    private TableColumn<Transaction, Void> colActions;

    // Controles de paginación
    @FXML private Button btnPrev;
    @FXML private Button btnNext;
    @FXML private Label lblPage;
    @FXML private ChoiceBox<Integer> choicePageSize;

    // Estado de paginación
    private int currentPage = 1;
    private int pageSize = 25;
    private int totalItems = 0;
    private int totalPages = 1;
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

        colId.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getInteger("id"))));

        // Columna de estado
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Transaction tx = getTableView().getItems().get(getIndex());
                    boolean isActive = tx.isActive();
                    Label badge = new Label(isActive ? "Activo" : "Inactivo");
                    badge.setStyle(isActive
                        ? "-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-padding: 5 10; -fx-background-radius: 5;"
                        : "-fx-background-color: #9E9E9E; -fx-text-fill: white; -fx-padding: 5 10; -fx-background-radius: 5;");
                    setGraphic(badge);
                    setText(null);
                }
            }
        });

        colProduct.setCellValueFactory(data -> {
            // ...existing code...
            List<TransactionItem> items = transactionService.getTransactionItems(data.getValue().getInteger("id"));
            if (items.isEmpty()) {
                return new SimpleStringProperty("Sin productos");
            }
            if (items.size() == 1) {
                Product product = items.get(0).getProduct();
                return new SimpleStringProperty(product != null ? product.getString("name") : "N/A");
            }
            return new SimpleStringProperty("Multi-Producto (" + items.size() + ")");
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
        colNotes.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getString("notes")));

        // Columna de items
        colItems.setCellValueFactory(data -> {
            Integer totalItems = data.getValue().getInteger("total_items");
            if (totalItems != null && totalItems > 1) {
                return new SimpleStringProperty(totalItems + " items");
            }
            return new SimpleStringProperty("1 item");
        });

        // Columna de acciones con botones según permisos y estado
        colActions.setCellFactory(col -> new TableCell<>() {
            private final HBox container = new HBox(5);
            private final Button btnViewItems = new Button("Ver");
            private final Button btnInvalidate = new Button("Invalidar");
            private final Button btnRestore = new Button("Restaurar");

            {
                btnViewItems.setStyle("-fx-font-size: 14; -fx-background-color: #2196F3; -fx-text-fill: white; -fx-padding: 5 10;");
                btnInvalidate.setStyle("-fx-font-size: 14; -fx-background-color: #f44336; -fx-text-fill: white; -fx-padding: 5 10;");
                btnRestore.setStyle("-fx-font-size: 14; -fx-background-color: #4CAF50; -fx-text-fill: white; -fx-padding: 5 10;");

                btnViewItems.setOnAction(e -> {
                    Transaction transaction = getTableView().getItems().get(getIndex());
                    showTransactionItemsDialog(transaction);
                });

                btnInvalidate.setOnAction(e -> {
                    Transaction transaction = getTableView().getItems().get(getIndex());
                    onInvalidateIncome(transaction);
                });

                btnRestore.setOnAction(e -> {
                    Transaction transaction = getTableView().getItems().get(getIndex());
                    onRestoreIncome(transaction);
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

                    // Mostrar botón invalidar solo si está activo y tiene permiso
                    if (isActive && canInvalidate) {
                        container.getChildren().add(btnInvalidate);
                    }

                    // Mostrar botón restaurar solo si está inactivo y tiene permiso
                    if (!isActive && canRestore) {
                        container.getChildren().add(btnRestore);
                    }

                    setGraphic(container);
                    setGraphic(btnViewItems);
                }
            }
        });

        colId.setStyle("-fx-font-size: 17;");
        colStatus.setStyle("-fx-font-size: 17;");
        colProduct.setStyle("-fx-font-size: 17;");
        colQuantity.setStyle("-fx-font-size: 17;");
        colItems.setStyle("-fx-font-size: 17;");
        colDate.setStyle("-fx-font-size: 17;");
        colDni.setStyle("-fx-font-size: 17;");
        colPerson.setStyle("-fx-font-size: 17;");
        colNotes.setStyle("-fx-font-size: 17;");
        colActions.setStyle("-fx-font-size: 17;");

        // Inicializar paginación (similar a InventoryController)
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
        if (btnNewIncome != null) {
            PermissionHelper.hideIfNoPermission(btnNewIncome, "INGRESOS", "CREATE");
        }
    }


    private void loadPageFromRepo(int page) {
        final int p = page;
        final String searchText = txtSearch != null ? txtSearch.getText() : "";

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                try {
                    DatabaseConfig.openConnection();

                    long count;
                    List<Transaction> pageItems;
                    int offset = (Math.max(1, p) - 1) * pageSize;

                    if (searchText == null || searchText.trim().isEmpty()) {
                        count = transactionService.countByType(Transaction.TYPE_IN);
                        pageItems = transactionService.findByTypePage(Transaction.TYPE_IN, pageSize, offset);
                    } else {
                        // Búsqueda filtrada
                        String search = searchText.trim().toLowerCase();
                        List<Transaction> allTransactions = transactionService.findByType(Transaction.TYPE_IN);
                        List<Transaction> filtered = allTransactions.stream()
                            .filter(t -> {
                                // Buscar por ID de transacción
                                if (String.valueOf(t.getInteger("id")).contains(search)) return true;

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
                        count = filtered.size();
                        int fromIndex = Math.min(offset, filtered.size());
                        int toIndex = Math.min(offset + pageSize, filtered.size());
                        pageItems = fromIndex < toIndex ? filtered.subList(fromIndex, toIndex) : List.of();
                    }

                    int total = (int) count;
                    int pages = Math.max(1, (int) Math.ceil(total / (double) pageSize));
                    int pageToLoad = Math.min(Math.max(1, p), pages);

                    final List<Transaction> finalPageItems = pageItems;
                    Platform.runLater(() -> {
                        totalItems = total;
                        totalPages = pages;
                        currentPage = pageToLoad;
                        currentPageItems.setAll(finalPageItems == null ? java.util.List.of() : finalPageItems);
                        tblTransactions.setItems(currentPageItems);
                        updatePaginationControls();
                    });
                } catch (Throwable t) {
                    Logs.error(getClass(), "Error cargando página de ingresos: {}", t.getMessage());
                    Platform.runLater(() -> Utils.showAlert("Error", "No se pudo cargar ingresos: " + t.getMessage()));
                } finally {
                    DatabaseConfig.closeConnection();
                }
                return null;
            }
        };

        new Thread(task, "incomes-pager").start();
    }

    private void updatePaginationControls() {
        if (lblPage != null) lblPage.setText(String.format("Página %d/%d (%d)", currentPage, totalPages, totalItems));
        if (btnPrev != null) btnPrev.setDisable(currentPage <= 1);
        if (btnNext != null) btnNext.setDisable(currentPage >= totalPages);
    }


    @FXML
    public void onNewMultiProduct() {
        // Validar permiso específico del módulo INGRESOS antes de crear
        if (!PermissionHelper.validateAndShow("INGRESOS", "CREATE")) {
            return;
        }

        Dialog<List<TransactionItemDTO>> dialog = createMultiProductDialog();
        dialog.showAndWait().ifPresent(items -> {
            try {
                // FASE 10: Obtener todos los datos del diálogo
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> dialogData = (java.util.Map<String, Object>)
                    dialog.getDialogPane().getUserData();

                Person selectedPerson = (Person) dialogData.get("person");
                LocalDate date = (LocalDate) dialogData.getOrDefault("date", LocalDate.now());
                String notes = (String) dialogData.getOrDefault("notes", "");
                boolean isReturn = (Boolean) dialogData.getOrDefault("isReturn", false);

                if (isReturn) {
                    String loanCode = (String) dialogData.get("loanCode");
                    String returnTypeDisplay = (String) dialogData.get("returnType");
                    String returnNotes = (String) dialogData.getOrDefault("returnNotes", "");

                    // Validar código de préstamo
                    if (loanCode == null || loanCode.trim().isEmpty()) {
                        Utils.showAlert("Error", "Debe ingresar el código del préstamo");
                        return;
                    }

                    // Buscar préstamo
                    Transaction loan = transactionService.findByLoanCode(loanCode.trim());
                    if (loan == null) {
                        Utils.showAlert("Error", "Préstamo no encontrado con código: " + loanCode);
                        return;
                    }

                    // Validar estado del préstamo
                    String loanStatus = loan.getString("loan_status");
                    if (!"pending".equals(loanStatus)) {
                        String statusMsg = switch (loanStatus) {
                            case "returned" -> "ya fue devuelto";
                            case "paid" -> "ya fue pagado";
                            case "replaced" -> "ya fue reemplazado";
                            default -> "no está disponible para devolución";
                        };
                        Utils.showAlert("Error", "El préstamo " + statusMsg);
                        return;
                    }

                    // Convertir tipo de devolución
                    String returnType = mapReturnType(returnTypeDisplay);

                    // Crear devolución
                    transactionService.createLoanReturn(
                        loan.getInteger("id"),
                        returnType,
                        returnNotes,
                        items
                    );

                    loadPageFromRepo(currentPage);
                    Utils.showAlert("Éxito",
                        "Devolución registrada exitosamente\n\n" +
                        "Préstamo: " + loanCode + "\n" +
                        "Tipo: " + returnTypeDisplay + "\n" +
                        "Estado: Devuelto");

                } else {
                    // INGRESO NORMAL
                    transactionService.createMultiProductTransaction(
                        Transaction.TYPE_IN,
                        date.toString(),
                        Session.getCurrentUserId(),
                        selectedPerson != null ? selectedPerson.getInteger("id") : null,
                        notes,
                        items
                    );

                    loadPageFromRepo(currentPage);
                    Utils.showAlert("Éxito", "Ingreso multi-producto registrado exitosamente con " + items.size() + " productos");
                }

            } catch (Exception e) {
                Logs.error(getClass(), "Error: {}", e.getMessage());
                e.printStackTrace();
                Utils.showAlert("Error", "No se pudo registrar: " + e.getMessage());
            }
        });
    }

    /**
     * FASE 10: Convierte el tipo de devolución de display a valor de BD
     */
    @Contract(pure = true)
    private @NotNull String mapReturnType(@NotNull String displayType) {
        return switch (displayType) {
            case "Mismo Producto (Devuelto)" -> "same_product";
            case "Comprado/Pagado" -> "purchased";
            case "Producto Reemplazado" -> "replaced";
            default -> "other";
        };
    }

    private @NotNull Dialog<List<TransactionItemDTO>> createMultiProductDialog() {
        Dialog<List<TransactionItemDTO>> d = new Dialog<>();
        d.setResizable(true);
        d.getDialogPane().setPrefSize(1200, 700);
        d.setTitle("Nuevo Ingreso Multi-Producto");
        d.setHeaderText("Registrar entrada de múltiples productos al inventario");
        ButtonType save = new ButtonType("Guardar Ingreso", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        // ObservableList para los items que se van agregando
        ObservableList<TransactionItemDTO> items = FXCollections.observableArrayList();

        // === SECCIÓN 1: INFORMACIÓN GENERAL ===
        Label lblGeneral = new Label("Información General");
        lblGeneral.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #2196F3;");

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
            Person newPerson = PersonDialogHelper.showCreatePersonDialog();
            if (newPerson != null) {
                masterPeople.add(newPerson);
                cmbPerson.getSelectionModel().select(newPerson);
            }
        });

        HBox personBox = new HBox(5, cmbPerson, btnNewPerson);
        HBox.setHgrow(cmbPerson, Priority.ALWAYS);

        TextArea txtNotes = new TextArea();
        txtNotes.setPromptText("Notas generales del ingreso (opcional)");
        txtNotes.setPrefRowCount(2);
        txtNotes.setStyle("-fx-font-size: 17;");

        // FASE 10: Campos para devolución de préstamo
        CheckBox chkIsReturn = new CheckBox("Este ingreso es una devolución de préstamo");
        chkIsReturn.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #FF5722;");

        // ComboBox editable con autocompletado para códigos de préstamo
        ComboBox<String> cmbLoanCode = new ComboBox<>();
        cmbLoanCode.setEditable(true);
        cmbLoanCode.setPromptText("Escribe el código del préstamo...");
        cmbLoanCode.setMaxWidth(Double.MAX_VALUE);
        cmbLoanCode.setStyle("-fx-font-size: 17;");
        cmbLoanCode.visibleProperty().bind(chkIsReturn.selectedProperty());
        cmbLoanCode.managedProperty().bind(chkIsReturn.selectedProperty());

        // Label para información del préstamo
        Label lblLoanInfo = new Label();
        lblLoanInfo.setStyle("-fx-font-size: 14px; -fx-padding: 5; -fx-background-color: #f5f5f5; -fx-border-color: #ddd; -fx-border-radius: 5; -fx-background-radius: 5;");
        lblLoanInfo.setWrapText(true);
        lblLoanInfo.setMaxWidth(Double.MAX_VALUE);
        lblLoanInfo.visibleProperty().bind(chkIsReturn.selectedProperty());
        lblLoanInfo.managedProperty().bind(chkIsReturn.selectedProperty());

        // Variable para almacenar el préstamo encontrado
        final Transaction[] foundLoan = {null};

        // Bandera para evitar que el listener limpie cuando se selecciona del dropdown
        final boolean[] isSelecting = {false};

        // Búsqueda con autocompletado (debounce de 500ms)
        PauseTransition loanSearchDebounce = new PauseTransition(Duration.millis(500));

        cmbLoanCode.getEditor().textProperty().addListener((obs, oldVal, newVal) -> {
            // Si está en proceso de selección, no hacer nada
            if (isSelecting[0]) {
                return;
            }

            loanSearchDebounce.stop();

            // Limpiar info si está vacío
            if (newVal == null || newVal.trim().isEmpty()) {
                cmbLoanCode.getItems().clear();
                cmbLoanCode.hide();
                lblLoanInfo.setText("");
                lblLoanInfo.setStyle("-fx-font-size: 14px; -fx-padding: 5; -fx-background-color: #f5f5f5; -fx-border-color: #ddd; -fx-border-radius: 5; -fx-background-radius: 5;");
                foundLoan[0] = null;
                return;
            }

            // Buscar préstamos mientras escribe (con debounce)
            loanSearchDebounce.setOnFinished(e -> {
                String searchText = newVal.trim();

                // Buscar solo si hay al menos 2 caracteres
                if (searchText.length() < 2) {
                    return;
                }

                new Thread(() -> {
                    try {
                        // Buscar préstamos pendientes que coincidan
                        List<Transaction> matchingLoans = transactionService.searchPendingLoansByCode(searchText);

                        Platform.runLater(() -> {
                            // No limpiar si el texto coincide exactamente con un item existente
                            // (esto significa que el usuario acaba de seleccionar del dropdown)
                            if (cmbLoanCode.getItems().contains(searchText)) {
                                return;
                            }

                            cmbLoanCode.getItems().clear();

                            if (matchingLoans != null && !matchingLoans.isEmpty()) {
                                // Agregar códigos al ComboBox
                                for (Transaction loan : matchingLoans) {
                                    String code = loan.getString("loan_code");
                                    if (code != null) {
                                        cmbLoanCode.getItems().add(code);
                                    }
                                }

                                // Mostrar dropdown solo si hay resultados
                                if (!cmbLoanCode.getItems().isEmpty()) {
                                    cmbLoanCode.show();
                                }
                            }
                        });
                    } catch (Exception ex) {
                        Logs.error(IncomesController.class,"Error buscando préstamos: " + ex.getMessage(), ex);
                    }
                }).start();
            });

            loanSearchDebounce.playFromStart();
        });

        // Cuando selecciona un código del dropdown, cargar información completa
        cmbLoanCode.setOnAction(e -> {
            String selectedCode = cmbLoanCode.getValue();
            if (selectedCode == null || selectedCode.trim().isEmpty()) {
                return;
            }

            // Activar bandera para evitar que el listener interfiera
            isSelecting[0] = true;

            new Thread(() -> {
                try {
                    Transaction loan = transactionService.findByLoanCode(selectedCode.trim());
                    Platform.runLater(() -> {
                        try {
                            if (loan == null) {
                                lblLoanInfo.setText("❌ Préstamo no encontrado");
                                lblLoanInfo.setStyle("-fx-font-size: 14px; -fx-padding: 5; -fx-text-fill: #f44336; -fx-background-color: #ffebee; -fx-border-color: #f44336; -fx-border-radius: 5; -fx-background-radius: 5;");
                                foundLoan[0] = null;
                            } else {
                                String status = loan.getString("loan_status");
                                if (!"pending".equals(status)) {
                                    String statusMsg = switch (status) {
                                        case "returned" -> "ya fue devuelto";
                                        case "paid" -> "ya fue pagado";
                                        case "replaced" -> "ya fue reemplazado";
                                        default -> "no está disponible";
                                    };
                                    lblLoanInfo.setText("⚠️ Este préstamo " + statusMsg);
                                    lblLoanInfo.setStyle("-fx-font-size: 14px; -fx-padding: 5; -fx-text-fill: #ff9800; -fx-background-color: #fff3e0; -fx-border-color: #ff9800; -fx-border-radius: 5; -fx-background-radius: 5;");
                                    foundLoan[0] = null;
                            } else {
                                // Préstamo válido encontrado
                                Person person = loan.parent(Person.class);
                                String personName = person != null ? person.getFullName() : "N/A";
                                String expectedDate = loan.getString("expected_return_date");
                                int totalItems = loan.getInteger("total_items") != null ? loan.getInteger("total_items") : 0;

                                lblLoanInfo.setText(String.format(
                                    "✅ Préstamo Encontrado - Productos cargados automáticamente\n" +
                                    "Persona: %s\n" +
                                    "Fecha: %s | Fecha Esperada Devolución: %s\n" +
                                    "Productos: %d items",
                                    personName,
                                    loan.getString("date"),
                                    expectedDate != null ? expectedDate : "No especificada",
                                    totalItems
                                ));
                                lblLoanInfo.setStyle("-fx-font-size: 14px; -fx-padding: 5; -fx-text-fill: #4CAF50; -fx-background-color: #e8f5e9; -fx-border-color: #4CAF50; -fx-border-radius: 5; -fx-background-radius: 5;");
                                foundLoan[0] = loan;

                                // AUTOCOMPLETAR: Seleccionar la persona del préstamo
                                if (person != null) {
                                    Integer personId = person.getInteger("id");

                                    // Buscar la persona en la lista masterPeople por ID
                                    Person personInList = masterPeople.stream()
                                        .filter(p -> p.getInteger("id").equals(personId))
                                        .findFirst()
                                        .orElse(null);

                                    if (personInList != null) {
                                        // Si la persona ya está en la lista, usarla
                                        cmbPerson.setValue(personInList);
                                    } else {
                                        // Si no está, agregarla y seleccionarla
                                        masterPeople.add(person);
                                        cmbPerson.setValue(person);
                                    }
                                }

                                // AUTOCOMPLETAR: Cargar los productos del préstamo automáticamente
                                items.clear(); // Limpiar items actuales
                                List<TransactionItem> loanItems = loan.getAll(TransactionItem.class);

                                for (TransactionItem loanItem : loanItems) {
                                    Product product = loanItem.parent(Product.class);
                                    if (product != null) {
                                        double quantity = loanItem.getDouble("quantity");
                                        String itemNotes = loanItem.getString("notes");

                                        // Crear DTO con los datos del préstamo usando el constructor correcto
                                        TransactionItemDTO dto = new TransactionItemDTO(
                                            product.getInteger("id"),
                                            quantity,
                                            itemNotes != null ? "Devolución: " + itemNotes : "Devolución de préstamo",
                                            product.getString("name")
                                        );

                                        items.add(dto);
                                    }
                                }
                            }
                            }
                        } finally {
                            // Desactivar bandera después de procesar
                            isSelecting[0] = false;
                        }
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        try {
                            lblLoanInfo.setText("❌ Error al buscar préstamo");
                            lblLoanInfo.setStyle("-fx-font-size: 14px; -fx-padding: 5; -fx-text-fill: #f44336; -fx-background-color: #ffebee; -fx-border-color: #f44336; -fx-border-radius: 5; -fx-background-radius: 5;");
                            foundLoan[0] = null;
                        } finally {
                            // Desactivar bandera en caso de error
                            isSelecting[0] = false;
                        }
                    });
                }
            }).start();
        });

        ComboBox<String> cmbReturnType = new ComboBox<>();
        cmbReturnType.getItems().addAll(
            "Mismo Producto (Devuelto)",
            "Comprado/Pagado",
            "Producto Reemplazado",
            "Otro"
        );
        cmbReturnType.setValue("Mismo Producto (Devuelto)");
        cmbReturnType.setStyle("-fx-font-size: 17;");
        cmbReturnType.visibleProperty().bind(chkIsReturn.selectedProperty());
        cmbReturnType.managedProperty().bind(chkIsReturn.selectedProperty());

        TextArea txtReturnNotes = new TextArea();
        txtReturnNotes.setPromptText("Notas sobre la devolución (opcional)");
        txtReturnNotes.setPrefRowCount(2);
        txtReturnNotes.setStyle("-fx-font-size: 17;");
        txtReturnNotes.visibleProperty().bind(chkIsReturn.selectedProperty());
        txtReturnNotes.managedProperty().bind(chkIsReturn.selectedProperty());


        // === SECCIÓN 2: PRODUCTOS ===
        Label lblProducts = new Label("Productos");
        lblProducts.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #2196F3;");

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

        tblItems.getColumns().addAll(colItemProduct, colItemQuantity, colItemNotes, colItemActions);

        colItemProduct.setStyle("-fx-font-size: 17;");
        colItemQuantity.setStyle("-fx-font-size: 17;");
        colItemNotes.setStyle("-fx-font-size: 17;");
        colItemActions.setStyle("-fx-font-size: 17;");

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
        Label lblNotesGen = new Label("Notas:");
        lblNotesGen.setStyle(lblStyle);

        // FASE 10: Labels para devolución
        Label lblLoanCode = new Label("Código Préstamo:");
        lblLoanCode.setStyle(lblStyle);
        lblLoanCode.visibleProperty().bind(chkIsReturn.selectedProperty());
        lblLoanCode.managedProperty().bind(chkIsReturn.selectedProperty());

        Label lblReturnType = new Label("Tipo Devolución:");
        lblReturnType.setStyle(lblStyle);
        lblReturnType.visibleProperty().bind(chkIsReturn.selectedProperty());
        lblReturnType.managedProperty().bind(chkIsReturn.selectedProperty());

        Label lblReturnNotes = new Label("Notas Devolución:");
        lblReturnNotes.setStyle(lblStyle);
        lblReturnNotes.visibleProperty().bind(chkIsReturn.selectedProperty());
        lblReturnNotes.managedProperty().bind(chkIsReturn.selectedProperty());

        int row = 0;
        gp.add(lblGeneral, 0, row++, 4, 1);
        gp.add(lblDate, 0, row);
        gp.add(dpDate, 1, row++);
        gp.add(lblPerson, 0, row);
        gp.add(personBox, 1, row);
        gp.add(lblSearchPerson, 2, row);
        gp.add(txtSearchPerson, 3, row++);
        gp.add(lblNotesGen, 0, row);
        gp.add(txtNotes, 1, row++, 3, 1);

        // FASE 10: Agregar sección de devolución
        gp.add(new javafx.scene.control.Separator(), 0, row++, 4, 1);
        gp.add(chkIsReturn, 0, row++, 4, 1);
        gp.add(lblLoanCode, 0, row);
        gp.add(cmbLoanCode, 1, row++, 3, 1);
        gp.add(lblLoanInfo, 0, row++, 4, 1);
        gp.add(lblReturnType, 0, row);
        gp.add(cmbReturnType, 1, row++, 3, 1);
        gp.add(lblReturnNotes, 0, row);
        gp.add(txtReturnNotes, 1, row++, 3, 1);

        gp.add(new javafx.scene.control.Separator(), 0, row++, 4, 1);
        gp.add(lblProducts, 0, row++, 4, 1);
        gp.add(tblItems, 0, row++, 4, 1);
        gp.add(btnAddProduct, 0, row++, 4, 1);

        GridPane.setHgrow(dpDate, Priority.ALWAYS);
        GridPane.setHgrow(personBox, Priority.ALWAYS);
        GridPane.setHgrow(txtSearchPerson, Priority.ALWAYS);
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
                // FASE 10: Guardar todos los datos incluyendo devolución
                java.util.Map<String, Object> dialogData = new java.util.HashMap<>();
                dialogData.put("person", cmbPerson.getValue());
                dialogData.put("date", dpDate.getValue());
                dialogData.put("notes", txtNotes.getText());

                // Datos de devolución
                dialogData.put("isReturn", chkIsReturn.isSelected());
                if (chkIsReturn.isSelected()) {
                    dialogData.put("loanCode", cmbLoanCode.getValue());
                    dialogData.put("returnType", cmbReturnType.getValue());
                    dialogData.put("returnNotes", txtReturnNotes.getText());
                    dialogData.put("foundLoan", foundLoan[0]); // Pasar el préstamo encontrado
                }

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
                setText(empty || it == null ? null : it.getString("code") + " - " + it.getString("name") + " (Stock: " + it.getDouble("current_stock") + ")");
            }
        });
        cmbProduct.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Product it, boolean empty) {
                super.updateItem(it, empty);
                setText(empty || it == null ? null : it.getString("code") + " - " + it.getString("name") + " (Stock: " + it.getDouble("current_stock") + ")");
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
        txtSearchProduct.setPromptText("Buscar producto por nombre, ID o código (soporta escáner)...");
        txtSearchProduct.setStyle("-fx-font-size: 17;");

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
        txtQuantity.setPromptText("Cantidad");
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
                    double qty = Double.parseDouble(qText);
                    if (qty <= 0) {
                        Utils.showAlert("Validación", "La cantidad debe ser mayor que cero");
                        return null;
                    }
                    return new TransactionItemDTO(
                            p.getInteger("id"),
                            qty,
                            txtNotes.getText(),
                            p.getString("name")
                    );
                } catch (NumberFormatException ex) {
                    Utils.showAlert("Error", "La cantidad debe ser un número válido");
                }
            }
            return null;
        });

        return d.showAndWait();
    }

    private void showTransactionItemsDialog(@NotNull Transaction transaction) {
        Dialog<Void> d = new Dialog<>();
        d.setTitle("Items de la Transacción #" + transaction.getId());
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

        VBox vbox = new VBox(10, tblItems);
        vbox.setPadding(new Insets(15));
        d.getDialogPane().setContent(vbox);
        d.getDialogPane().setPrefSize(750, 500);

        d.showAndWait();
    }

    // ===== MÉTODOS DE INVALIDACIÓN Y RESTAURACIÓN =====


    /**
     * Configurar permisos del usuario actual
     */
    private void setupPermissions() {
        // Verificar permiso de invalidar (DELETE en ingresos)
        canInvalidate = PermissionHelper.canAccess("INGRESOS", "DELETE");

        // Verificar permiso de restaurar
        canRestore = PermissionHelper.canAccess("INGRESOS", "RESTORE");

        // Verificar permiso de ver eliminados
        canViewDeleted = PermissionHelper.canAccess("SISTEMA", "VIEW_DELETED");

        // Configurar botón ver invalidados
        if (btnViewInvalidated != null) {
            btnViewInvalidated.setVisible(canViewDeleted);
            btnViewInvalidated.setManaged(canViewDeleted);
        }

        Logs.info(getClass(), "Permisos configurados - Invalidar: {}, Restaurar: {}, Ver eliminados: {}",
            canInvalidate, canRestore, canViewDeleted);
    }

    /**
     * Invalidar un ingreso
     */
    private void onInvalidateIncome(Transaction transaction) {
        if (!canInvalidate) {
            Utils.showAlert("Acceso Denegado", "No tiene permiso para invalidar ingresos");
            return;
        }

        if (!transaction.isActive()) {
            Utils.showAlert("Error", "Este ingreso ya está invalidado");
            return;
        }

        // Confirmar invalidación
        boolean confirmed = Utils.showConfirmation(
            "Confirmar Invalidación",
            "¿Está seguro de invalidar este ingreso?\n\n" +
            "ID: " + transaction.getInteger("id") + "\n" +
            "Esta acción revertirá el stock de los productos ingresados."
        );

        if (!confirmed) {
            return;
        }

        // Solicitar razón
        javafx.scene.control.TextInputDialog reasonDialog = new javafx.scene.control.TextInputDialog();
        reasonDialog.setTitle("Razón de Invalidación");
        reasonDialog.setHeaderText("Ingrese la razón por la cual invalida este ingreso:");
        reasonDialog.setContentText("Razón:");

        java.util.Optional<String> reasonResult = reasonDialog.showAndWait();
        if (reasonResult.isEmpty() || reasonResult.get().trim().isEmpty()) {
            Utils.showAlert("Cancelado", "Debe proporcionar una razón para invalidar");
            return;
        }

        try {
            transactionService.invalidate(transaction, reasonResult.get());
            Utils.showAlert("Éxito", "Ingreso invalidado correctamente");
            loadPageFromRepo(currentPage);
        } catch (Exception e) {
            Logs.error(getClass(), "Error invalidando ingreso: {}", e.getMessage());
            Utils.showAlert("Error", "No se pudo invalidar el ingreso: " + e.getMessage());
        }
    }

    /**
     * Restaurar un ingreso invalidado
     */
    private void onRestoreIncome(Transaction transaction) {
        if (!canRestore) {
            Utils.showAlert("Acceso Denegado", "No tiene permiso para restaurar ingresos");
            return;
        }

        if (transaction.isActive()) {
            Utils.showAlert("Error", "Este ingreso ya está activo");
            return;
        }

        // Confirmar restauración
        boolean confirmed = Utils.showConfirmation(
            "Confirmar Restauración",
            "¿Está seguro de restaurar este ingreso?\n\n" +
            "ID: " + transaction.getInteger("id") + "\n" +
            "Esta acción volverá a incrementar el stock de los productos."
        );

        if (!confirmed) {
            return;
        }

        try {
            transactionService.restore(transaction);
            Utils.showAlert("Éxito", "Ingreso restaurado correctamente");
            loadPageFromRepo(currentPage);
        } catch (Exception e) {
            Logs.error(getClass(), "Error restaurando ingreso: {}", e.getMessage());
            Utils.showAlert("Error", "No se pudo restaurar el ingreso: " + e.getMessage());
        }
    }

    /**
     * Mostrar ingresos invalidados
     */
    @FXML
    public void showInvalidatedIncomes() {
        showingInvalidated = !showingInvalidated;
        if (btnViewInvalidated != null) {
            btnViewInvalidated.setText(showingInvalidated ? "Ver Activos" : "Ver Invalidados");
        }
        currentPage = 1;
        loadPageFromRepo(currentPage);
    }
}
