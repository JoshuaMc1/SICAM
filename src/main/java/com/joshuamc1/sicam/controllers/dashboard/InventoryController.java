package com.joshuamc1.sicam.controllers.dashboard;

import com.joshuamc1.sicam.core.BarcodeGenerator;
import com.joshuamc1.sicam.core.Logs;
import com.joshuamc1.sicam.core.PermissionHelper;
import com.joshuamc1.sicam.core.Session;
import com.joshuamc1.sicam.core.Utils;
import com.joshuamc1.sicam.core.enums.Units;
import com.joshuamc1.sicam.core.enums.StockLevel;
import com.joshuamc1.sicam.models.Product;
import com.joshuamc1.sicam.models.User;
import com.joshuamc1.sicam.services.ProductService;
import com.joshuamc1.sicam.services.InventoryImportService;
import com.joshuamc1.sicam.config.DatabaseConfig;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

// POI para exportar a Excel
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class InventoryController {
    private final ProductService productService = new ProductService();

    @FXML private TableView<Product> tblProducts;
    @FXML private TableColumn<Product, Integer> colId;
    @FXML private TableColumn<Product, String> colStatus;
    @FXML private TableColumn<Product, String> colCode;
    @FXML private TableColumn<Product, String> colName;
    @FXML private TableColumn<Product, String> colDescription;
    @FXML private TableColumn<Product, String> colUnit;
    @FXML private TableColumn<Product, Double> colStock;
    @FXML private TableColumn<Product, Double> colMinStock;
    @FXML private TableColumn<Product, StockLevel> colStockLevel;
    @FXML private ToggleButton btnStockAll;
    @FXML private ToggleButton btnViewDeleted;
    private boolean showDeleted;
    private boolean canViewDeleted;
    @FXML private ToggleButton btnStockNear;
    @FXML private ToggleButton btnStockBelow;
    @FXML private ToggleButton btnStockEmpty;
    private StockLevel stockFilter;
    @FXML private TableColumn<Product, Void> colActions;
    @FXML private Label lblBadge;

    @FXML private Button btnRefresh;
    @FXML private Button btnNew;
    @FXML private Button btnEdit;
    @FXML private Button btnDelete;
    @FXML private Button btnImport;
    @FXML private Button btnExport;
    @FXML private TextField txtSearch;
    @FXML private Button btnClearSearch;
    // Controles de paginación
    @FXML private Button btnPrev;
    @FXML private Button btnNext;
    @FXML private Label lblPage;
    @FXML private ChoiceBox<Integer> choicePageSize;

    // Estado de paginación
    private int currentPage = 1; // 1-based
    private int pageSize = 25;
    private int totalItems = 0;
    private int totalPages = 1;

    // Permisos
    private boolean canRestore = false;

    private List<Product> allProducts = FXCollections.observableArrayList();
    private boolean updatingPage;
    private final ObservableList<Product> currentPageItems = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        // Configurar permisos primero
        canRestore = com.joshuamc1.sicam.core.PermissionHelper.canAccess("INVENTARIO", "RESTORE");

        ToggleGroup stockFilters = new ToggleGroup();
        for (ToggleButton button : List.of(btnStockAll, btnStockNear, btnStockBelow, btnStockEmpty)) {
            button.setToggleGroup(stockFilters);
        }
        btnStockNear.setUserData(StockLevel.NEAR_MINIMUM);
        btnStockBelow.setUserData(StockLevel.BELOW_MINIMUM);
        btnStockEmpty.setUserData(StockLevel.OUT_OF_STOCK);
        btnStockAll.setSelected(true);
        stockFilters.selectedToggleProperty().addListener((obs, previous, selected) -> {
            if (selected == null) {
                btnStockAll.setSelected(true);
                return;
            }
            stockFilter = (StockLevel) selected.getUserData();
            currentPage = 1;
            applyFiltersAndPagination();
        });
        btnStockNear.setTooltip(new Tooltip("Stock entre el mínimo y un 20 % por encima del mínimo."));
        btnStockBelow.setTooltip(new Tooltip("Stock mayor que cero y menor que el mínimo."));
        btnStockEmpty.setTooltip(new Tooltip("Stock igual o menor que cero."));

        setupTable();
        configurePermissions();
        loadProducts();

        btnViewDeleted.setOnAction(e -> {
            if (!PermissionHelper.validateAndShow("SISTEMA", "VIEW_DELETED")) {
                btnViewDeleted.setSelected(false);
                return;
            }
            showDeleted = btnViewDeleted.isSelected();
            stockFilter = null;
            btnStockAll.setSelected(true);
            updateDeletedView();
            currentPage = 1;
            loadProducts();
        });
        btnRefresh.setOnAction(e -> loadProducts());
        btnNew.setOnAction(e -> openCreateDialog());
        btnEdit.setOnAction(e -> openEditDialog());
        btnDelete.setOnAction(e -> deleteSelected());

        // Botón Importar (solo visible si usuario root por permiso del sistema)
        if (btnImport != null) {
            boolean isRoot = com.joshuamc1.sicam.core.PermissionHelper.canAccess("SISTEMA", "DESTRUCTIVE_OPERATIONS");
            btnImport.setVisible(isRoot);
            btnImport.setManaged(isRoot);
            btnImport.setOnAction(e -> openImportDialog());
        }

        // Botón Exportar (solo ADMIN o ROOT)
        if (btnExport != null) {
            boolean canExport = PermissionHelper.isAdminOrRoot();
            btnExport.setVisible(canExport);
            btnExport.setManaged(canExport);
            btnExport.setOnAction(e -> openExportDialog());
        }

        updateDeletedView();

        // Búsqueda automática con debounce de 500ms
        javafx.animation.PauseTransition searchPause = new javafx.animation.PauseTransition(javafx.util.Duration.millis(500));
        txtSearch.textProperty().addListener((obs, oldVal, newVal) -> {
            searchPause.setOnFinished(event -> {
                currentPage = 1;
                applyFiltersAndPagination();
            });
            searchPause.playFromStart();
        });
        btnClearSearch.setOnAction(e -> {
            txtSearch.clear();
            currentPage = 1;
            applyFiltersAndPagination();
        });

        // Inicializar paginación
        choicePageSize.setItems(FXCollections.observableArrayList(10,25,50,100));
        choicePageSize.setValue(pageSize);
        choicePageSize.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                pageSize = newV;
                currentPage = 1;
                applyFiltersAndPagination();
            }
        });
        btnPrev.setOnAction(e -> {
            if (currentPage > 1) {
                currentPage--;
                applyFiltersAndPagination();
            }
        });
        btnNext.setOnAction(e -> {
            if (currentPage < totalPages) {
                currentPage++;
                applyFiltersAndPagination();
            }
        });
    }

    /**
     * Configura la visibilidad de los botones según los permisos del usuario
     */
    private void configurePermissions() {
        User currentUser = Session.getCurrentUser();
        if (currentUser == null) {
            Logs.warn(getClass(), "No hay usuario en sesión para configurar permisos");
            return;
        }

        Logs.info(getClass(), "Configurando permisos de inventario para: {}",
            currentUser.getString("username"));

        // Botón Nuevo - Requiere CREATE
        PermissionHelper.hideIfNoPermission(btnNew, "INVENTARIO", "CREATE");

        // Botón Editar - Requiere UPDATE
        PermissionHelper.hideIfNoPermission(btnEdit, "INVENTARIO", "UPDATE");

        // Botón Eliminar - Requiere DELETE
        PermissionHelper.hideIfNoPermission(btnDelete, "INVENTARIO", "DELETE");

        // Si solo tiene READ, la tabla es de solo lectura
        boolean hasOnlyRead = PermissionHelper.canAccess("INVENTARIO", "READ") &&
                             !PermissionHelper.canAccess("INVENTARIO", "UPDATE") &&
                             !PermissionHelper.canAccess("INVENTARIO", "DELETE");

        if (hasOnlyRead) {
            Logs.info(getClass(), "Usuario con acceso de solo lectura al inventario");
        }

        Logs.info(getClass(), "Permisos de inventario configurados");
        canViewDeleted = PermissionHelper.canAccess("SISTEMA", "VIEW_DELETED");
        updateDeletedView();
    }

    private void setupTable() {
        colId.setCellValueFactory(c -> {
            Integer id = c.getValue().getInteger("id");
            return new SimpleIntegerProperty(id == null ? 0 : id).asObject();
        });

        colCode.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getString("code")));
        colName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getString("name")));
        colDescription.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getString("description")));
        colUnit.setCellValueFactory(c -> new SimpleStringProperty(Units.fromDbValue(c.getValue().getString("unit")).getDisplayName()));
        colStock.setCellValueFactory(c -> {
            Double v = c.getValue().getDouble("current_stock");
            return new SimpleDoubleProperty(v == null ? 0.0 : v).asObject();
        });
        colMinStock.setCellValueFactory(c -> {
            Double v = c.getValue().getDouble("minimum_stock");
            return new SimpleDoubleProperty(v == null ? 0.0 : v).asObject();
        });

        colStockLevel.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(
            StockLevel.classify(c.getValue().getDouble("current_stock"), c.getValue().getDouble("minimum_stock"))));
        colStockLevel.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(StockLevel level, boolean empty) {
                super.updateItem(level, empty);
                setText(null);
                if (empty || level == null) {
                    setGraphic(null);
                    return;
                }
                Label badge = new Label(level.getLabel());
                badge.setStyle("-fx-background-color: " + level.getColor()
                    + "; -fx-text-fill: " + (level == StockLevel.NEAR_MINIMUM ? "#212121" : "white")
                    + "; -fx-padding: 5 10; -fx-background-radius: 5;");
                setGraphic(badge);
            }
        });

        // Columna de estado con badge visual
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Product product = getTableView().getItems().get(getIndex());
                    String label = product.isDeleted() ? "Eliminado" : product.isActive() ? "Activo" : "Inactivo";
                    String color = product.isDeleted() ? "#D32F2F" : product.isActive() ? "#4CAF50" : "#9E9E9E";
                    Label badge = new Label(label);
                    badge.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; -fx-padding: 5 10; -fx-background-radius: 5;");
                    setGraphic(badge);
                    setText(null);
                }
            }
        });

        // Configurar columna de acciones con botones de código y restaurar
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnBarcode = new Button("⬇ Br");
            private final Button btnQR = new Button("⬇ Qr");
            private final Button btnRestore = new Button("Restaurar");
            private final HBox paneActive = new HBox(5, btnBarcode, btnQR);
            private final HBox paneDeleted = new HBox(5, btnRestore);

            {
                paneActive.setAlignment(Pos.CENTER);
                paneDeleted.setAlignment(Pos.CENTER);
                btnBarcode.setStyle("-fx-font-size: 16; -fx-cursor: hand;");
                btnQR.setStyle("-fx-font-size: 16; -fx-cursor: hand;");
                btnRestore.setStyle("-fx-font-size: 12; -fx-background-color: #4CAF50; -fx-text-fill: white;");
                btnBarcode.setTooltip(new Tooltip("Descargar Código de Barras"));
                btnQR.setTooltip(new Tooltip("Descargar Código QR"));

                btnBarcode.setOnAction(e -> {
                    Product p = getTableView().getItems().get(getIndex());
                    downloadBarcode(p);
                });

                btnQR.setOnAction(e -> {
                    Product p = getTableView().getItems().get(getIndex());
                    downloadQRCode(p);
                });

                btnRestore.setOnAction(e -> {
                    Product p = getTableView().getItems().get(getIndex());
                    restoreProduct(p);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    Product p = getTableView().getItems().get(getIndex());
                    boolean isDeleted = p.get("deleted_at") != null;

                    // Si está eliminado y tiene permiso de restaurar, mostrar botón restaurar
                    if (isDeleted && canRestore) {
                        setGraphic(paneDeleted);
                    } else if (!isDeleted) {
                        // Si está activo, mostrar botones de código si hay código
                        String code = p.getString("code");
                        if (code != null && !code.trim().isEmpty()) {
                            setGraphic(paneActive);
                        } else {
                            setGraphic(null);
                        }
                    } else {
                        setGraphic(null);
                    }
                }
            }
        });

        colId.setStyle("-fx-font-size: 17;");
        colStatus.setStyle("-fx-font-size: 17;");
        colCode.setStyle("-fx-font-size: 17;");
        colName.setStyle("-fx-font-size: 17;");
        colDescription.setStyle("-fx-font-size: 17;");
        colUnit.setStyle("-fx-font-size: 17;");
        colStock.setStyle("-fx-font-size: 17;");
        colMinStock.setStyle("-fx-font-size: 17;");
        colStockLevel.setStyle("-fx-font-size: 17;");
        lblBadge.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");
        colActions.setStyle("-fx-font-size: 17;");

        tblProducts.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        tblProducts.setSortPolicy(table -> {
            if (!updatingPage) {
                currentPage = 1;
                applyFiltersAndPagination();
            }
            return true;
        });
    }

    private void loadProducts() {
        Task<List<Product>> task = new Task<>() {
            @Override
            protected java.util.List<Product> call() {
                DatabaseConfig.openConnection();
                try {
                    com.joshuamc1.sicam.core.Authorization.require("INVENTARIO","READ");
                    List<Product> products = productService.findAll();

                    // Consultar eliminados solo con el permiso de visualización correspondiente
                    if (com.joshuamc1.sicam.core.PermissionHelper.canAccess("SISTEMA","VIEW_DELETED")) {
                        List<Product> deleted = productService.findDeleted();
                        products = new java.util.ArrayList<>(products);
                        products.addAll(deleted);
                    }

                    products = new java.util.ArrayList<>(products);
                    products.sort(java.util.Comparator.comparing((Product product) -> product.getInteger("id")).reversed());
                    return products;
                } finally {
                    DatabaseConfig.closeConnection();
                }
            }
        };

        task.setOnSucceeded(evt -> {
            allProducts = task.getValue();
            canViewDeleted = PermissionHelper.canAccess("SISTEMA", "VIEW_DELETED");
            if (!canViewDeleted) showDeleted = false;
            updateDeletedView();
            currentPage = 1;
            applyFiltersAndPagination();
        });
        task.setOnFailed(evt -> {
            Throwable t = task.getException();
            Utils.showAlert("Error", "No se pudieron cargar los productos: " + (t == null ? "unknown" : t.getMessage()));
        });

        Thread th = new Thread(task, "inventory-loader");
        th.setDaemon(true);
        th.start();
    }

    private void updateDeletedView() {
        btnViewDeleted.setVisible(canViewDeleted);
        btnViewDeleted.setManaged(canViewDeleted);
        btnViewDeleted.setSelected(showDeleted);
        btnViewDeleted.setText(showDeleted ? "Ver inventario" : "Ver eliminados");
        for (ToggleButton button : List.of(btnStockAll, btnStockNear, btnStockBelow, btnStockEmpty)) {
            button.setDisable(showDeleted);
        }
        btnEdit.setDisable(showDeleted);
        btnDelete.setDisable(showDeleted && !PermissionHelper.canAccess("SISTEMA", "DESTRUCTIVE_OPERATIONS"));
    }

    private void applyFiltersAndPagination() {
        String searchText = txtSearch.getText();
        List<Product> filtered;
        if (searchText == null || searchText.trim().isEmpty()) {
            filtered = allProducts;
        } else {
            String search = searchText.trim().toLowerCase();
            filtered = allProducts.stream()
                .filter(p -> {
                    String name = p.getString("name");
                    String id = String.valueOf(p.getInteger("id"));
                    String code = p.getString("code");
                    return (name != null && name.toLowerCase().contains(search))
                        || (id.contains(search))
                        || (code != null && code.toLowerCase().contains(search));
                })
                .collect(java.util.stream.Collectors.toList());
        }
        filtered = filtered.stream()
            .filter(product -> product.isDeleted() ? showDeleted && canViewDeleted : !showDeleted)
            .collect(java.util.stream.Collectors.toList());
        if (stockFilter != null) {
            filtered = filtered.stream()
                .filter(product -> !product.isDeleted())
                .filter(product -> StockLevel.classify(product.getDouble("current_stock"),
                    product.getDouble("minimum_stock")) == stockFilter)
                .collect(java.util.stream.Collectors.toList());
        }
        java.util.Comparator<Product> order = tblProducts.getComparator();
        java.util.Comparator<Product> newestFirst = java.util.Comparator.comparing(
            (Product product) -> product.getInteger("id")).reversed();
        filtered.sort(order == null ? newestFirst : order.thenComparing(newestFirst));
        totalItems = filtered.size();
        totalPages = Math.max(1, (int)Math.ceil(totalItems / (double)pageSize));
        if (currentPage > totalPages) currentPage = totalPages;
        int fromIndex = (currentPage - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, totalItems);
        List<Product> pageSlice = fromIndex >= toIndex ? java.util.Collections.emptyList() : filtered.subList(fromIndex, toIndex);
        updatingPage = true;
        try {
            currentPageItems.setAll(pageSlice);
            tblProducts.setItems(currentPageItems);
        } finally {
            updatingPage = false;
        }
        updatePaginationControls();
    }

    private void updatePaginationControls() {
        if (lblPage != null) lblPage.setText(String.format("Página %d/%d (%d)", currentPage, totalPages, totalItems));
        if (btnPrev != null) btnPrev.setDisable(currentPage <= 1);
        if (btnNext != null) btnNext.setDisable(currentPage >= totalPages);
    }

    private void openCreateDialog() {
        // Validar permiso antes de abrir el diálogo
        if (!PermissionHelper.validateAndShow("INVENTARIO", "CREATE")) {
            return;
        }

        Dialog<Product> dialog = new Dialog<>();
        dialog.setTitle("Nuevo producto");

        ButtonType createBtn = new ButtonType("Crear", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createBtn, ButtonType.CANCEL);

        // Declarar todos los campos primero
        TextField txtCode = new TextField();
        TextField txtName = new TextField();
        TextArea txtDesc = new TextArea();
        ComboBox<Units> cbUnit = new ComboBox<>(FXCollections.observableArrayList(Units.values()));
        TextField txtCurrentStock = new TextField();
        TextField txtMinStock = new TextField();

        // Configurar txtCode
        txtCode.setPromptText("Ingrese o escanee el código");
        // Generar código automáticamente al abrir el diálogo
        txtCode.setText(BarcodeGenerator.generateRandomCode());
        Button btnGenerateCode = new Button("🎲");
        btnGenerateCode.setTooltip(new Tooltip("Generar código aleatorio"));
        btnGenerateCode.setOnAction(e -> txtCode.setText(BarcodeGenerator.generateRandomCode()));

        // Prevenir que Enter cierre el diálogo cuando se escanea un código
        txtCode.setOnKeyPressed(event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ENTER) {
                event.consume();
                // Mover foco al siguiente campo
                txtName.requestFocus();
            }
        });

        HBox hboxCode = new HBox(5, txtCode, btnGenerateCode);
        hboxCode.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        txtCode.prefWidthProperty().bind(hboxCode.widthProperty().subtract(btnGenerateCode.widthProperty()).subtract(10));

        // Configurar otros campos
        cbUnit.setValue(Units.UNIDAD);
        txtDesc.setPrefRowCount(3);

        // Aplicar validadores numéricos
        txtCurrentStock.setTextFormatter(com.joshuamc1.sicam.utils.ValidationHelper.getDecimalFormatter());
        txtMinStock.setTextFormatter(com.joshuamc1.sicam.utils.ValidationHelper.getDecimalFormatter());

        // Aplicar estilos a los labels y campos de entrada
        txtCode.setStyle("-fx-font-size: 17;");
        btnGenerateCode.setStyle("-fx-font-size: 14;");
        txtName.setStyle("-fx-font-size: 17;");
        txtDesc.setStyle("-fx-font-size: 17;");
        cbUnit.setStyle("-fx-font-size: 17;");
        txtCurrentStock.setStyle("-fx-font-size: 17;");
        txtMinStock.setStyle("-fx-font-size: 17;");

        Label lblCode = new Label("Código:");
        Label lblName = new Label("Nombre:");
        Label lblDescription = new Label("Descripción:");
        Label lblUnit = new Label("Unidad:");
        Label lblCurrentStock = new Label("Stock actual:");
        Label lblMinStock = new Label("Stock mínimo:");

        lblCode.setStyle("-fx-font-weight: bold; -fx-font-size: 17;");
        lblName.setStyle("-fx-font-weight: bold; -fx-font-size: 17;");
        lblDescription.setStyle("-fx-font-weight: bold; -fx-font-size: 17;");
        lblUnit.setStyle("-fx-font-weight: bold; -fx-font-size: 17;");
        lblCurrentStock.setStyle("-fx-font-weight: bold; -fx-font-size: 17;");
        lblMinStock.setStyle("-fx-font-weight: bold; -fx-font-size: 17;");

        // Helper texts para campos obligatorios
        Label helperName = new Label("Obligatorio");
        helperName.setStyle("-fx-text-fill: #d60000; -fx-font-size: 12px;");
        Label helperUnit = new Label("Obligatorio");
        helperUnit.setStyle("-fx-text-fill: #d60000; -fx-font-size: 12px;");
        Label helperCurrentStock = new Label("Obligatorio");
        helperCurrentStock.setStyle("-fx-text-fill: #d60000; -fx-font-size: 12px;");
        Label helperMinStock = new Label("Obligatorio");
        helperMinStock.setStyle("-fx-text-fill: #d60000; -fx-font-size: 12px;");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(lblCode, 0, 0);
        grid.add(hboxCode, 1, 0);
        grid.add(lblName, 0, 1);
        grid.add(txtName, 1, 1);
        grid.add(helperName, 1, 2);
        grid.add(lblDescription, 0, 3);
        grid.add(txtDesc, 1, 3);
        grid.add(lblUnit, 0, 4);
        grid.add(cbUnit, 1, 4);
        grid.add(helperUnit, 1, 5);
        grid.add(lblCurrentStock, 0, 6);
        grid.add(txtCurrentStock, 1, 6);
        grid.add(helperCurrentStock, 1, 7);
        grid.add(lblMinStock, 0, 8);
        grid.add(txtMinStock, 1, 8);
        grid.add(helperMinStock, 1, 9);

        dialog.getDialogPane().setContent(grid);

        // Deshabilitar botón crear hasta que todos los campos obligatorios estén llenos
        javafx.scene.Node createButton = dialog.getDialogPane().lookupButton(createBtn);
        javafx.beans.binding.BooleanBinding isInvalid =
            com.joshuamc1.sicam.utils.ValidationHelper.isTextFieldEmpty(txtName)
            .or(com.joshuamc1.sicam.utils.ValidationHelper.isComboBoxEmpty(cbUnit))
            .or(com.joshuamc1.sicam.utils.ValidationHelper.isTextFieldEmpty(txtCurrentStock))
            .or(com.joshuamc1.sicam.utils.ValidationHelper.isTextFieldEmpty(txtMinStock));

        createButton.disableProperty().bind(isInvalid);

        dialog.setResultConverter(btn -> {
            if (btn == createBtn) {
                String code = txtCode.getText().trim();
                String name = txtName.getText().trim();
                String desc = txtDesc.getText().trim();
                Units unit = cbUnit.getValue();
                double min = 0.0;
                double current = 0.0;

                // Validar código si se proporcionó
                if (!code.isEmpty() && !BarcodeGenerator.isValidCode(code)) {
                    Utils.showAlert("Error", "El código debe contener solo letras, números, guiones y guiones bajos (3-100 caracteres)");
                    return null;
                }

                try {
                    current = Double.parseDouble(txtCurrentStock.getText().trim());
                } catch (Exception e) {
                    Utils.showAlert("Error", "Stock actual debe ser un número válido");
                    return null;
                }

                try {
                    min = Double.parseDouble(txtMinStock.getText().trim());
                } catch (Exception e) {
                    Utils.showAlert("Error", "Stock mínimo debe ser un número válido");
                    return null;
                }

                try {
                    return productService.createProduct(name, desc, unit.getDbValue(), current, min, code);
                } catch (IllegalArgumentException | IllegalStateException ex) {
                    Utils.showAlert("No se pudo crear el producto", ex.getMessage());
                    return null;
                } catch (RuntimeException ex) {
                    Logs.error(getClass(), "Error creando producto", ex);
                    Utils.showAlert("No se pudo crear el producto", "No fue posible guardar el producto. Intente nuevamente; si el problema continúa, revise el registro de errores.");
                    return null;
                }
            }
            return null;
        });

        Optional<Product> result = dialog.showAndWait();
        result.ifPresent(p -> {
            Utils.showAlert("Éxito", "Producto creado correctamente");

            loadProducts();
        });
    }

    private void openEditDialog() {
        // Validar permiso antes de abrir el diálogo
        if (!PermissionHelper.validateAndShow("INVENTARIO", "UPDATE")) {
            return;
        }

        Product sel = tblProducts.getSelectionModel().getSelectedItem();
        if (sel == null) {
            Utils.showAlert("Info", "Seleccione un producto para editar");
            return;
        }

        Dialog<Product> dialog = new Dialog<>();
        dialog.setTitle("Editar producto");
        dialog.setResizable(true);

        ButtonType saveBtn = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        // Declarar todos los campos primero
        String currentCode = sel.getString("code");
        TextField txtCode = new TextField(currentCode != null ? currentCode : "");
        TextField txtName = new TextField(sel.getString("name"));
        TextArea txtDesc = new TextArea(sel.getString("description"));
        ComboBox<Units> cbUnit = new ComboBox<>(FXCollections.observableArrayList(Units.values()));
        cbUnit.setValue(Units.fromDbValue(sel.getString("unit")));
        final double originalStock = sel.getDouble("current_stock") == null ? 0d : sel.getDouble("current_stock");
        TextField txtCurrentStock = new TextField(String.valueOf(sel.getDouble("current_stock") == null ? 0.0 : sel.getDouble("current_stock")));
        TextField txtMinStock = new TextField(String.valueOf(sel.getDouble("minimum_stock")));

        // Configurar txtCode
        txtCode.setPromptText("Ingrese o escanee el código");
        Button btnGenerateCode = new Button("🎲");
        btnGenerateCode.setTooltip(new Tooltip("Generar código aleatorio"));
        btnGenerateCode.setOnAction(e -> txtCode.setText(BarcodeGenerator.generateRandomCode()));

        // Prevenir que Enter cierre el diálogo cuando se escanea un código
        txtCode.setOnKeyPressed(event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ENTER) {
                event.consume();
                // Mover foco al siguiente campo
                txtName.requestFocus();
            }
        });

        HBox hboxCode = new HBox(5, txtCode, btnGenerateCode);
        hboxCode.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        txtCode.prefWidthProperty().bind(hboxCode.widthProperty().subtract(btnGenerateCode.widthProperty()).subtract(10));


        // Aplicar validadores numéricos
        txtCurrentStock.setTextFormatter(com.joshuamc1.sicam.utils.ValidationHelper.getDecimalFormatter());
        txtMinStock.setTextFormatter(com.joshuamc1.sicam.utils.ValidationHelper.getDecimalFormatter());

        txtCode.setStyle("-fx-font-size: 17;");
        btnGenerateCode.setStyle("-fx-font-size: 14;");
        txtName.setStyle("-fx-font-size: 17;");
        txtDesc.setStyle("-fx-font-size: 17;");
        cbUnit.setStyle("-fx-font-size: 17;");
        txtCurrentStock.setStyle("-fx-font-size: 17;");
        txtMinStock.setStyle("-fx-font-size: 17;");

        Label lblCode = new Label("Código:");
        Label lblName = new Label("Nombre:");
        Label lblDescription = new Label("Descripción:");
        Label lblUnit = new Label("Unidad:");
        Label lblCurrentStock = new Label("Stock actual:");
        Label lblMinStock = new Label("Stock mínimo:");

        lblCode.setStyle("-fx-font-weight: bold; -fx-font-size: 17;");
        lblName.setStyle("-fx-font-weight: bold; -fx-font-size: 17;");
        lblDescription.setStyle("-fx-font-weight: bold; -fx-font-size: 17;");
        lblUnit.setStyle("-fx-font-weight: bold; -fx-font-size: 17;");
        lblCurrentStock.setStyle("-fx-font-weight: bold; -fx-font-size: 17;");
        lblMinStock.setStyle("-fx-font-weight: bold; -fx-font-size: 17;");

        // Helper texts para campos obligatorios
        Label helperName = new Label("Obligatorio");
        helperName.setStyle("-fx-text-fill: #d60000; -fx-font-size: 12px;");
        Label helperUnit = new Label("Obligatorio");
        helperUnit.setStyle("-fx-text-fill: #d60000; -fx-font-size: 12px;");
        Label helperCurrentStock = new Label("Obligatorio");
        helperCurrentStock.setStyle("-fx-text-fill: #d60000; -fx-font-size: 12px;");
        Label helperMinStock = new Label("Obligatorio");
        helperMinStock.setStyle("-fx-text-fill: #d60000; -fx-font-size: 12px;");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(lblCode, 0, 0);
        grid.add(hboxCode, 1, 0);
        grid.add(lblName, 0, 1);
        grid.add(txtName, 1, 1);
        grid.add(helperName, 1, 2);
        grid.add(lblDescription, 0, 3);
        grid.add(txtDesc, 1, 3);
        grid.add(lblUnit, 0, 4);
        grid.add(cbUnit, 1, 4);
        grid.add(helperUnit, 1, 5);
        grid.add(lblCurrentStock, 0, 6);
        grid.add(txtCurrentStock, 1, 6);
        grid.add(helperCurrentStock, 1, 7);
        grid.add(lblMinStock, 0, 8);
        grid.add(txtMinStock, 1, 8);
        grid.add(helperMinStock, 1, 9);

        dialog.getDialogPane().setContent(grid);

        // Deshabilitar botón guardar hasta que todos los campos obligatorios estén llenos
        javafx.scene.Node saveButton = dialog.getDialogPane().lookupButton(saveBtn);
        javafx.beans.binding.BooleanBinding isInvalid =
            com.joshuamc1.sicam.utils.ValidationHelper.isTextFieldEmpty(txtName)
            .or(com.joshuamc1.sicam.utils.ValidationHelper.isComboBoxEmpty(cbUnit))
            .or(com.joshuamc1.sicam.utils.ValidationHelper.isTextFieldEmpty(txtCurrentStock))
            .or(com.joshuamc1.sicam.utils.ValidationHelper.isTextFieldEmpty(txtMinStock));

        saveButton.disableProperty().bind(isInvalid);

        dialog.setResultConverter(btn -> {
            if (btn == saveBtn) {
                String code = txtCode.getText().trim();
                String name = txtName.getText().trim();
                String desc = txtDesc.getText().trim();
                Units unit = cbUnit.getValue();

                double min = 0.0;
                double current = 0.0;

                // Validar código si se proporcionó
                if (!code.isEmpty() && !BarcodeGenerator.isValidCode(code)) {
                    Utils.showAlert("Error", "El código debe contener solo letras, números, guiones y guiones bajos (3-100 caracteres)");
                    return null;
                }

                try {
                    current = Double.parseDouble(txtCurrentStock.getText().trim());
                } catch (Exception e) {
                    Utils.showAlert("Error", "Stock actual debe ser un número válido");
                    return null;
                }

                try {
                    min = Double.parseDouble(txtMinStock.getText().trim());
                } catch (Exception e) {
                    Utils.showAlert("Error", "Stock mínimo debe ser un número válido");
                    return null;
                }

                sel.set("code", code.isEmpty() ? null : code);
                sel.set("name", name);
                sel.set("description", desc);
                sel.set("unit", unit.getDbValue());
                sel.set("minimum_stock", min);
                sel.set("current_stock", current);

                try {
                    productService.save(sel, originalStock);
                    return sel;
                } catch (IllegalArgumentException | IllegalStateException ex) {
                    Utils.showAlert("No se pudo actualizar el producto", ex.getMessage());
                    loadProducts();
                    return null;
                } catch (RuntimeException ex) {
                    Logs.error(getClass(), "Error actualizando producto", ex);
                    Utils.showAlert("No se pudo actualizar el producto", "No fue posible guardar los cambios. Intente nuevamente; si el problema continúa, revise el registro de errores.");
                    loadProducts();
                    return null;
                }
            }
            return null;
        });

        Optional<Product> result = dialog.showAndWait();
        result.ifPresent(p -> {
            Utils.showAlert("Éxito", "Producto actualizado correctamente");
            loadProducts();
        });
    }

    private void deleteSelected() {
        // Validar permiso antes de eliminar
        if (!PermissionHelper.validateAndShow("INVENTARIO", "DELETE")) {
            return;
        }

        Product sel = tblProducts.getSelectionModel().getSelectedItem();

        if (sel == null) {
            Utils.showAlert("Info", "Seleccione un producto para eliminar");
            return;
        }

        // Mostrar dialog con opciones según permisos
        boolean canHardDelete = PermissionHelper.canAccess("SISTEMA", "DESTRUCTIVE_OPERATIONS");

        if (canHardDelete) {
            // ROOT puede elegir entre soft delete y hard delete
            showDeleteOptionsDialog(sel);
        } else {
            // Usuario normal: solo soft delete
            boolean ok = Utils.confirmAction("Confirmar Eliminación",
                "¿Desea eliminar el producto: " + sel.getString("name") + "?");
            if (!ok) return;

            try {
                productService.softDelete(sel);
                Utils.showAlert("Éxito", "Producto eliminado correctamente");
                loadProducts();
            } catch (Exception e) {
                Logs.error(getClass(), "Error eliminando producto", e);
                Utils.showAlert("Error", "No se pudo eliminar el producto: " + e.getMessage());
            }
        }
    }

    /**
     * Muestra dialog con opciones de eliminación (solo para ROOT)
     */
    private void showDeleteOptionsDialog(Product product) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Opciones de Eliminación");
        dialog.setHeaderText("Producto: " + product.getString("name"));

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

        // Mantener la disponibilidad del botón coherente con las validaciones del servicio.
        dialog.getDialogPane().lookupButton(softDeleteBtn).setDisable(product.isDeleted());
        dialog.getDialogPane().lookupButton(hardDeleteBtn).setDisable(true);
        if (product.isActive()) {
            Label warning = new Label("Primero debe realizar la eliminación reversible del producto.");
            warning.setStyle("-fx-font-size: 13px; -fx-text-fill: #d60000;");
            warning.setWrapText(true);
            grid.add(warning, 0, 3);
        } else {
            try {
                Number transactionCount = com.joshuamc1.sicam.core.DatabaseManager.callInConnection(() ->
                    (Number) org.javalite.activejdbc.Base.firstCell(
                        "SELECT COUNT(*) FROM transaction_items WHERE product_id = ?", product.getId()));
                if (transactionCount == null) {
                    throw new IllegalStateException("No se obtuvo el número de transacciones asociadas");
                }
                if (transactionCount.longValue() == 0) {
                    dialog.getDialogPane().lookupButton(hardDeleteBtn).setDisable(false);
                } else {
                    Label warning = new Label("Este producto tiene " + transactionCount.longValue() +
                        " transacción(es) asociada(s).\nNo se puede eliminar permanentemente.");
                    warning.setStyle("-fx-font-size: 13px; -fx-text-fill: #d60000;");
                    warning.setWrapText(true);
                    grid.add(warning, 0, 3);
                }
            } catch (Exception e) {
                Logs.warn(getClass(), "No se pudo verificar transacciones asociadas: " + e.getMessage());
                Label warning = new Label("No se pudo verificar el historial. Cierre y vuelva a intentar.");
                warning.setStyle("-fx-font-size: 13px; -fx-text-fill: #d60000;");
                warning.setWrapText(true);
                grid.add(warning, 0, 3);
            }
        }

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn == softDeleteBtn) return "soft";
            if (btn == hardDeleteBtn) return "hard";
            return null;
        });

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(type -> {
            try {
                if ("soft".equals(type)) {
                    productService.softDelete(product);
                    Utils.showAlert("Éxito", "Producto eliminado correctamente");
                } else if ("hard".equals(type)) {
                    boolean confirm = Utils.confirmAction("⚠️ CONFIRMACIÓN FINAL",
                        "¿Está COMPLETAMENTE SEGURO de eliminar permanentemente?\n\n" +
                        "Esta acción NO SE PUEDE DESHACER.\n\n" +
                        "Producto: " + product.getString("name"));
                    if (confirm) {
                        productService.hardDelete(product);
                        Utils.showAlert("Éxito", "Producto eliminado permanentemente");
                    } else {
                        return;
                    }
                }
                loadProducts();
            } catch (IllegalStateException e) {
                // Errores de validación
                Logs.warn(getClass(), "Validación de eliminación: " + e.getMessage());
                Utils.showAlert("No se puede eliminar", e.getMessage());
            } catch (Exception e) {
                Logs.error(getClass(), "Error eliminando producto", e);
                Utils.showAlert("Error", "No se pudo eliminar el producto: " + e.getMessage());
            }
        });
    }

    /**
     * Restaura un producto eliminado (desde columna de acciones)
     */
    private void restoreProduct(Product product) {
        boolean ok = Utils.confirmAction("Confirmar Restauración",
            "¿Desea restaurar el producto: " + product.getString("name") + "?");
        if (!ok) return;

        try {
            productService.restore(product);
            Utils.showAlert("Éxito", "Producto restaurado correctamente");
            loadProducts();
        } catch (Exception e) {
            Logs.error(getClass(), "Error restaurando producto", e);
            Utils.showAlert("Error", "No se pudo restaurar el producto: " + e.getMessage());
        }
    }


    private void downloadBarcode(@NotNull Product product) {
        String code = product.getString("code");
        if (code == null || code.trim().isEmpty()) {
            Utils.showAlert("Error", "El producto no tiene código asignado");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Guardar Código de Barras");
        fileChooser.setInitialFileName("barcode_" + code + ".png");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("PNG Image", "*.png")
        );

        File file = fileChooser.showSaveDialog(tblProducts.getScene().getWindow());
        if (file != null) {
            try {
                BarcodeGenerator.generateBarcode(code, file);
                Utils.showAlert("Éxito", "Código de barras generado: " + file.getAbsolutePath());
            } catch (Exception e) {
                Logs.error(getClass(), "Error generando código de barras", e);
                Utils.showAlert("Error", "No se pudo generar el código de barras: " + e.getMessage());
            }
        }
    }

    private void downloadQRCode(@NotNull Product product) {
        String code = product.getString("code");
        String name = product.getString("name");

        if (code == null || code.trim().isEmpty()) {
            Utils.showAlert("Error", "El producto no tiene código asignado");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Guardar Código QR");
        fileChooser.setInitialFileName("qr_" + code + ".png");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("PNG Image", "*.png")
        );

        File file = fileChooser.showSaveDialog(tblProducts.getScene().getWindow());
        if (file != null) {
            try {
                BarcodeGenerator.generateQRCode(code, name, file);
                Utils.showAlert("Éxito", "Código QR generado: " + file.getAbsolutePath());
            } catch (Exception e) {
                Logs.error(getClass(), "Error generando código QR", e);
                Utils.showAlert("Error", "No se pudo generar el código QR: " + e.getMessage());
            }
        }
    }

    private boolean importBusy;

    private void openImportDialog() {
        if (importBusy) return;
        // Validación adicional por permisos
        if (!com.joshuamc1.sicam.core.PermissionHelper.canAccess("SISTEMA", "DESTRUCTIVE_OPERATIONS")) {
            Utils.showAlert("Permiso denegado", "Esta opción solo está disponible para el rol root.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Seleccionar archivo de inventario");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("CSV", "*.csv"),
                new FileChooser.ExtensionFilter("Excel", "*.xlsx")
        );
        File file = chooser.showOpenDialog(tblProducts.getScene().getWindow());
        if (file == null) return;

        importBusy = true;
        String importButtonText = btnImport.getText();
        btnImport.setText("Leyendo archivo…");
        btnImport.setDisable(true);
        InventoryImportService service = new InventoryImportService();
        Task<InventoryImportService.Plan> load = new Task<>() {
            @Override protected InventoryImportService.Plan call() throws Exception {
                return service.preview(service.readFile(file), InventoryImportService.Mode.UPSERT);
            }
        };
        load.setOnSucceeded(e -> {
            btnImport.setText(importButtonText);
            importBusy = false;
            btnImport.setDisable(false);
            showImportPreview(service, load.getValue());
        });
        load.setOnFailed(e -> {
            btnImport.setText(importButtonText);
            importBusy = false;
            btnImport.setDisable(false);
            Utils.showAlert("Error de importación", load.getException().getMessage());
        });
        Thread reader = new Thread(load, "inventory-preview");
        reader.setDaemon(true);
        reader.start();
    }

    private void showImportPreview(InventoryImportService service, InventoryImportService.Plan initial) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(tblProducts.getScene().getWindow());
        dialog.setTitle("Revisar importación de inventario");
        dialog.setResizable(true);
        dialog.getDialogPane().setPrefSize(1100, 650);
        ButtonType importType = new ButtonType("Importar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(importType, ButtonType.CANCEL);
        Button run = (Button) dialog.getDialogPane().lookupButton(importType);
        Button cancel = (Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        ComboBox<InventoryImportService.Mode> mode = new ComboBox<>(FXCollections.observableArrayList(InventoryImportService.Mode.values()));
        mode.setValue(initial.mode);
        Label summary = new Label(initial.summary());
        Label notice = new Label("Los códigos vacíos generan productos nuevos. Las actualizaciones reemplazan el stock y quedan registradas en Auditoría. Una descripción vacía conserva la existente.");
        notice.setWrapText(true);
        ProgressBar progress = new ProgressBar(0);
        progress.setMaxWidth(Double.MAX_VALUE);
        TableView<InventoryImportService.PreviewRow> table = new TableView<>();
        String[] titles = {"Fila", "Código", "Nombre", "Unidad", "Stock", "Mínimo", "Acción", "Cambios / errores"};
        for (int i = 0; i < titles.length; i++) {
            final int index = i;
            TableColumn<InventoryImportService.PreviewRow, String> column = new TableColumn<>(titles[i]);
            column.setCellValueFactory(c -> {
                var row = c.getValue();
                String value;
                switch (index) {
                    case 0: value = String.valueOf(row.input.row); break;
                    case 1: value = row.input.code; break;
                    case 2: value = row.input.name; break;
                    case 3: value = row.input.unit; break;
                    case 4: value = java.util.Objects.toString(row.input.stock, "Inválido"); break;
                    case 5: value = java.util.Objects.toString(row.input.minimum, "Inválido"); break;
                    case 6: value = row.action; break;
                    default: value = row.detail;
                }
                return new SimpleStringProperty(value);
            });
            column.setPrefWidth(index == 7 ? 600 : index == 2 ? 180 : 95);
            column.setCellFactory(col -> new TableCell<>() {
                @Override protected void updateItem(String value, boolean empty) {
                    super.updateItem(value, empty);
                    setText(empty ? null : value);
                    setTooltip(empty || value == null ? null : new Tooltip(value));
                }
            });
            table.getColumns().add(column);
        }
        table.setRowFactory(t -> new TableRow<>() {
            @Override protected void updateItem(InventoryImportService.PreviewRow row, boolean empty) {
                super.updateItem(row, empty);
                setStyle(!empty && row != null && row.hasError() ? "-fx-background-color: #ffebee;" : "");
            }
        });
        TextArea detail = new TextArea("Seleccione una fila para ver sus cambios o errores completos.");
        detail.setEditable(false);
        detail.setWrapText(true);
        detail.setPrefRowCount(3);
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, row) -> {
            if (row != null) detail.setText("Fila " + row.input.row + ": " + row.detail);
        });
        InventoryImportService.Plan[] selected = {initial};
        java.util.function.Consumer<InventoryImportService.Plan> display = plan -> {
            selected[0] = plan;
            table.setItems(FXCollections.observableArrayList(plan.rows));
            summary.setText(plan.summary());
            summary.setStyle(plan.hasErrors() ? "-fx-text-fill: #D32F2F;" : "");
            run.setDisable(plan.hasErrors() || plan.count("Crear") + plan.count("Actualizar") == 0);
        };
        display.accept(initial);
        mode.valueProperty().addListener((obs, old, value) -> {
            mode.setDisable(true); run.setDisable(true); cancel.setDisable(true);
            Task<InventoryImportService.Plan> refresh = new Task<>() {
                @Override protected InventoryImportService.Plan call() {
                    java.util.List<InventoryImportService.InputRow> inputs = new java.util.ArrayList<>();
                    for (var row : initial.rows) inputs.add(row.input);
                    return service.preview(inputs, value);
                }
            };
            refresh.setOnSucceeded(e -> { display.accept(refresh.getValue()); mode.setDisable(false); cancel.setDisable(false); });
            refresh.setOnFailed(e -> { detail.setText(refresh.getException().getMessage()); mode.setDisable(false); cancel.setDisable(false); });
            Thread thread = new Thread(refresh, "inventory-preview-mode"); thread.setDaemon(true); thread.start();
        });
        javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(10, new Label("Modo de importación"), mode, notice, summary, table, detail, progress);
        javafx.scene.layout.VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);
        dialog.getDialogPane().setContent(content);
        dialog.setOnCloseRequest(e -> { if (importBusy || cancel.isDisabled()) e.consume(); });
        run.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume();
            if (importBusy || selected[0].hasErrors()) return;
            importBusy = true; btnImport.setDisable(true); run.setDisable(true); mode.setDisable(true); cancel.setDisable(true);
            Task<InventoryImportService.Plan> task = new Task<>() {
                @Override protected InventoryImportService.Plan call() {
                    return service.execute(selected[0], (done, total) -> updateProgress(done, total));
                }
            };
            progress.progressProperty().bind(task.progressProperty());
            task.setOnSucceeded(e -> {
                importBusy = false; btnImport.setDisable(false); cancel.setDisable(false);
                dialog.close();
                loadProducts();
                showImportResult(task.getValue());
            });
            task.setOnFailed(e -> {
                importBusy = false; btnImport.setDisable(false); cancel.setDisable(false); mode.setDisable(false);
                progress.progressProperty().unbind(); progress.setProgress(0);
                detail.setText(task.getException().getMessage() + "\nVuelva a seleccionar el modo para actualizar la vista previa.");
                summary.setText("Importación cancelada; no se guardaron cambios.");
                summary.setStyle("-fx-text-fill: #D32F2F;");
            });
            Thread thread = new Thread(task, "inventory-import"); thread.setDaemon(true); thread.start();
        });
        dialog.showAndWait();
    }

    private void showImportResult(InventoryImportService.Plan plan) {
        Dialog<ButtonType> result = new Dialog<>();
        result.setTitle("Resultado de importación");
        result.setResizable(true);
        if (tblProducts.getScene() != null) result.initOwner(tblProducts.getScene().getWindow());
        result.getDialogPane().getButtonTypes().add(new ButtonType("Cerrar", ButtonBar.ButtonData.OK_DONE));
        Label title = new Label("Importación completada");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #237443;");
        Label subtitle = new Label(plan.rows.size() + " filas procesadas · " + plan.mode);
        subtitle.setStyle("-fx-font-size: 13px; -fx-text-fill: #555555;");
        javafx.scene.layout.HBox counters = new javafx.scene.layout.HBox(12);
        String[] actions = {"Crear", "Actualizar", "Omitir", "Error"};
        String[] captions = {"Creados", "Actualizados", "Omitidos", "Errores"};
        String[] colors = {"#237443", "#1565C0", "#8A5700", "#C62828"};
        for (int i = 0; i < actions.length; i++) {
            Label count = new Label(String.valueOf(plan.count(actions[i])));
            count.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: " + colors[i] + ";");
            Label caption = new Label(captions[i]);
            caption.setStyle("-fx-font-size: 13px; -fx-text-fill: #444444;");
            javafx.scene.layout.VBox card = new javafx.scene.layout.VBox(4, count, caption);
            card.setStyle("-fx-background-color: #F5F7FA; -fx-background-radius: 8; -fx-padding: 12 18;");
            card.setMaxWidth(Double.MAX_VALUE);
            javafx.scene.layout.HBox.setHgrow(card, javafx.scene.layout.Priority.ALWAYS);
            counters.getChildren().add(card);
        }
        TableView<InventoryImportService.PreviewRow> table = new TableView<>();
        table.setItems(FXCollections.observableArrayList(plan.rows));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setFixedCellSize(34);
        table.setPrefHeight(32 + 34 * Math.max(1, Math.min(plan.rows.size(), 8)));
        String[] headers = {"Fila", "Código", "Producto", "Resultado", "Detalle"};
        double[] widths = {55, 135, 210, 115, 290};
        for (int i = 0; i < headers.length; i++) {
            final int index = i;
            TableColumn<InventoryImportService.PreviewRow, String> column = new TableColumn<>(headers[i]);
            column.setPrefWidth(widths[i]);
            column.setCellValueFactory(c -> {
                var row = c.getValue();
                String value = switch (index) {
                    case 0 -> String.valueOf(row.input.row);
                    case 1 -> row.input.code.isBlank() ? "Generado automáticamente" : row.input.code;
                    case 2 -> row.input.name;
                    case 3 -> switch (row.action) {
                        case "Crear" -> "Creado";
                        case "Actualizar" -> "Actualizado";
                        case "Omitir" -> "Omitido";
                        default -> row.action;
                    };
                    default -> row.detail;
                };
                return new SimpleStringProperty(value);
            });
            column.setCellFactory(col -> new TableCell<>() {
                @Override protected void updateItem(String value, boolean empty) {
                    super.updateItem(value, empty);
                    setText(empty ? null : value);
                    setTooltip(empty || value == null ? null : new Tooltip(value));
                }
            });
            table.getColumns().add(column);
        }
        TextArea detail = new TextArea();
        detail.setEditable(false);
        detail.setWrapText(true);
        detail.setPrefRowCount(2);
        detail.setAccessibleText("Detalle del resultado seleccionado");
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, row) ->
            detail.setText(row == null ? "" : "Fila " + row.input.row + " · " + row.input.name + "\n" + row.detail));
        if (!plan.rows.isEmpty()) table.getSelectionModel().selectFirst();
        javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(12, title, subtitle, counters,
            new Label("Resultado por producto"), table, detail);
        content.setPadding(new javafx.geometry.Insets(8));
        javafx.scene.layout.VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);
        result.getDialogPane().setContent(content);
        result.getDialogPane().setPrefWidth(880);
        result.showAndWait();
    }

    private void openExportDialog() {
        // Validar rol admin/root por seguridad extra
        if (!PermissionHelper.isAdminOrRoot()) {
            PermissionHelper.showAccessDenied("INVENTARIO", "EXPORT");
            return;
        }

        ChoiceDialog<String> dialog = new ChoiceDialog<>("CSV", FXCollections.observableArrayList("CSV", "Excel"));
        dialog.setTitle("Exportar inventario");
        dialog.setHeaderText("Seleccione el formato de exportación");
        dialog.setContentText("Formato:");

        Optional<String> choice = dialog.showAndWait();
        if (choice.isEmpty()) return;
        String format = choice.get();

        if ("CSV".equalsIgnoreCase(format)) {
            exportInventoryCsv();
        } else {
            exportInventoryExcel();
        }
    }

    private String timestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    }

    private void exportInventoryCsv() {
        com.joshuamc1.sicam.core.Authorization.requireAdminOrRoot();
        com.joshuamc1.sicam.core.Authorization.require("INVENTARIO","READ");
        // Cargar datos activos
        List<Product> products = productService.findAll();
        if (products == null || products.isEmpty()) {
            Utils.showAlert("Info", "No hay productos para exportar");
            return;
        }

        FileChooser fc = new FileChooser();
        fc.setTitle("Exportar Inventario - CSV");
        fc.setInitialFileName("inventario_" + timestamp() + ".csv");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File out = fc.showSaveDialog(tblProducts.getScene().getWindow());
        if (out == null) return;

        // Columnas: code,name,description,unit,current_stock,minimum_stock,status
        try (PrintWriter pw = new PrintWriter(out, StandardCharsets.UTF_8)) {
            pw.println("code,name,description,unit,current_stock,minimum_stock,status");
            for (Product p : products) {
                String code = nullToEmpty(p.getString("code"));
                String name = nullToEmpty(p.getString("name"));
                String desc = nullToEmpty(p.getString("description"));
                String unit = nullToEmpty(p.getString("unit")); // valor DB, el importador lo espera así
                String current = String.valueOf(p.getDouble("current_stock") == null ? 0d : p.getDouble("current_stock"));
                String min = String.valueOf(p.getDouble("minimum_stock") == null ? 0d : p.getDouble("minimum_stock"));
                String status = nullToEmpty(p.getString("status"));

                pw.println(String.join(",",
                    quoteCsv(code),
                    quoteCsv(name),
                    quoteCsv(desc),
                    quoteCsv(unit),
                    quoteCsv(current),
                    quoteCsv(min),
                    quoteCsv(status)
                ));
            }
        } catch (Exception ex) {
            Logs.error(getClass(), "Error exportando inventario CSV", ex);
            Utils.showAlert("Error", "No se pudo exportar CSV: " + ex.getMessage());
            return;
        }

        Utils.showAlert("Éxito", "Inventario exportado a CSV correctamente");
    }

    private void exportInventoryExcel() {
        com.joshuamc1.sicam.core.Authorization.requireAdminOrRoot();
        com.joshuamc1.sicam.core.Authorization.require("INVENTARIO","READ");
        // Cargar datos activos
        List<Product> products = productService.findAll();
        if (products == null || products.isEmpty()) {
            Utils.showAlert("Info", "No hay productos para exportar");
            return;
        }

        FileChooser fc = new FileChooser();
        fc.setTitle("Exportar Inventario - Excel");
        fc.setInitialFileName("inventario_" + timestamp() + ".xlsx");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel (*.xlsx)", "*.xlsx"));
        File out = fc.showSaveDialog(tblProducts.getScene().getWindow());
        if (out == null) return;

        try (XSSFWorkbook wb = new XSSFWorkbook(); FileOutputStream fos = new FileOutputStream(out)) {
            Sheet sheet = wb.createSheet("Inventario");
            // Header
            Row header = sheet.createRow(0);
            String[] cols = {"code","name","description","unit","current_stock","minimum_stock","status"};
            for (int i = 0; i < cols.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(cols[i]);
            }

            int r = 1;
            for (Product p : products) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(safe(p.getString("code")));
                row.createCell(1).setCellValue(safe(p.getString("name")));
                row.createCell(2).setCellValue(safe(p.getString("description")));
                row.createCell(3).setCellValue(safe(p.getString("unit"))); // valor DB
                row.createCell(4).setCellValue(p.getDouble("current_stock") == null ? 0d : p.getDouble("current_stock"));
                row.createCell(5).setCellValue(p.getDouble("minimum_stock") == null ? 0d : p.getDouble("minimum_stock"));
                row.createCell(6).setCellValue(safe(p.getString("status")));
            }

            for (int i = 0; i < cols.length; i++) sheet.autoSizeColumn(i);
            wb.write(fos);
        } catch (Exception ex) {
            Logs.error(getClass(), "Error exportando inventario Excel", ex);
            Utils.showAlert("Error", "No se pudo exportar Excel: " + ex.getMessage());
            return;
        }

        Utils.showAlert("Éxito", "Inventario exportado a Excel correctamente");
    }

    private String quoteCsv(String s) {
        if (s == null) return "";
        String v = s;
        boolean needQuotes = v.contains(",") || v.contains("\n") || v.contains("\r") || v.contains("\"") || v.contains(";");
        v = v.replace("\"", "\"\"");
        return needQuotes ? "\"" + v + "\"" : v;
    }

    private String nullToEmpty(String s) { return s == null ? "" : s; }
    private String safe(String s) { return s == null ? "" : s; }
}
