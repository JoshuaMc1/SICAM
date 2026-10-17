package com.joshuamc1.sicam.controllers.dashboard;

import com.joshuamc1.sicam.core.BarcodeGenerator;
import com.joshuamc1.sicam.core.Logs;
import com.joshuamc1.sicam.core.PermissionHelper;
import com.joshuamc1.sicam.core.Session;
import com.joshuamc1.sicam.core.Utils;
import com.joshuamc1.sicam.core.enums.Units;
import com.joshuamc1.sicam.models.Product;
import com.joshuamc1.sicam.models.User;
import com.joshuamc1.sicam.services.ProductService;
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
    @FXML private TableColumn<Product, Void> colActions;

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
    private final ObservableList<Product> currentPageItems = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        // Configurar permisos primero
        canRestore = com.joshuamc1.sicam.core.PermissionHelper.canAccess("INVENTARIO", "RESTORE");

        setupTable();
        configurePermissions();
        loadProducts();

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
                    boolean isActive = product.isActive();
                    Label badge = new Label(isActive ? "Activo" : "Inactivo");
                    badge.setStyle(isActive
                            ? "-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-padding: 5 10; -fx-background-radius: 5;"
                            : "-fx-background-color: #9E9E9E; -fx-text-fill: white; -fx-padding: 5 10; -fx-background-radius: 5;");
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
        colActions.setStyle("-fx-font-size: 17;");

        tblProducts.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
    }

    private void loadProducts() {
        Task<List<Product>> task = new Task<>() {
            @Override
            protected java.util.List<Product> call() {
                DatabaseConfig.openConnection();
                try {
                    List<Product> products = productService.findAll();

                    // Si tiene permiso de restaurar, incluir eliminados
                    if (canRestore) {
                        List<Product> deleted = productService.findDeleted();
                        products = new java.util.ArrayList<>(products);
                        products.addAll(deleted);
                    }

                    return products;
                } finally {
                    DatabaseConfig.closeConnection();
                }
            }
        };

        task.setOnSucceeded(evt -> {
            allProducts = task.getValue();
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
        totalItems = filtered.size();
        totalPages = Math.max(1, (int)Math.ceil(totalItems / (double)pageSize));
        if (currentPage > totalPages) currentPage = totalPages;
        int fromIndex = (currentPage - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, totalItems);
        List<Product> pageSlice = fromIndex >= toIndex ? java.util.Collections.emptyList() : filtered.subList(fromIndex, toIndex);
        currentPageItems.setAll(pageSlice);
        tblProducts.setItems(currentPageItems);
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

                Product product = productService.createProduct(name, desc, unit.getDbValue(), current, min);
                // Asignar código si se proporcionó
                if (!code.isEmpty()) {
                    product.set("code", code.trim());
                    productService.save(product);
                }
                return product;
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

                productService.save(sel);

                return sel;
            }
            return null;
        });

        Optional<Product> result = dialog.showAndWait();
        result.ifPresent(p -> loadProducts());
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

        // Verificar si tiene transacciones asociadas
        try {
            Long transactionCount = (Long) org.javalite.activejdbc.Base.firstCell(
                "SELECT COUNT(*) FROM transaction_items WHERE product_id = ?",
                product.getId()
            );

            if (transactionCount != null && transactionCount > 0) {
                Label lblWarning = new Label("⚠️ Este producto tiene " + transactionCount +
                    " transacción(es) asociada(s).\nNo se puede eliminar permanentemente.");
                lblWarning.setStyle("-fx-font-size: 13px; -fx-text-fill: #ff9800; -fx-font-weight: bold;");
                lblWarning.setWrapText(true);
                grid.add(lblWarning, 0, 3);

                // Deshabilitar eliminación permanente
                dialog.getDialogPane().lookupButton(hardDeleteBtn).setDisable(true);
            }
        } catch (Exception e) {
            Logs.warn(getClass(), "No se pudo verificar transacciones asociadas: " + e.getMessage());
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

    private void openImportDialog() {
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

        // Parseo básico y vista previa (placeholder)
        List<Product> previewItems;
        try {
            previewItems = new com.joshuamc1.sicam.services.InventoryImportService().parseFileForPreview(file);
        } catch (Exception ex) {
            Logs.error(getClass(), "Error al parsear archivo: {}", ex.getMessage());
            Utils.showAlert("Error", "No se pudo leer el archivo seleccionado: " + ex.getMessage());
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Vista previa de importación");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefWidth(650);
        dialog.getDialogPane().setPrefHeight(400);
        dialog.setResizable(true);

        TableView<Product> previewTable = new TableView<>();
        TableColumn<Product, String> pCode = new TableColumn<>("Código");
        pCode.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getString("code")));
        TableColumn<Product, String> pName = new TableColumn<>("Nombre");
        pName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getString("name")));
        TableColumn<Product, String> pUnit = new TableColumn<>("Unidad");
        pUnit.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getString("unit")));
        TableColumn<Product, Double> pStock = new TableColumn<>("Stock");
        pStock.setCellValueFactory(c -> new SimpleDoubleProperty(c.getValue().getDouble("current_stock") == null ? 0d : c.getValue().getDouble("current_stock")).asObject());
        previewTable.getColumns().addAll(pCode, pName, pUnit, pStock);
        previewTable.setItems(FXCollections.observableArrayList(previewItems));

        dialog.getDialogPane().setContent(previewTable);
        Optional<ButtonType> res = dialog.showAndWait();
        if (res.isPresent() && res.get() == ButtonType.OK) {
            // Ejecutar importación en background
            Task<Void> task = new Task<>() {
                @Override
                protected Void call() {
                    DatabaseConfig.openConnection();
                    try {
                        new com.joshuamc1.sicam.services.InventoryImportService().importItems(previewItems);
                        return null;
                    } finally {
                        DatabaseConfig.closeConnection();
                    }
                }
            };
            task.setOnSucceeded(ev -> {
                Utils.showAlert("Éxito", "Inventario importado correctamente.");
                loadProducts();
            });
            task.setOnFailed(ev -> {
                Throwable t = task.getException();
                Utils.showAlert("Error", "Falló la importación: " + (t == null ? "unknown" : t.getMessage()));
            });
            Thread th = new Thread(task, "inventory-import");
            th.setDaemon(true);
            th.start();
        }
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
