package com.joshuamc1.sicam.controllers.dashboard;

import com.joshuamc1.sicam.config.DatabaseConfig;
import com.joshuamc1.sicam.core.Logs;
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
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;
import java.util.List;

public class OutcomesController {
    @FXML
    private TableView<Transaction> tblTransactions;
    @FXML private Button btnNewOutcome;
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
                btnViewItems.setStyle("-fx-font-size: 14; -fx-background-color: #FF9800; -fx-text-fill: white; -fx-padding: 5 10;");
                btnInvalidate.setStyle("-fx-font-size: 14; -fx-background-color: #f44336; -fx-text-fill: white; -fx-padding: 5 10;");
                btnRestore.setStyle("-fx-font-size: 14; -fx-background-color: #4CAF50; -fx-text-fill: white; -fx-padding: 5 10;");

                btnViewItems.setOnAction(e -> {
                    Transaction transaction = getTableView().getItems().get(getIndex());
                    showTransactionItemsDialog(transaction);
                });

                btnInvalidate.setOnAction(e -> {
                    Transaction transaction = getTableView().getItems().get(getIndex());
                    onInvalidateOutcome(transaction);
                });

                btnRestore.setOnAction(e -> {
                    Transaction transaction = getTableView().getItems().get(getIndex());
                    onRestoreOutcome(transaction);
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
        if (btnNewOutcome != null) {
            com.joshuamc1.sicam.core.PermissionHelper.hideIfNoPermission(btnNewOutcome, "EGRESOS", "CREATE");
        }
    }

    @FXML
    public void onRefresh() {
        loadPageFromRepo(currentPage);
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
                        count = transactionService.countByType(Transaction.TYPE_OUT);
                        pageItems = transactionService.findByTypePage(Transaction.TYPE_OUT, pageSize, offset);
                    } else {
                        // Búsqueda filtrada
                        String search = searchText.trim().toLowerCase();
                        List<Transaction> allTransactions = transactionService.findByType(Transaction.TYPE_OUT);
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
                    Logs.error(getClass(), "Error cargando página de EGRESOS: {}", t.getMessage());
                    Platform.runLater(() -> Utils.showAlert("Error", "No se pudo cargar EGRESOS: " + t.getMessage()));
                } finally {
                    DatabaseConfig.closeConnection();
                }
                return null;
            }
        };

        new Thread(task, "outcomes-pager").start();
    }

    private void updatePaginationControls() {
        if (lblPage != null) lblPage.setText(String.format("Página %d/%d (%d)", currentPage, totalPages, totalItems));
        if (btnPrev != null) btnPrev.setDisable(currentPage <= 1);
        if (btnNext != null) btnNext.setDisable(currentPage >= totalPages);
    }


    @FXML
    public void onNew() {
        // Validar permiso específico del módulo EGRESOS antes de crear
        if (!com.joshuamc1.sicam.core.PermissionHelper.validateAndShow("EGRESOS", "CREATE")) {
            return;
        }

        Dialog<List<TransactionItemDTO>> dialog = createMultiProductDialog();
        dialog.showAndWait().ifPresent(items -> {
            try {
                // Obtener datos comunes del diálogo
                Person selectedPerson = (Person) dialog.getDialogPane().getUserData();
                String date = LocalDate.now().toString(); // Se puede obtener del diálogo si se necesita
                String notes = ""; // Se puede obtener del diálogo si se necesita

                transactionService.createMultiProductTransaction(
                        Transaction.TYPE_OUT,
                        date,
                        Session.getCurrentUserId(),
                        selectedPerson != null ? selectedPerson.getInteger("id") : null,
                        notes,
                        items
                );

                loadPageFromRepo(currentPage);
                Utils.showAlert("Éxito", "egreso multi-producto registrado exitosamente con " + items.size() + " productos");
            } catch (Exception e) {
                Logs.error(getClass(), "Error creando egreso multi-producto: {}", e.getMessage());
                Utils.showAlert("Error", "No se pudo registrar el egreso: " + e.getMessage());
            }
        });
    }

    private @NotNull Dialog<List<TransactionItemDTO>> createMultiProductDialog() {
        Dialog<List<TransactionItemDTO>> d = new Dialog<>();
        d.setResizable(true);
        d.getDialogPane().setPrefSize(1200, 700);
        d.setTitle("Nuevo egreso Multi-Producto");
        d.setHeaderText("Registrar entrada de múltiples productos al inventario");
        ButtonType save = new ButtonType("Guardar egreso", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        // ObservableList para los items que se van agregando
        ObservableList<TransactionItemDTO> items = FXCollections.observableArrayList();

        // === SECCIÓN 1: INFORMACIÓN GENERAL ===
        Label lblGeneral = new Label("Información General");
        lblGeneral.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #FF9800;");

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
        txtNotes.setPromptText("Notas generales del egreso (opcional)");
        txtNotes.setPrefRowCount(2);
        txtNotes.setStyle("-fx-font-size: 17;");

        // === SECCIÓN 2: PRODUCTOS ===
        Label lblProducts = new Label("Productos");
        lblProducts.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #FF9800;");

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
        gp.add(lblNotesGen, 0, row);
        gp.add(txtNotes, 1, row++, 3, 1);

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
                // Guardar persona seleccionada en UserData para usarla después
                d.getDialogPane().setUserData(cmbPerson.getValue());
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

    /**
     * Configurar permisos del usuario actual
     */
    private void setupPermissions() {
        canInvalidate = com.joshuamc1.sicam.core.PermissionHelper.canAccess("EGRESOS", "DELETE");
        canRestore = com.joshuamc1.sicam.core.PermissionHelper.canAccess("EGRESOS", "RESTORE");
        canViewDeleted = com.joshuamc1.sicam.core.PermissionHelper.canAccess("SISTEMA", "VIEW_DELETED");

        if (btnViewInvalidated != null) {
            btnViewInvalidated.setVisible(canViewDeleted);
            btnViewInvalidated.setManaged(canViewDeleted);
        }

        Logs.info(getClass(), "Permisos configurados - Invalidar: {}, Restaurar: {}, Ver eliminados: {}",
            canInvalidate, canRestore, canViewDeleted);
    }

    /**
     * Invalidar un egreso
     */
    private void onInvalidateOutcome(Transaction transaction) {
        if (!canInvalidate) {
            Utils.showAlert("Acceso Denegado", "No tiene permiso para invalidar egresos");
            return;
        }

        if (!transaction.isActive()) {
            Utils.showAlert("Error", "Este egreso ya está invalidado");
            return;
        }

        boolean confirmed = Utils.showConfirmation(
            "Confirmar Invalidación",
            "¿Está seguro de invalidar este egreso?\n\n" +
            "ID: " + transaction.getInteger("id") + "\n" +
            "Esta acción revertirá el stock de los productos egresados."
        );

        if (!confirmed) {
            return;
        }

        javafx.scene.control.TextInputDialog reasonDialog = new javafx.scene.control.TextInputDialog();
        reasonDialog.setTitle("Razón de Invalidación");
        reasonDialog.setHeaderText("Ingrese la razón por la cual invalida este egreso:");
        reasonDialog.setContentText("Razón:");

        java.util.Optional<String> reasonResult = reasonDialog.showAndWait();
        if (reasonResult.isEmpty() || reasonResult.get().trim().isEmpty()) {
            Utils.showAlert("Cancelado", "Debe proporcionar una razón para invalidar");
            return;
        }

        try {
            transactionService.invalidate(transaction, reasonResult.get());
            Utils.showAlert("Éxito", "Egreso invalidado correctamente");
            loadPageFromRepo(currentPage);
        } catch (Exception e) {
            Logs.error(getClass(), "Error invalidando egreso: {}", e.getMessage());
            Utils.showAlert("Error", "No se pudo invalidar el egreso: " + e.getMessage());
        }
    }

    /**
     * Restaurar un egreso invalidado
     */
    private void onRestoreOutcome(Transaction transaction) {
        if (!canRestore) {
            Utils.showAlert("Acceso Denegado", "No tiene permiso para restaurar egresos");
            return;
        }

        if (transaction.isActive()) {
            Utils.showAlert("Error", "Este egreso ya está activo");
            return;
        }

        boolean confirmed = Utils.showConfirmation(
            "Confirmar Restauración",
            "¿Está seguro de restaurar este egreso?\n\n" +
            "ID: " + transaction.getInteger("id") + "\n" +
            "Esta acción volverá a restar el stock de los productos."
        );

        if (!confirmed) {
            return;
        }

        try {
            transactionService.restore(transaction);
            Utils.showAlert("Éxito", "Egreso restaurado correctamente");
            loadPageFromRepo(currentPage);
        } catch (Exception e) {
            Logs.error(getClass(), "Error restaurando egreso: {}", e.getMessage());
            Utils.showAlert("Error", "No se pudo restaurar el egreso: " + e.getMessage());
        }
    }

    /**
     * Mostrar egresos invalidados
     */
    @FXML
    public void showInvalidatedOutcomes() {
        showingInvalidated = !showingInvalidated;
        if (btnViewInvalidated != null) {
            btnViewInvalidated.setText(showingInvalidated ? "Ver Activos" : "Ver Invalidados");
        }
        currentPage = 1;
        loadPageFromRepo(currentPage);
    }
}
