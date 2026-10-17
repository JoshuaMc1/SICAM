package com.joshuamc1.sicam.controllers.dashboard;

import com.joshuamc1.sicam.core.AsyncLoadable;
import com.joshuamc1.sicam.core.DatabaseManager;
import com.joshuamc1.sicam.core.Logs;
import com.joshuamc1.sicam.core.PermissionHelper;
import com.joshuamc1.sicam.core.Session;
import com.joshuamc1.sicam.models.Product;
import com.joshuamc1.sicam.models.Transaction;
import com.joshuamc1.sicam.models.User;
import com.joshuamc1.sicam.services.ProductService;
import com.joshuamc1.sicam.services.TransactionService;
import com.joshuamc1.sicam.services.UserService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class HomeController implements AsyncLoadable {
    // KPIs
    @FXML private Label lblTotalProductos;
    @FXML private Label lblBajoStock;
    @FXML private Label lblSinStock;
    @FXML private Label lblPrestamosActivos;
    @FXML private Label lblTransaccionesMes;
    @FXML private Label lblTrendMes;

    // Información de contexto
    @FXML private Label lblUltimaActualizacion;
    @FXML private Label lblEstadoSistema;
    @FXML private Button btnRefresh;

    // Gráficos
    @FXML private BarChart<String, Number> barStock;
    @FXML private PieChart pieStock;
    @FXML private LineChart<String, Number> lineMovimientos;
    @FXML private BarChart<String, Number> barMasMovidos;
    @FXML private PieChart pieTipoTransaccion;
    @FXML private BarChart<String, Number> barActividadSemanal;
    @FXML private BarChart<String, Number> barTendenciaTrimestral;

    // Comparativas
    @FXML private Label lblCompTransacciones;
    @FXML private Label lblCompTransaccionesTrend;
    @FXML private Label lblCompProductos;
    @FXML private Label lblCompProductosTrend;
    @FXML private Label lblCompIngresos;
    @FXML private Label lblCompIngresosTrend;

    // Tabla
    @FXML private TableView<Transaction> tblTransacciones;
    @FXML private TableColumn<Transaction, String> colFecha;
    @FXML private TableColumn<Transaction, String> colProducto;
    @FXML private TableColumn<Transaction, String> colTipo;
    @FXML private TableColumn<Transaction, String> colCantidad; // Cambiado a String para formato personalizado
    @FXML private TableColumn<Transaction, String> colUsuario;

    private final ProductService productService = new ProductService();
    private final TransactionService transactionService = new TransactionService();
    private final UserService userService = new UserService();

    private final AtomicBoolean loading = new AtomicBoolean(false);
    private final AtomicReference<Runnable> onLoaded = new AtomicReference<>(null);

    // Caché temporal para evitar queries duplicadas al renderizar tabla
    private final Map<Integer, List<com.joshuamc1.sicam.models.TransactionItem>> itemsCache = new HashMap<>();

    // Auto-refresh del dashboard
    private ScheduledExecutorService refreshScheduler;
    private static final int AUTO_REFRESH_MINUTES = 5;

    @FXML
    public void initialize() {
        btnRefresh.setOnAction(e -> loadDashboardAsync());
        setupTableColumns();
        configurePermissions();
        setupAutoRefresh();
        loadDashboardAsync();
    }

    /**
     * Configura la visibilidad de elementos según los permisos del usuario
     */
    private void configurePermissions() {
        User currentUser = Session.getCurrentUser();
        if (currentUser == null) {
            Logs.warn(getClass(), "No hay usuario en sesión para configurar permisos");
            return;
        }

        Logs.info(getClass(), "Configurando permisos del dashboard para: {}", currentUser.getString("username"));

        // Verificar acceso general al dashboard
        // Si tiene DASHBOARD.READ, puede ver todas las estadísticas (solo observación)
        boolean canViewDashboard = PermissionHelper.canAccess("DASHBOARD", "READ");

        if (!canViewDashboard) {
            Logs.warn(getClass(), "Usuario sin permiso DASHBOARD.READ - acceso denegado");
            // Ocultar todo el dashboard
            hideAllWidgets();
            return;
        }

        // Con DASHBOARD.READ puede ver todas las estadísticas
        Logs.info(getClass(), "Usuario con DASHBOARD.READ - mostrando todas las estadísticas");
    }

    /**
     * Configura el auto-refresh del dashboard cada X minutos.
     */
    private void setupAutoRefresh() {
        refreshScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "dashboard-auto-refresh");
            t.setDaemon(true);
            return t;
        });

        // Programar actualización automática
        refreshScheduler.scheduleAtFixedRate(
            () -> {
                if (!loading.get()) {
                    Platform.runLater(this::loadDashboardAsync);
                }
            },
            AUTO_REFRESH_MINUTES,
            AUTO_REFRESH_MINUTES,
            TimeUnit.MINUTES
        );

        Logs.info(HomeController.class, "Auto-refresh configurado cada {} minutos", AUTO_REFRESH_MINUTES);
    }

    /**
     * Detiene el auto-refresh y libera recursos.
     * Debe llamarse cuando se cierra el controller.
     */
    public void shutdown() {
        if (refreshScheduler != null && !refreshScheduler.isShutdown()) {
            refreshScheduler.shutdown();
            try {
                if (!refreshScheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                    refreshScheduler.shutdownNow();
                }
                Logs.info(HomeController.class, "Auto-refresh detenido correctamente");
            } catch (InterruptedException e) {
                refreshScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        clearItemsCache();
    }

    /**
     * Oculta todos los widgets del dashboard
     */
    private void hideAllWidgets() {
        hideInventoryWidgets();
        hideTransactionWidgets();
        Logs.info(getClass(), "Todos los widgets del dashboard ocultos");
    }

    /**
     * Oculta widgets relacionados con inventario
     */
    private void hideInventoryWidgets() {
        if (lblTotalProductos != null) {
            lblTotalProductos.getParent().setVisible(false);
            lblTotalProductos.getParent().setManaged(false);
        }
        if (lblBajoStock != null) {
            lblBajoStock.getParent().setVisible(false);
            lblBajoStock.getParent().setManaged(false);
        }
        if (lblSinStock != null) {
            lblSinStock.getParent().setVisible(false);
            lblSinStock.getParent().setManaged(false);
        }
        if (barStock != null) {
            barStock.setVisible(false);
            barStock.setManaged(false);
        }
        if (pieStock != null) {
            pieStock.setVisible(false);
            pieStock.setManaged(false);
        }
        if (barMasMovidos != null) {
            barMasMovidos.setVisible(false);
            barMasMovidos.setManaged(false);
        }
        Logs.info(getClass(), "Widgets de inventario ocultos");
    }

    /**
     * Oculta widgets relacionados con transacciones
     */
    private void hideTransactionWidgets() {
        if (lblTransaccionesMes != null) {
            lblTransaccionesMes.getParent().setVisible(false);
            lblTransaccionesMes.getParent().setManaged(false);
        }
        if (lblPrestamosActivos != null) {
            lblPrestamosActivos.getParent().setVisible(false);
            lblPrestamosActivos.getParent().setManaged(false);
        }
        if (lineMovimientos != null) {
            lineMovimientos.setVisible(false);
            lineMovimientos.setManaged(false);
        }
        if (pieTipoTransaccion != null) {
            pieTipoTransaccion.setVisible(false);
            pieTipoTransaccion.setManaged(false);
        }
        if (barActividadSemanal != null) {
            barActividadSemanal.setVisible(false);
            barActividadSemanal.setManaged(false);
        }
        if (tblTransacciones != null) {
            tblTransacciones.setVisible(false);
            tblTransacciones.setManaged(false);
        }
        Logs.info(getClass(), "Widgets de transacciones ocultos");
    }

    @Override
    public boolean isLoading() {
        return loading.get();
    }

    @Override
    public void setOnLoaded(Runnable callback) {
        this.onLoaded.set(callback);
    }

    private void notifyLoaded() {
        Runnable cb = onLoaded.getAndSet(null);
        if (cb != null) {
            try {
                cb.run();
            } catch (Exception e) {
                Logs.error(HomeController.class, "onLoaded callback failed: {}", e.getMessage());
            }
        }
    }

    /**
     * Obtiene los items de una transacción con caché para evitar queries duplicadas.
     *
     * @param transactionId ID de la transacción
     * @return Lista de items de la transacción
     */
    private List<com.joshuamc1.sicam.models.TransactionItem> getTransactionItemsCached(Integer transactionId) {
        if (transactionId == null) {
            return Collections.emptyList();
        }
        return itemsCache.computeIfAbsent(transactionId,
            id -> transactionService.getTransactionItems(id));
    }

    /**
     * Limpia el caché de transaction items.
     * Debe llamarse después de actualizar la tabla para liberar memoria.
     */
    private void clearItemsCache() {
        itemsCache.clear();
    }

    private void setupTableColumns() {
        // Fecha de creación de la transacción
        colFecha.setCellValueFactory(c -> {
            String createdAt = c.getValue().getString("created_at");
            if (createdAt != null && createdAt.length() >= 19) {
                // Formato: 2025-11-28 14:30:00 -> 28/11/2025 14:30
                try {
                    LocalDateTime dateTime = LocalDateTime.parse(createdAt, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    return new SimpleStringProperty(dateTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
                } catch (Exception e) {
                    return new SimpleStringProperty(createdAt.substring(0, 16));
                }
            }
            return new SimpleStringProperty("N/A");
        });

        // Productos de la transacción (multi-producto)
        colProducto.setCellValueFactory(c -> {
            Transaction transaction = c.getValue();
            Integer totalItems = transaction.getInteger("total_items");

            if (totalItems == null || totalItems == 0) {
                return new SimpleStringProperty("Sin productos");
            }

            // Obtener items con caché
            var items = getTransactionItemsCached(transaction.getInteger("id"));
            if (items.isEmpty()) {
                return new SimpleStringProperty("Sin productos");
            }

            if (items.size() == 1) {
                var item = items.get(0);
                var product = item.parent(Product.class);
                String productName = product != null ? product.getString("name") : "N/A";
                return new SimpleStringProperty(productName);
            }

            // Multi-producto
            return new SimpleStringProperty("Multi-Producto (" + items.size() + " items)");
        });

        // Tipo de transacción
        colTipo.setCellValueFactory(c -> {
            String type = c.getValue().getString("type");
            String displayType = switch (type) {
                case "in" -> "Ingreso";
                case "out" -> "Egreso";
                case "loan" -> "Préstamo";
                default -> type;
            };
            return new SimpleStringProperty(displayType);
        });

        // Cantidad total de la transacción
        colCantidad.setCellValueFactory(c -> {
            Transaction transaction = c.getValue();

            // Calcular suma total de cantidades desde los items (con caché)
            var items = getTransactionItemsCached(transaction.getInteger("id"));
            double total = items.stream()
                .mapToDouble(item -> {
                    Double qty = item.getDouble("quantity");
                    return qty != null ? qty : 0.0;
                })
                .sum();

            // Formatear: sin decimales si es entero, con decimales si no
            String formatted = total % 1.0 == 0.0 ?
                String.format("%.0f", total) :
                String.format("%.2f", total);

            return new SimpleStringProperty(formatted);
        });

        // Usuario que registró la transacción
        colUsuario.setCellValueFactory(c -> {
            User u = c.getValue().parent(User.class);
            return new SimpleStringProperty(u != null ? u.getString("name") : "N/A");
        });

        // Estilos
        colFecha.setStyle("-fx-font-size: 14;");
        colProducto.setStyle("-fx-font-size: 14;");
        colTipo.setStyle("-fx-font-size: 14;");
        colCantidad.setStyle("-fx-font-size: 14;");
        colUsuario.setStyle("-fx-font-size: 14;");
    }

    private void loadDashboardAsync() {
        if (!loading.compareAndSet(false, true)) {
            Logs.info(HomeController.class, "Dashboard load already in progress, skipping");
            return;
        }

        Task<DashboardData> task = new Task<>() {
            @Override
            protected @Nullable DashboardData call() {
                try {
                    // Envolver TODA la lógica (incluida la verificación de permisos) dentro de la conexión
                    return DatabaseManager.callInConnection(() -> {
                        // Si tiene DASHBOARD.READ, puede ver todas las estadísticas
                        boolean canViewDashboard = PermissionHelper.canAccess("DASHBOARD", "READ");

                        if (!canViewDashboard) {
                            Logs.warn(HomeController.class, "Usuario sin DASHBOARD.READ - no se cargan datos");
                            return new DashboardData(); // Retorna datos vacíos
                        }

                        DashboardData data = new DashboardData();

                        Logs.info(HomeController.class, "Cargando datos completos del dashboard");

                        // KPIs básicos de inventario
                        data.totalProducts = productService.countAll();
                        data.lowStock = productService.countLowStock();
                        data.outOfStock = productService.countOutOfStock();
                        data.products = productService.findAll();
                        data.newProductsThisMonth = productService.countCreatedThisMonth();
                        data.newProductsLastMonth = productService.countCreatedLastMonth();

                        // KPIs de transacciones
                        data.activeLoans = transactionService.countActiveLoans();
                        data.transactionsThisMonth = transactionService.countThisMonth();
                        data.transactionsLastMonth = transactionService.countLastMonth();
                        data.latestTransactions = transactionService.findLatest(15);
                        data.last30DaysTransactions = transactionService.findLast30Days();
                        data.mostMovedProducts = transactionService.getMostMovedProducts(10);
                        data.transactionsByType = transactionService.getTransactionsByType();
                        data.weeklyActivity = transactionService.getWeeklyActivity();

                        // Comparaciones mensuales por tipo de transacción
                        data.incomesThisMonth = transactionService.countByTypeThisMonth("in");
                        data.incomesLastMonth = transactionService.countByTypeLastMonth("in");
                        data.outcomesThisMonth = transactionService.countByTypeThisMonth("out");
                        data.outcomesLastMonth = transactionService.countByTypeLastMonth("out");
                        data.loansThisMonth = transactionService.countByTypeThisMonth("loan");
                        data.loansLastMonth = transactionService.countByTypeLastMonth("loan");

                        data.quarterlyTrend = transactionService.getQuarterlyTrend();

                        Logs.info(HomeController.class, "Dashboard data loaded successfully");

                        return data;
                    });
                } catch (Exception e) {
                    Logs.error(HomeController.class, "Error loading dashboard data: {}", e.getMessage());
                    throw e;
                }
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    try {
                        DashboardData data = getValue();
                        updateKPIs(data);
                        updateCharts(data);
                        updateTable(data.latestTransactions);
                        updateContextInfo();
                    } finally {
                        loading.set(false);
                        notifyLoaded();
                    }
                });
            }

            @Override
            protected void failed() {
                loading.set(false);
                Platform.runLater(() -> {
                    Throwable e = getException();
                    Logs.error(HomeController.class, "Dashboard load failed: {}", e == null ? "unknown" : e.getMessage());
                    lblEstadoSistema.setText("● Error de carga");
                    lblEstadoSistema.setStyle("-fx-font-size: 12; -fx-text-fill: #e74c3c;");
                    notifyLoaded();
                });
            }
        };

        Thread th = new Thread(task, "dashboard-loader");
        th.setDaemon(true);
        th.start();
    }

    private void updateKPIs(DashboardData data) {
        // KPIs principales
        lblTotalProductos.setText(String.valueOf(data.totalProducts));
        lblBajoStock.setText(String.valueOf(data.lowStock));
        lblSinStock.setText(String.valueOf(data.outOfStock));
        lblPrestamosActivos.setText(String.valueOf(data.activeLoans));
        lblTransaccionesMes.setText(String.valueOf(data.transactionsThisMonth));

        // === COMPARACIÓN 1: Transacciones Mes Actual vs Mes Anterior ===
        if (data.transactionsLastMonth > 0) {
            double changePercent = ((data.transactionsThisMonth - data.transactionsLastMonth) / (double) data.transactionsLastMonth) * 100;
            long difference = data.transactionsThisMonth - data.transactionsLastMonth;

            String trendText;
            String trendStyle;

            if (changePercent > 0) {
                trendText = String.format("↑ %.1f%% (+%d)", changePercent, difference);
                trendStyle = "-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: #27ae60;";
            } else if (changePercent < 0) {
                trendText = String.format("↓ %.1f%% (%d)", Math.abs(changePercent), difference);
                trendStyle = "-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: #e74c3c;";
            } else {
                trendText = "→ 0% (sin cambios)";
                trendStyle = "-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: #95a5a6;";
            }

            lblTrendMes.setText(trendText);
            lblTrendMes.setStyle(trendStyle);
        } else if (data.transactionsThisMonth > 0) {
            // Primer mes con transacciones
            lblTrendMes.setText(String.format("↑ Nuevo (%d)", data.transactionsThisMonth));
            lblTrendMes.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: #3498db;");
        } else {
            // Sin transacciones en ambos meses
            lblTrendMes.setText("→ Sin datos");
            lblTrendMes.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: #95a5a6;");
        }

        // === COMPARACIÓN 2: Total de Transacciones (desglosado) ===
        if (lblCompTransacciones != null) {
            long totalThisMonth = data.incomesThisMonth + data.outcomesThisMonth + data.loansThisMonth;
            lblCompTransacciones.setText(String.format("%d (I:%d E:%d P:%d)",
                totalThisMonth,
                data.incomesThisMonth,
                data.outcomesThisMonth,
                data.loansThisMonth));

            if (lblCompTransaccionesTrend != null) {
                long totalLastMonth = data.incomesLastMonth + data.outcomesLastMonth + data.loansLastMonth;
                updateComparisonLabel(lblCompTransaccionesTrend, totalThisMonth, totalLastMonth);
            }
        }

        // === COMPARACIÓN 3: Productos Nuevos ===
        if (lblCompProductos != null) {
            lblCompProductos.setText(String.valueOf(data.newProductsThisMonth));

            if (lblCompProductosTrend != null) {
                updateComparisonLabel(lblCompProductosTrend, data.newProductsThisMonth, data.newProductsLastMonth);
            }
        }

        // === COMPARACIÓN 4: Ingresos ===
        if (lblCompIngresos != null) {
            lblCompIngresos.setText(String.valueOf(data.incomesThisMonth));

            if (lblCompIngresosTrend != null) {
                updateComparisonLabel(lblCompIngresosTrend, data.incomesThisMonth, data.incomesLastMonth);
            }
        }
    }

    /**
     * Actualiza un label de comparación con formato y colores apropiados.
     *
     * @param label Label a actualizar
     * @param currentValue Valor del mes actual
     * @param previousValue Valor del mes anterior
     */
    private void updateComparisonLabel(Label label, long currentValue, long previousValue) {
        if (previousValue > 0) {
            double changePercent = ((currentValue - previousValue) / (double) previousValue) * 100;
            long difference = currentValue - previousValue;

            String text;
            String style;

            if (changePercent > 5) {
                // Incremento significativo (>5%)
                text = String.format("↑ %.1f%% (+%d)", changePercent, difference);
                style = "-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #27ae60;";
            } else if (changePercent > 0) {
                // Incremento leve (0-5%)
                text = String.format("↑ %.1f%% (+%d)", changePercent, difference);
                style = "-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #2ecc71;";
            } else if (changePercent < -5) {
                // Decremento significativo (<-5%)
                text = String.format("↓ %.1f%% (%d)", Math.abs(changePercent), difference);
                style = "-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #e74c3c;";
            } else if (changePercent < 0) {
                // Decremento leve (0 a -5%)
                text = String.format("↓ %.1f%% (%d)", Math.abs(changePercent), difference);
                style = "-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #e67e22;";
            } else {
                // Sin cambios (0%)
                text = "→ 0%";
                style = "-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #95a5a6;";
            }

            label.setText(text);
            label.setStyle(style);
        } else if (currentValue > 0) {
            // Primer mes con datos
            label.setText(String.format("↑ Nuevo (%d)", currentValue));
            label.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #3498db;");
        } else {
            // Sin datos en ambos meses
            label.setText("→ Sin datos");
            label.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #bdc3c7;");
        }
    }

    private void updateCharts(@NotNull DashboardData data) {
        // 1. BarChart: Top 10 Stock (productos activos)
        XYChart.Series<String, Number> stockSeries = new XYChart.Series<>();
        stockSeries.setName("Stock Actual");

        data.products.stream()
            .filter(this::isProductActive) // Solo productos activos
            .sorted((p1, p2) -> {
                Double s1 = p1.getDouble("current_stock");
                Double s2 = p2.getDouble("current_stock");
                if (s1 == null) s1 = 0.0;
                if (s2 == null) s2 = 0.0;
                return Double.compare(s2, s1);
            })
            .limit(10)
            .forEach(p -> {
                String name = p.getString("name");
                Double stock = p.getDouble("current_stock");
                if (name != null && stock != null) {
                    String displayName = name.length() > 15 ? name.substring(0, 12) + "..." : name;
                    stockSeries.getData().add(new XYChart.Data<>(displayName, stock));
                }
            });
        barStock.getData().clear();
        barStock.getData().add(stockSeries);

        // 2. PieChart: Distribución de Stock
        long activeProducts = data.products.stream()
            .filter(this::isProductActive)
            .count();
        long normalStock = Math.max(0, activeProducts - data.lowStock - data.outOfStock);

        pieStock.setData(FXCollections.observableArrayList(
            new PieChart.Data("Stock Normal", normalStock),
            new PieChart.Data("Stock Bajo", data.lowStock),
            new PieChart.Data("Sin Stock", data.outOfStock)
        ));

        // 3. LineChart: Movimientos últimos 30 días
        lineMovimientos.getData().clear();
        XYChart.Series<String, Number> ingresosSeries = new XYChart.Series<>();
        ingresosSeries.setName("Ingresos");
        XYChart.Series<String, Number> egresosSeries = new XYChart.Series<>();
        egresosSeries.setName("Egresos");
        XYChart.Series<String, Number> prestamosSeries = new XYChart.Series<>();
        prestamosSeries.setName("Préstamos");

        Map<String, Map<String, Long>> dailyData = groupTransactionsByDate(data.last30DaysTransactions);
        dailyData.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                String date = entry.getKey();
                Map<String, Long> types = entry.getValue();
                ingresosSeries.getData().add(new XYChart.Data<>(date, types.getOrDefault("in", 0L)));
                egresosSeries.getData().add(new XYChart.Data<>(date, types.getOrDefault("out", 0L)));
                prestamosSeries.getData().add(new XYChart.Data<>(date, types.getOrDefault("loan", 0L)));
            });

        lineMovimientos.getData().addAll(ingresosSeries, egresosSeries, prestamosSeries);

        // 4. BarChart: Productos Más Movidos (desde transaction_items)
        XYChart.Series<String, Number> movidosSeries = new XYChart.Series<>();
        movidosSeries.setName("Transacciones");

        if (data.mostMovedProducts != null && !data.mostMovedProducts.isEmpty()) {
            data.mostMovedProducts.forEach((productName, count) -> {
                String displayName = productName.length() > 15 ? productName.substring(0, 12) + "..." : productName;
                movidosSeries.getData().add(new XYChart.Data<>(displayName, count));
            });
        }
        barMasMovidos.getData().clear();
        barMasMovidos.getData().add(movidosSeries);

        // 5. PieChart: Distribución por Tipo de Transacción
        if (data.transactionsByType != null && !data.transactionsByType.isEmpty()) {
            pieTipoTransaccion.setData(FXCollections.observableArrayList(
                data.transactionsByType.entrySet().stream()
                    .map(e -> {
                        String displayName = switch (e.getKey()) {
                            case "in" -> "Ingresos";
                            case "out" -> "Egresos";
                            case "loan" -> "Préstamos";
                            default -> e.getKey();
                        };
                        return new PieChart.Data(displayName, e.getValue());
                    })
                    .collect(java.util.stream.Collectors.toList())
            ));
        } else {
            pieTipoTransaccion.setData(FXCollections.observableArrayList());
        }

        // 6. BarChart: Actividad Semanal
        XYChart.Series<String, Number> weeklySeries = new XYChart.Series<>();
        weeklySeries.setName("Transacciones");
        String[] dias = {"Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"};
        for (int i = 0; i < 7; i++) {
            long count = data.weeklyActivity != null ? data.weeklyActivity.getOrDefault(i + 1, 0L) : 0L;
            weeklySeries.getData().add(new XYChart.Data<>(dias[i], count));
        }
        barActividadSemanal.getData().clear();
        barActividadSemanal.getData().add(weeklySeries);

        // 7. BarChart: Tendencia Trimestral
        barTendenciaTrimestral.getData().clear();
        XYChart.Series<String, Number> trimIngresos = new XYChart.Series<>();
        trimIngresos.setName("Ingresos");
        XYChart.Series<String, Number> trimEgresos = new XYChart.Series<>();
        trimEgresos.setName("Egresos");
        XYChart.Series<String, Number> trimPrestamos = new XYChart.Series<>();
        trimPrestamos.setName("Préstamos");

        if (data.quarterlyTrend != null && !data.quarterlyTrend.isEmpty()) {
            data.quarterlyTrend.forEach((month, types) -> {
                trimIngresos.getData().add(new XYChart.Data<>(month, types.getOrDefault("in", 0L)));
                trimEgresos.getData().add(new XYChart.Data<>(month, types.getOrDefault("out", 0L)));
                trimPrestamos.getData().add(new XYChart.Data<>(month, types.getOrDefault("loan", 0L)));
            });
        }

        barTendenciaTrimestral.getData().addAll(trimIngresos, trimEgresos, trimPrestamos);
    }


    private void updateTable(List<Transaction> transactions) {
        // Limpiar caché anterior
        clearItemsCache();
        tblTransacciones.setItems(FXCollections.observableArrayList(transactions));
    }

    private void updateContextInfo() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
        lblUltimaActualizacion.setText("Última actualización: " + timestamp);
        lblEstadoSistema.setText("● Sistema OK");
        lblEstadoSistema.setStyle("-fx-font-size: 12; -fx-text-fill: #27ae60;");
    }

    private @NotNull Map<String, Map<String, Long>> groupTransactionsByDate(List<Transaction> transactions) {
        Map<String, Map<String, Long>> result = new LinkedHashMap<>();

        // Inicializar los últimos 30 días
        for (int i = 29; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            String dateKey = date.format(DateTimeFormatter.ofPattern("dd/MM"));
            result.put(dateKey, new HashMap<>());
        }

        // Agrupar transacciones por fecha
        if (transactions != null && !transactions.isEmpty()) {
            transactions.forEach(t -> {
                String createdAt = t.getString("created_at");
                if (createdAt != null && createdAt.length() >= 10) {
                    try {
                        LocalDate date = LocalDate.parse(createdAt.substring(0, 10));
                        String dateKey = date.format(DateTimeFormatter.ofPattern("dd/MM"));
                        String type = t.getString("type");

                        if (type != null && result.containsKey(dateKey)) {
                            result.get(dateKey).merge(type, 1L, Long::sum);
                        }
                    } catch (Exception e) {
                        Logs.warn(HomeController.class, "Error parsing date: {}", createdAt);
                    }
                }
            });
        }

        return result;
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    /**
     * Verifica si un producto está activo.
     * Maneja tanto valores Number como String para el campo status (soporta ENUM en BD).
     *
     * @param product El producto a verificar
     * @return true si el producto está activo, false en caso contrario
     */
    private boolean isProductActive(Product product) {
        if (product == null) {
            return false;
        }

        try {
            Object statusObj = product.get("status");
            if (statusObj == null) {
                // Si no hay status almacenado, asumimos activo por compatibilidad
                return true;
            }

            // Si viene como número (compatibilidad con migraciones antiguas)
            if (statusObj instanceof Number) {
                int v = ((Number) statusObj).intValue();
                return v == 1;
            }

            // Tratar como cadena (ENUM: 'active','inactive','deleted' o valores '1','true')
            String statusStr = String.valueOf(statusObj).trim().toLowerCase();
            return "1".equals(statusStr) || "active".equals(statusStr) || "true".equals(statusStr);

        } catch (Exception e) {
            Logs.warn(HomeController.class, "Error verificando estado del producto ID {}: {}",
                     product.getId(), e.getMessage());
            // En caso de error, asumimos que está activo para no ocultar datos
            return true;
        }
    }

    // Clase interna para datos del dashboard
    private static class DashboardData {
        // KPIs básicos
        long totalProducts;
        long lowStock;
        long outOfStock;
        long activeLoans;

        // Comparaciones mensuales - Transacciones totales
        long transactionsThisMonth;
        long transactionsLastMonth;

        // Comparaciones mensuales - Productos nuevos
        long newProductsThisMonth;
        long newProductsLastMonth;

        // Comparaciones mensuales - Ingresos
        long incomesThisMonth;
        long incomesLastMonth;

        // Comparaciones mensuales - Egresos
        long outcomesThisMonth;
        long outcomesLastMonth;

        // Comparaciones mensuales - Préstamos
        long loansThisMonth;
        long loansLastMonth;

        // Listas de datos
        List<Product> products;
        List<Transaction> latestTransactions;
        List<Transaction> last30DaysTransactions;

        // Mapas de análisis
        Map<String, Long> mostMovedProducts;
        Map<String, Long> transactionsByType;
        Map<Integer, Long> weeklyActivity;
        Map<String, Map<String, Long>> quarterlyTrend;
    }
}
