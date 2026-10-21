package com.joshuamc1.sicam.controllers.dashboard;

import com.joshuamc1.sicam.core.dialog.DateRangeDialog;
import com.joshuamc1.sicam.core.Logs;
import com.joshuamc1.sicam.core.Roles;
import com.joshuamc1.sicam.core.Session;
import com.joshuamc1.sicam.core.dialog.TransactionTypeDialog;
import com.joshuamc1.sicam.core.dialog.UserSelectionDialog;
import com.joshuamc1.sicam.core.dialog.PersonSelectionDialog;
import com.joshuamc1.sicam.core.dialog.MonthYearDialog;
import com.joshuamc1.sicam.models.Report;
import com.joshuamc1.sicam.services.ReportService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDFont;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.concurrent.Task;
import javafx.scene.layout.VBox;
import javafx.application.Platform;
import java.util.concurrent.atomic.AtomicInteger;

public class ReportsController {
    // Botones de Usuarios
    @FXML private Button btnUsersNoRootCsv;
    @FXML private Button btnUsersNoRootPdf;
    @FXML private Button btnUsersGroupedCsv;
    @FXML private Button btnUsersGroupedPdf;

    // Botones de Inventario
    @FXML private Button btnInventoryCompleteCsv;
    @FXML private Button btnInventoryCompletePdf;
    @FXML private Button btnInventoryLowStockCsv;
    @FXML private Button btnInventoryLowStockPdf;
    @FXML private Button btnInventoryOutOfStockCsv;
    @FXML private Button btnInventoryOutOfStockPdf;
    @FXML private Button btnInventoryMovementsCsv;
    @FXML private Button btnInventoryMovementsPdf;
    @FXML private Button btnMovementsByDateCsv;
    @FXML private Button btnMovementsByDatePdf;
    @FXML private Button btnProductsByDateCsv;
    @FXML private Button btnProductsByDatePdf;

    // Botones de Transacciones
    @FXML private Button btnTransactionsAllCsv;
    @FXML private Button btnTransactionsAllPdf;
    @FXML private Button btnTransactionsByTypeCsv;
    @FXML private Button btnTransactionsByTypePdf;
    @FXML private Button btnTransactionsByTypeAndDateCsv;
    @FXML private Button btnTransactionsByTypeAndDatePdf;
    @FXML private Button btnTransactionsByUserCsv;
    @FXML private Button btnTransactionsByUserPdf;
    @FXML private Button btnTransactionsByPersonCsv;
    @FXML private Button btnTransactionsByPersonPdf;

    // Botones de Reportes Analíticos
    @FXML private Button btnProductsMostMovedCsv;
    @FXML private Button btnProductsMostMovedPdf;
    @FXML private Button btnPeopleWithLoansCsv;
    @FXML private Button btnPeopleWithLoansPdf;
    @FXML private Button btnMonthlyConsumptionCsv;
    @FXML private Button btnMonthlyConsumptionPdf;
    @FXML private Button btnProductsNoMovementCsv;
    @FXML private Button btnProductsNoMovementPdf;
    @FXML private Button btnUserActivityCsv;
    @FXML private Button btnUserActivityPdf;
    @FXML private Button btnMonthlyDashboardCsv;
    @FXML private Button btnMonthlyDashboardPdf;

    @FXML private ChoiceBox<String> transactionStatusFilter;
    @FXML private ChoiceBox<String> stockLevelFilter;
    @FXML private CheckBox includeDeleted;

    private String selectedTransactionStatus() {
        String value = transactionStatusFilter.getValue();
        return "Activas".equals(value) ? "active" : "Invalidadas".equals(value) ? "invalidated"
            : "Canceladas".equals(value) ? "cancelled" : null;
    }

    private com.joshuamc1.sicam.core.enums.StockLevel selectedStockLevel() {
        for (com.joshuamc1.sicam.core.enums.StockLevel level : com.joshuamc1.sicam.core.enums.StockLevel.values()) {
            if (level.getLabel().equals(stockLevelFilter.getValue())) return level;
        }
        return null;
    }

    private final ReportService reportService = new ReportService();

    @FXML
    public void initialize() {
        boolean canViewDeleted = com.joshuamc1.sicam.core.PermissionHelper.canAccess("SISTEMA","VIEW_DELETED");
        transactionStatusFilter.getItems().setAll(canViewDeleted ? java.util.List.of("Todas","Activas","Invalidadas","Canceladas") : java.util.List.of("Activas"));
        transactionStatusFilter.setValue(canViewDeleted ? "Todas" : "Activas");
        com.joshuamc1.sicam.core.PermissionHelper.configureVisibleAction(includeDeleted,canViewDeleted);
        stockLevelFilter.getItems().add("Todos");
        for (com.joshuamc1.sicam.core.enums.StockLevel level : com.joshuamc1.sicam.core.enums.StockLevel.values()) {
            stockLevelFilter.getItems().add(level.getLabel());
        }
        stockLevelFilter.setValue("Todos");
        // Reportes de Usuarios sin Root
        btnUsersNoRootCsv.setOnAction(e -> exportUsersExcludingRootCsv());
        btnUsersNoRootPdf.setOnAction(e -> exportUsersExcludingRootPdf());

        // Reportes de Usuarios Agrupados por Rol
        btnUsersGroupedCsv.setOnAction(e -> exportUsersGroupedCsv());
        btnUsersGroupedPdf.setOnAction(e -> exportUsersGroupedPdf());

        // Reportes de Inventario Completo
        btnInventoryCompleteCsv.setOnAction(e -> exportInventoryCompleteCsv());
        btnInventoryCompletePdf.setOnAction(e -> exportInventoryCompletePdf());

        // Reportes de Inventario con Stock Bajo
        btnInventoryLowStockCsv.setOnAction(e -> exportInventoryLowStockCsv());
        btnInventoryLowStockPdf.setOnAction(e -> exportInventoryLowStockPdf());

        // Reportes de Inventario Sin Stock
        btnInventoryOutOfStockCsv.setOnAction(e -> exportInventoryOutOfStockCsv());
        btnInventoryOutOfStockPdf.setOnAction(e -> exportInventoryOutOfStockPdf());

        // Reportes de Movimientos de Inventario
        btnInventoryMovementsCsv.setOnAction(e -> exportInventoryMovementsCsv());
        btnInventoryMovementsPdf.setOnAction(e -> exportInventoryMovementsPdf());

        // Reportes con Rango de Fechas Personalizado
        btnMovementsByDateCsv.setOnAction(e -> exportMovementsByDateRangeCsv());
        btnMovementsByDatePdf.setOnAction(e -> exportMovementsByDateRangePdf());
        btnProductsByDateCsv.setOnAction(e -> exportProductsByDateRangeCsv());
        btnProductsByDatePdf.setOnAction(e -> exportProductsByDateRangePdf());

        // Sección: Transacciones
        btnTransactionsAllCsv.setOnAction(e -> exportTransactionsAllCsv());
        btnTransactionsAllPdf.setOnAction(e -> exportTransactionsAllPdf());
        btnTransactionsByTypeCsv.setOnAction(e -> exportTransactionsByTypeCsv());
        btnTransactionsByTypePdf.setOnAction(e -> exportTransactionsByTypePdf());
        btnTransactionsByTypeAndDateCsv.setOnAction(e -> exportTransactionsByTypeAndDateCsv());
        btnTransactionsByTypeAndDatePdf.setOnAction(e -> exportTransactionsByTypeAndDatePdf());
        btnTransactionsByUserCsv.setOnAction(e -> exportTransactionsByUserCsv());
        btnTransactionsByUserPdf.setOnAction(e -> exportTransactionsByUserPdf());
        btnTransactionsByPersonCsv.setOnAction(e -> exportTransactionsByPersonCsv());
        btnTransactionsByPersonPdf.setOnAction(e -> exportTransactionsByPersonPdf());

        // Reportes Analíticos
        btnProductsMostMovedCsv.setOnAction(e -> exportProductsMostMovedCsv());
        btnProductsMostMovedPdf.setOnAction(e -> exportProductsMostMovedPdf());
        btnPeopleWithLoansCsv.setOnAction(e -> exportPeopleWithLoansCsv());
        btnPeopleWithLoansPdf.setOnAction(e -> exportPeopleWithLoansPdf());
        btnMonthlyConsumptionCsv.setOnAction(e -> exportMonthlyConsumptionCsv());
        btnMonthlyConsumptionPdf.setOnAction(e -> exportMonthlyConsumptionPdf());
        btnProductsNoMovementCsv.setOnAction(e -> exportProductsNoMovementCsv());
        btnProductsNoMovementPdf.setOnAction(e -> exportProductsNoMovementPdf());
        btnUserActivityCsv.setOnAction(e -> exportUserActivityCsv());
        btnUserActivityPdf.setOnAction(e -> exportUserActivityPdf());
        btnMonthlyDashboardCsv.setOnAction(e -> exportMonthlyDashboardCsv());
        btnMonthlyDashboardPdf.setOnAction(e -> exportMonthlyDashboardPdf());

        // Configurar permisos
        configurePermissions();
    }

    private void configurePermissions() {
        for (java.lang.reflect.Field field : getClass().getDeclaredFields()) {
            if (field.getType() != Button.class) continue;
            try {
                Button button = (Button) field.get(this);
                String name=field.getName();
                if (button != null) com.joshuamc1.sicam.core.PermissionHelper.configureVisibleAction(button,canExportReport(name));
            } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
        }
    }

    private boolean canExportReport(String name) {
        var auth = new java.util.ArrayList<String>();
        auth.add("REPORTES.READ"); auth.add("REPORTES.GENERATE"); auth.add("REPORTES.EXPORT");
        if (name.contains("Users") || name.contains("UserActivity") || name.contains("ByUser")) auth.add("USUARIOS.READ");
        if (name.contains("PeopleWithLoans")) { auth.add("PERSONAS.READ"); auth.add("PRESTAMOS.READ"); }
        if (name.contains("ByPerson") || name.contains("UserActivity")) auth.add("PERSONAS.READ");
        if (name.contains("Dashboard")) auth.add("DASHBOARD.READ");
        if (name.contains("Inventory") || name.contains("Products") || name.contains("Movements") || name.contains("Consumption")) auth.add("INVENTARIO.READ");
        if (name.contains("Consumption")) auth.add("EGRESOS.READ");
        if (name.contains("Movements") || name.contains("MostMoved") || name.contains("NoMovement") || name.contains("UserActivity")
                || (name.contains("Transactions") && !name.contains("ByType"))) {
            auth.add("INGRESOS.READ");auth.add("EGRESOS.READ");auth.add("PRESTAMOS.READ");
        }
        if (name.contains("ByType") && !com.joshuamc1.sicam.core.PermissionHelper.canAccess("INGRESOS","READ")
            && !com.joshuamc1.sicam.core.PermissionHelper.canAccess("EGRESOS","READ")
            && !com.joshuamc1.sicam.core.PermissionHelper.canAccess("PRESTAMOS","READ")) return false;
        for(String permission:auth) {
            String[] parts=permission.split("\\.");
            if(!com.joshuamc1.sicam.core.PermissionHelper.canAccess(parts[0],parts[1])) return false;
        }
        return true;
    }

    private boolean validateExport(String buttonName) {
        if (canExportReport(buttonName)) return true;
        configurePermissions();
        com.joshuamc1.sicam.core.PermissionHelper.showAccessDenied("REPORTES","EXPORT");
        return false;
    }

    /**
     * Valida permisos para generar reportes de un módulo específico
     * @param module El módulo (INVENTARIO, USUARIOS, TRANSACCIONES, etc.)
     * @return true si tiene permisos, false en caso contrario
     */
    private boolean validateReportPermission(String module) {
        // Validar primero REPORTES.READ
        if (!com.joshuamc1.sicam.core.PermissionHelper.validateAndShow("REPORTES", "READ")) {
            return false;
        }

        // Validar permiso específico del módulo
        if (!com.joshuamc1.sicam.core.PermissionHelper.canAccess(module, "READ")) {
            com.joshuamc1.sicam.core.Utils.showAlert("Acceso Denegado",
                "No tienes permiso para generar reportes de " + module + ". Se requiere permiso de lectura.");
            return false;
        }

        return true;
    }

    // ==================== USUARIOS SIN ROOT ====================

    private void exportUsersExcludingRootCsv() {
        if (!validateExport("btnUsersNoRootCsv")) return;
        // Validar permisos de reportes y usuarios
        if (!validateReportPermission("USUARIOS")) return;

        try {
            Report report = reportService.usersReport(false, includeDeleted.isSelected());
            FileChooser fc = new FileChooser();
            fc.setTitle("Exportar Usuarios - CSV");
            fc.setInitialFileName("usuarios_" + getTimestamp() + ".csv");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
            File f = fc.showSaveDialog(btnUsersNoRootCsv.getScene().getWindow());
            if (f == null) return;

            exportToCsv(report, f);
            showSuccess("CSV exportado exitosamente: " + f.getName());
        } catch (Exception ex) {
            Logs.error(getClass(), "Error exportando usuarios sin root a CSV", ex);
            showError("No se pudo exportar el reporte: " + ex.getMessage());
        }
    }

    private void exportUsersExcludingRootPdf() {
        if (!validateExport("btnUsersNoRootPdf")) return;
        // Validar permisos de reportes y usuarios
        if (!validateReportPermission("USUARIOS")) return;

        try {
            Report report = reportService.usersReport(false, includeDeleted.isSelected());
            FileChooser fc = new FileChooser();
            fc.setTitle("Exportar Usuarios - PDF");
            fc.setInitialFileName("usuarios_" + getTimestamp() + ".pdf");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
            File f = fc.showSaveDialog(btnUsersNoRootPdf.getScene().getWindow());
            if (f == null) return;

            String description = "Usuarios del sistema, excluyendo las cuentas SYSTEM y las cuentas con rol ROOT.";
            exportToPdfAsync(report, f, description);
        } catch (Exception ex) {
            Logs.error(getClass(), "Error exportando usuarios sin root a PDF", ex);
            showError("No se pudo exportar el reporte: " + ex.getMessage());
        }
    }

    // ==================== USUARIOS AGRUPADOS POR ROL ====================

    private void exportUsersGroupedCsv() {
        if (!validateExport("btnUsersGroupedCsv")) return;
        try {
            Report report = reportService.usersReport(true, includeDeleted.isSelected());
            FileChooser fc = new FileChooser();
            fc.setTitle("Exportar Usuarios Agrupados por Rol - CSV");
            fc.setInitialFileName("usuarios_por_rol_" + getTimestamp() + ".csv");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
            File f = fc.showSaveDialog(btnUsersGroupedCsv.getScene().getWindow());
            if (f == null) return;

            exportToCsv(report, f);
            showSuccess("CSV exportado exitosamente: " + f.getName());
        } catch (Exception ex) {
            Logs.error(getClass(), "Error exportando usuarios agrupados a CSV", ex);
            showError("No se pudo exportar el reporte: " + ex.getMessage());
        }
    }

    private void exportUsersGroupedPdf() {
        if (!validateExport("btnUsersGroupedPdf")) return;
        try {
            Report report = reportService.usersReport(true, includeDeleted.isSelected());
            FileChooser fc = new FileChooser();
            fc.setTitle("Exportar Usuarios Agrupados por Rol - PDF");
            fc.setInitialFileName("usuarios_por_rol_" + getTimestamp() + ".pdf");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
            File f = fc.showSaveDialog(btnUsersGroupedPdf.getScene().getWindow());
            if (f == null) return;

            String description = "Reporte de usuarios organizados y agrupados por su rol asignado en el sistema.";
            exportToPdfAsync(report, f, description);
        } catch (Exception ex) {
            Logs.error(getClass(), "Error exportando usuarios agrupados a PDF", ex);
            showError("No se pudo exportar el reporte: " + ex.getMessage());
        }
    }

    // ==================== INVENTARIO COMPLETO ====================

    private void exportInventoryCompleteCsv() {
        if (!validateExport("btnInventoryCompleteCsv")) return;
        // Validar permisos de reportes e inventario
        if (!validateReportPermission("INVENTARIO")) return;

        try {
            Report report = reportService.inventoryReport("Inventario completo", null, null, includeDeleted.isSelected(), selectedStockLevel(), true);
            FileChooser fc = new FileChooser();
            fc.setTitle("Exportar Inventario Completo - CSV");
            fc.setInitialFileName("inventario_completo_" + getTimestamp() + ".csv");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
            File f = fc.showSaveDialog(btnInventoryCompleteCsv.getScene().getWindow());
            if (f == null) return;

            exportToCsv(report, f);
            showSuccess("CSV exportado exitosamente: " + f.getName());
        } catch (Exception ex) {
            Logs.error(getClass(), "Error exportando inventario completo a CSV", ex);
            showError("No se pudo exportar el reporte: " + ex.getMessage());
        }
    }

    private void exportInventoryCompletePdf() {
        if (!validateExport("btnInventoryCompletePdf")) return;
        try {
            Report report = reportService.inventoryReport("Inventario completo", null, null, includeDeleted.isSelected(), selectedStockLevel(), true);
            FileChooser fc = new FileChooser();
            fc.setTitle("Exportar Inventario Completo - PDF");
            fc.setInitialFileName("inventario_completo_" + getTimestamp() + ".pdf");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
            File f = fc.showSaveDialog(btnInventoryCompletePdf.getScene().getWindow());
            if (f == null) return;
            String description = "Listado completo de todos los productos registrados en el sistema con su stock actual y mínimo.";
            exportToPdfAsync(report, f, description);
        } catch (Exception ex) {
            Logs.error(getClass(), "Error exportando inventario completo a PDF", ex);
            showError("No se pudo exportar el reporte: " + ex.getMessage());
        }
    }

    // ==================== PRODUCTOS CON STOCK BAJO ====================

    private void exportInventoryLowStockCsv() {
        if (!validateExport("btnInventoryLowStockCsv")) return;
        try {
            Report report = reportService.inventoryReport("Productos bajo el mínimo", null, null, includeDeleted.isSelected(), com.joshuamc1.sicam.core.enums.StockLevel.BELOW_MINIMUM);
            FileChooser fc = new FileChooser();
            fc.setTitle("Exportar Productos con Stock Bajo - CSV");
            fc.setInitialFileName("productos_stock_bajo_" + getTimestamp() + ".csv");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
            File f = fc.showSaveDialog(btnInventoryLowStockCsv.getScene().getWindow());
            if (f == null) return;

            exportToCsv(report, f);
            showSuccess("CSV exportado exitosamente: " + f.getName());
        } catch (Exception ex) {
            Logs.error(getClass(), "Error exportando productos con stock bajo a CSV", ex);
            showError("No se pudo exportar el reporte: " + ex.getMessage());
        }
    }

    private void exportInventoryLowStockPdf() {
        if (!validateExport("btnInventoryLowStockPdf")) return;
        try {
            Report report = reportService.inventoryReport("Productos bajo el mínimo", null, null, includeDeleted.isSelected(), com.joshuamc1.sicam.core.enums.StockLevel.BELOW_MINIMUM);
            FileChooser fc = new FileChooser();
            fc.setTitle("Exportar Productos con Stock Bajo - PDF");
            fc.setInitialFileName("productos_stock_bajo_" + getTimestamp() + ".pdf");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
            File f = fc.showSaveDialog(btnInventoryLowStockPdf.getScene().getWindow());
            if (f == null) return;
            String description = "Productos con stock positivo menor que el mínimo. No incluye productos sin stock ni cerca del mínimo.";
            exportToPdfAsync(report, f, description);
        } catch (Exception ex) {
            Logs.error(getClass(), "Error exportando productos con stock bajo a PDF", ex);
            showError("No se pudo exportar el reporte: " + ex.getMessage());
        }
    }

    // ==================== PRODUCTOS SIN STOCK ====================

    private void exportInventoryOutOfStockCsv() {
        if (!validateExport("btnInventoryOutOfStockCsv")) return;
        try {
            Report report = reportService.inventoryReport("Productos sin stock", null, null, includeDeleted.isSelected(), com.joshuamc1.sicam.core.enums.StockLevel.OUT_OF_STOCK);
            FileChooser fc = new FileChooser();
            fc.setTitle("Exportar Productos Sin Stock - CSV");
            fc.setInitialFileName("productos_sin_stock_" + getTimestamp() + ".csv");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
            File f = fc.showSaveDialog(btnInventoryOutOfStockCsv.getScene().getWindow());
            if (f == null) return;

            exportToCsv(report, f);
            showSuccess("CSV exportado exitosamente: " + f.getName());
        } catch (Exception ex) {
            Logs.error(getClass(), "Error exportando productos sin stock a CSV", ex);
            showError("No se pudo exportar el reporte: " + ex.getMessage());
        }
    }

    private void exportInventoryOutOfStockPdf() {
        if (!validateExport("btnInventoryOutOfStockPdf")) return;
        try {
            Report report = reportService.inventoryReport("Productos sin stock", null, null, includeDeleted.isSelected(), com.joshuamc1.sicam.core.enums.StockLevel.OUT_OF_STOCK);
            FileChooser fc = new FileChooser();
            fc.setTitle("Exportar Productos Sin Stock - PDF");
            fc.setInitialFileName("productos_sin_stock_" + getTimestamp() + ".pdf");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
            File f = fc.showSaveDialog(btnInventoryOutOfStockPdf.getScene().getWindow());
            if (f == null) return;
            String description = "Productos con stock igual o menor que cero. Requieren reabastecimiento urgente.";
            exportToPdfAsync(report, f, description);
        } catch (Exception ex) {
            Logs.error(getClass(), "Error exportando productos sin stock a PDF", ex);
            showError("No se pudo exportar el reporte: " + ex.getMessage());
        }
    }

    // ==================== MOVIMIENTOS DE INVENTARIO ====================

    private void exportInventoryMovementsCsv() {
        if (!validateExport("btnInventoryMovementsCsv")) return;
        try {
            Report report = reportService.transactionReport("Movimientos de los últimos 30 días", null, java.time.LocalDate.now().minusDays(29).toString(), java.time.LocalDate.now().toString(), null, null, selectedTransactionStatus());
            FileChooser fc = new FileChooser();
            fc.setTitle("Exportar Movimientos de Inventario - CSV");
            fc.setInitialFileName("movimientos_inventario_" + getTimestamp() + ".csv");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
            File f = fc.showSaveDialog(btnInventoryMovementsCsv.getScene().getWindow());
            if (f == null) return;

            exportToCsv(report, f);
            showSuccess("CSV exportado exitosamente: " + f.getName());
        } catch (Exception ex) {
            Logs.error(getClass(), "Error exportando movimientos de inventario a CSV", ex);
            showError("No se pudo exportar el reporte: " + ex.getMessage());
        }
    }

    private void exportInventoryMovementsPdf() {
        if (!validateExport("btnInventoryMovementsPdf")) return;
        try {
            Report report = reportService.transactionReport("Movimientos de los últimos 30 días", null, java.time.LocalDate.now().minusDays(29).toString(), java.time.LocalDate.now().toString(), null, null, selectedTransactionStatus());
            FileChooser fc = new FileChooser();
            fc.setTitle("Exportar Movimientos de Inventario - PDF");
            fc.setInitialFileName("movimientos_inventario_" + getTimestamp() + ".pdf");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
            File f = fc.showSaveDialog(btnInventoryMovementsPdf.getScene().getWindow());
            if (f == null) return;
            String description = "Movimientos de los últimos 30 días por fecha de operación. Una fila por transacción con desglose de productos y unidades, con estado y datos de invalidación.";
            exportToPdfAsync(report, f, description);
        } catch (Exception ex) {
            Logs.error(getClass(), "Error exportando movimientos de inventario a PDF", ex);
            showError("No se pudo exportar el reporte: " + ex.getMessage());
        }
    }

    // ==================== MOVIMIENTOS POR RANGO DE FECHAS ====================

    private void exportMovementsByDateRangeCsv() {
        if (!validateExport("btnMovementsByDateCsv")) return;
        DateRangeDialog.DateRange dateRange = DateRangeDialog.showDialog("Movimientos por Rango de Fechas");
        if (dateRange == null) return;

        try {
            Report report = reportService.transactionReport("Movimientos por fecha de operación", null,
                dateRange.getFromString(), dateRange.getToString(), null, null, selectedTransactionStatus());
            FileChooser fc = new FileChooser();
            fc.setTitle("Exportar Movimientos por Fecha - CSV");
            fc.setInitialFileName("movimientos_" + dateRange.getFromString() + "_" + dateRange.getToString() + ".csv");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
            File f = fc.showSaveDialog(btnMovementsByDateCsv.getScene().getWindow());
            if (f == null) return;

            exportToCsv(report, f);
            showSuccess("CSV exportado exitosamente: " + f.getName());
        } catch (Exception ex) {
            Logs.error(getClass(), "Error exportando movimientos por fecha a CSV", ex);
            showError("No se pudo exportar el reporte: " + ex.getMessage());
        }
    }

    private void exportMovementsByDateRangePdf() {
        if (!validateExport("btnMovementsByDatePdf")) return;
        DateRangeDialog.DateRange dateRange = DateRangeDialog.showDialog("Movimientos por Rango de Fechas");
        if (dateRange == null) return;

        try {
            Report report = reportService.transactionReport("Movimientos por fecha de operación", null,
                dateRange.getFromString(), dateRange.getToString(), null, null, selectedTransactionStatus());
            FileChooser fc = new FileChooser();
            fc.setTitle("Exportar Movimientos por Fecha - PDF");
            fc.setInitialFileName("movimientos_" + dateRange.getFromString() + "_" + dateRange.getToString() + ".pdf");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
            File f = fc.showSaveDialog(btnMovementsByDatePdf.getScene().getWindow());
            if (f == null) return;
            String description = String.format("Historial de transacciones desde %s hasta %s.",
                dateRange.getFrom().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                dateRange.getTo().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            exportToPdfAsync(report, f, description);
        } catch (Exception ex) {
            Logs.error(getClass(), "Error exportando movimientos por fecha a PDF", ex);
            showError("No se pudo exportar el reporte: " + ex.getMessage());
        }
    }

    // ==================== PRODUCTOS REGISTRADOS POR RANGO DE FECHAS ====================

    private void exportProductsByDateRangeCsv() {
        if (!validateExport("btnProductsByDateCsv")) return;
        DateRangeDialog.DateRange dateRange = DateRangeDialog.showDialog("Productos Registrados por Fecha");
        if (dateRange == null) return;

        try {
            Report report = reportService.inventoryReport("Productos por fecha de registro",
                dateRange.getFromString(), dateRange.getToString(), includeDeleted.isSelected(), selectedStockLevel());
            FileChooser fc = new FileChooser();
            fc.setTitle("Exportar Productos por Fecha - CSV");
            fc.setInitialFileName("productos_registrados_" + dateRange.getFromString() + "_" + dateRange.getToString() + ".csv");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
            File f = fc.showSaveDialog(btnProductsByDateCsv.getScene().getWindow());
            if (f == null) return;

            exportToCsv(report, f);
            showSuccess("CSV exportado exitosamente: " + f.getName());
        } catch (Exception ex) {
            Logs.error(getClass(), "Error exportando productos por fecha a CSV", ex);
            showError("No se pudo exportar el reporte: " + ex.getMessage());
        }
    }

    private void exportProductsByDateRangePdf() {
        if (!validateExport("btnProductsByDatePdf")) return;
        DateRangeDialog.DateRange dateRange = DateRangeDialog.showDialog("Productos Registrados por Fecha");
        if (dateRange == null) return;

        try {
            Report report = reportService.inventoryReport("Productos por fecha de registro",
                dateRange.getFromString(), dateRange.getToString(), includeDeleted.isSelected(), selectedStockLevel());
            FileChooser fc = new FileChooser();
            fc.setTitle("Exportar Productos por Fecha - PDF");
            fc.setInitialFileName("productos_registrados_" + dateRange.getFromString() + "_" + dateRange.getToString() + ".pdf");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
            File f = fc.showSaveDialog(btnProductsByDatePdf.getScene().getWindow());
            if (f == null) return;
            String description = String.format("Productos registrados en el sistema desde %s hasta %s.",
                dateRange.getFrom().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                dateRange.getTo().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            exportToPdfAsync(report, f, description);
        } catch (Exception ex) {
            Logs.error(getClass(), "Error exportando productos por fecha a PDF", ex);
            showError("No se pudo exportar el reporte: " + ex.getMessage());
        }
    }

    // ==================== TRANSACCIONES - HISTORIAL COMPLETO ====================
    private void exportTransactionsAllCsv() {
        if (!validateExport("btnTransactionsAllCsv")) return;
        try {
            Report report = reportService.transactionReport("Historial de transacciones", null, null, null, null, null, selectedTransactionStatus());
            FileChooser fc = new FileChooser();
            fc.setTitle("Exportar Historial de Transacciones - CSV");
            fc.setInitialFileName("transacciones_historial_" + getTimestamp() + ".csv");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
            File f = fc.showSaveDialog(btnTransactionsAllCsv.getScene().getWindow());
            if (f == null) return;
            exportToCsv(report, f);
            showSuccess("CSV exportado exitosamente: " + f.getName());
        } catch (Exception ex) {
            Logs.error(getClass(), "Error exportando historial de transacciones a CSV", ex);
            showError("No se pudo exportar el reporte: " + ex.getMessage());
        }
    }

    private void exportTransactionsAllPdf() {
        if (!validateExport("btnTransactionsAllPdf")) return;
        try {
            Report report = reportService.transactionReport("Historial de transacciones", null, null, null, null, null, selectedTransactionStatus());
            FileChooser fc = new FileChooser();
            fc.setTitle("Exportar Historial de Transacciones - PDF");
            fc.setInitialFileName("transacciones_historial_" + getTimestamp() + ".pdf");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
            File f = fc.showSaveDialog(btnTransactionsAllPdf.getScene().getWindow());
            if (f == null) return;
            String description = "Historial según el estado seleccionado. Una fila por transacción con desglose de productos y unidades; incluye los datos de invalidación.";
            exportToPdfAsync(report, f, description);
        } catch (Exception ex) {
            Logs.error(getClass(), "Error exportando historial de transacciones a PDF", ex);
            showError("No se pudo exportar el reporte: " + ex.getMessage());
        }
    }

    // ==================== TRANSACCIONES - POR TIPO ====================
    private void exportTransactionsByTypeCsv() {
        if (!validateExport("btnTransactionsByTypeCsv")) return;
        TransactionTypeDialog.TransactionType type = TransactionTypeDialog.showDialog("Transacciones por Tipo");
        if (type == null) return;
        try {
            Report report = reportService.transactionReport("Transacciones por tipo", type.getCode(), null, null, null, null, selectedTransactionStatus());
            FileChooser fc = new FileChooser();
            fc.setTitle("Exportar Transacciones por Tipo - CSV");
            fc.setInitialFileName("transacciones_" + type.getCode() + "_" + getTimestamp() + ".csv");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
            File f = fc.showSaveDialog(btnTransactionsByTypeCsv.getScene().getWindow());
            if (f == null) return;
            exportToCsv(report, f);
            showSuccess("CSV exportado exitosamente: " + f.getName());
        } catch (Exception ex) {
            Logs.error(getClass(), "Error exportando transacciones por tipo a CSV", ex);
            showError("No se pudo exportar el reporte: " + ex.getMessage());
        }
    }

    private void exportTransactionsByTypePdf() {
        if (!validateExport("btnTransactionsByTypePdf")) return;
        TransactionTypeDialog.TransactionType type = TransactionTypeDialog.showDialog("Transacciones por Tipo");
        if (type == null) return;
        try {
            Report report = reportService.transactionReport("Transacciones por tipo", type.getCode(), null, null, null, null, selectedTransactionStatus());
            FileChooser fc = new FileChooser();
            fc.setTitle("Exportar Transacciones por Tipo - PDF");
            fc.setInitialFileName("transacciones_" + type.getCode() + "_" + getTimestamp() + ".pdf");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
            File f = fc.showSaveDialog(btnTransactionsByTypePdf.getScene().getWindow());
            if (f == null) return;
            String description = "Transacciones filtradas por tipo: " + type.getDisplayName();
            exportToPdfAsync(report, f, description);
        } catch (Exception ex) {
            Logs.error(getClass(), "Error exportando transacciones por tipo a PDF", ex);
            showError("No se pudo exportar el reporte: " + ex.getMessage());
        }
    }

    // ==================== TRANSACCIONES - POR TIPO Y FECHA ====================
    private void exportTransactionsByTypeAndDateCsv() {
        if (!validateExport("btnTransactionsByTypeAndDateCsv")) return;
        TransactionTypeDialog.TransactionType type = TransactionTypeDialog.showDialog("Transacciones por Tipo y Fecha");
        if (type == null) return;
        DateRangeDialog.DateRange dateRange = DateRangeDialog.showDialog("Seleccione el rango de fechas");
        if (dateRange == null) return;
        try {
            Report report = reportService.transactionReport("Transacciones por tipo y fecha de operación", type.getCode(), dateRange.getFromString(), dateRange.getToString(), null, null, selectedTransactionStatus());
            FileChooser fc = new FileChooser();
            fc.setTitle("Exportar Transacciones por Tipo y Fecha - CSV");
            fc.setInitialFileName("transacciones_" + type.getCode() + "_" + dateRange.getFromString() + "_" + dateRange.getToString() + ".csv");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
            File f = fc.showSaveDialog(btnTransactionsByTypeAndDateCsv.getScene().getWindow());
            if (f == null) return;
            exportToCsv(report, f);
            showSuccess("CSV exportado exitosamente: " + f.getName());
        } catch (Exception ex) {
            Logs.error(getClass(), "Error exportando transacciones por tipo y fecha a CSV", ex);
            showError("No se pudo exportar el reporte: " + ex.getMessage());
        }
    }

    private void exportTransactionsByTypeAndDatePdf() {
        if (!validateExport("btnTransactionsByTypeAndDatePdf")) return;
        TransactionTypeDialog.TransactionType type = TransactionTypeDialog.showDialog("Transacciones por Tipo y Fecha");
        if (type == null) return;
        DateRangeDialog.DateRange dateRange = DateRangeDialog.showDialog("Seleccione el rango de fechas");
        if (dateRange == null) return;
        try {
            Report report = reportService.transactionReport("Transacciones por tipo y fecha de operación", type.getCode(), dateRange.getFromString(), dateRange.getToString(), null, null, selectedTransactionStatus());
            FileChooser fc = new FileChooser();
            fc.setTitle("Exportar Transacciones por Tipo y Fecha - PDF");
            fc.setInitialFileName("transacciones_" + type.getCode() + "_" + dateRange.getFromString() + "_" + dateRange.getToString() + ".pdf");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
            File f = fc.showSaveDialog(btnTransactionsByTypeAndDatePdf.getScene().getWindow());
            if (f == null) return;
            String description = String.format("Transacciones de tipo %s desde %s hasta %s.", type.getDisplayName(),
                dateRange.getFrom().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                dateRange.getTo().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            exportToPdfAsync(report, f, description);
        } catch (Exception ex) {
            Logs.error(getClass(), "Error exportando transacciones por tipo y fecha a PDF", ex);
            showError("No se pudo exportar el reporte: " + ex.getMessage());
        }
    }

    // ==================== TRANSACCIONES - POR USUARIO ====================
    private void exportTransactionsByUserCsv() {
        if (!validateExport("btnTransactionsByUserCsv")) return;
        UserSelectionDialog.UserSelection userSel = UserSelectionDialog.showDialog("Transacciones por Usuario");
        if (userSel == null) return;
        try {
            Report report = reportService.transactionReport("Transacciones por usuario", null, null, null, userSel.getUserId(), null, selectedTransactionStatus());
            FileChooser fc = new FileChooser();
            fc.setTitle("Exportar Transacciones por Usuario - CSV");
            fc.setInitialFileName("transacciones_usuario_" + userSel.getUserId() + "_" + getTimestamp() + ".csv");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
            File f = fc.showSaveDialog(btnTransactionsByUserCsv.getScene().getWindow());
            if (f == null) return;
            exportToCsv(report, f);
            showSuccess("CSV exportado exitosamente: " + f.getName());
        } catch (Exception ex) {
            Logs.error(getClass(), "Error exportando transacciones por usuario a CSV", ex);
            showError("No se pudo exportar el reporte: " + ex.getMessage());
        }
    }

    private void exportTransactionsByUserPdf() {
        if (!validateExport("btnTransactionsByUserPdf")) return;
        UserSelectionDialog.UserSelection userSel = UserSelectionDialog.showDialog("Transacciones por Usuario");
        if (userSel == null) return;
        try {
            Report report = reportService.transactionReport("Transacciones por usuario", null, null, null, userSel.getUserId(), null, selectedTransactionStatus());
            FileChooser fc = new FileChooser();
            fc.setTitle("Exportar Transacciones por Usuario - PDF");
            fc.setInitialFileName("transacciones_usuario_" + userSel.getUserId() + "_" + getTimestamp() + ".pdf");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
            File f = fc.showSaveDialog(btnTransactionsByUserPdf.getScene().getWindow());
            if (f == null) return;
            String description = "Historial de transacciones realizadas por el usuario: " + userSel.getUserName();
            exportToPdfAsync(report, f, description);
        } catch (Exception ex) {
            Logs.error(getClass(), "Error exportando transacciones por usuario a PDF", ex);
            showError("No se pudo exportar el reporte: " + ex.getMessage());
        }
    }

    // ==================== TRANSACCIONES - POR PERSONA ====================
    private void exportTransactionsByPersonCsv() {
        if (!validateExport("btnTransactionsByPersonCsv")) return;
        PersonSelectionDialog.PersonSelection personSel = PersonSelectionDialog.showDialog("Transacciones por Persona");
        if (personSel == null) return;
        try {
            Report report = reportService.transactionReport("Transacciones por persona", null, null, null, null, personSel.getPersonId(), selectedTransactionStatus());
            FileChooser fc = new FileChooser();
            fc.setTitle("Exportar Transacciones por Persona - CSV");
            fc.setInitialFileName("transacciones_persona_" + personSel.getPersonId() + "_" + getTimestamp() + ".csv");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
            File f = fc.showSaveDialog(btnTransactionsByPersonCsv.getScene().getWindow());
            if (f == null) return;
            exportToCsv(report, f);
            showSuccess("CSV exportado exitosamente: " + f.getName());
        } catch (Exception ex) {
            Logs.error(getClass(), "Error exportando transacciones por persona a CSV", ex);
            showError("No se pudo exportar el reporte: " + ex.getMessage());
        }
    }

    private void exportTransactionsByPersonPdf() {
        if (!validateExport("btnTransactionsByPersonPdf")) return;
        PersonSelectionDialog.PersonSelection personSel = PersonSelectionDialog.showDialog("Transacciones por Persona");
        if (personSel == null) return;
        try {
            Report report = reportService.transactionReport("Transacciones por persona", null, null, null, null, personSel.getPersonId(), selectedTransactionStatus());
            FileChooser fc = new FileChooser();
            fc.setTitle("Exportar Transacciones por Persona - PDF");
            fc.setInitialFileName("transacciones_persona_" + personSel.getPersonId() + "_" + getTimestamp() + ".pdf");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
            File f = fc.showSaveDialog(btnTransactionsByPersonPdf.getScene().getWindow());
            if (f == null) return;
            String description = "Transacciones asociadas a la persona: " + personSel.getPersonName();
            exportToPdfAsync(report, f, description);
        } catch (Exception ex) {
            Logs.error(getClass(), "Error exportando transacciones por persona a PDF", ex);
            showError("No se pudo exportar el reporte: " + ex.getMessage());
        }
    }

    // ==================== REPORTES ANALÍTICOS - PRODUCTOS MÁS MOVIDOS ====================
    private void exportProductsMostMovedCsv() {
        if (!validateExport("btnProductsMostMovedCsv")) return;
        DateRangeDialog.DateRange dateRange = DateRangeDialog.showDialog("Productos con mayor movimiento");
        if (dateRange == null) return;
        try {
            Report report = reportService.productsMostMoved(dateRange.getFromString(), dateRange.getToString());
            FileChooser fc = new FileChooser();
            fc.setTitle("Exportar Productos con mayor movimiento - CSV");
            fc.setInitialFileName("productos_con_mayor_movimiento_" + getTimestamp() + ".csv");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
            File f = fc.showSaveDialog(btnProductsMostMovedCsv.getScene().getWindow());
            if (f == null) return;
            exportToCsv(report, f);
            showSuccess("CSV exportado exitosamente: " + f.getName());
        } catch (Exception ex) {
            Logs.error(getClass(), "Error exportando productos más movidos a CSV", ex);
            showError("No se pudo exportar el reporte: " + ex.getMessage());
        }
    }

    private void exportProductsMostMovedPdf() {
        if (!validateExport("btnProductsMostMovedPdf")) return;
        DateRangeDialog.DateRange dateRange = DateRangeDialog.showDialog("Productos con mayor movimiento");
        if (dateRange == null) return;
        try {
            Report report = reportService.productsMostMoved(dateRange.getFromString(), dateRange.getToString());
            FileChooser fc = new FileChooser();
            fc.setTitle("Exportar Productos con mayor movimiento - PDF");
            fc.setInitialFileName("productos_con_mayor_movimiento_" + getTimestamp() + ".pdf");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
            File f = fc.showSaveDialog(btnProductsMostMovedPdf.getScene().getWindow());
            if (f == null) return;
            String description = String.format("Productos ordenados por número de transacciones vigentes desde %s hasta %s; excluye invalidadas y artículos cancelados.",
                dateRange.getFrom().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                dateRange.getTo().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            exportToPdfAsync(report, f, description);
        } catch (Exception ex) {
            Logs.error(getClass(), "Error exportando productos más movidos a PDF", ex);
            showError("No se pudo exportar el reporte: " + ex.getMessage());
        }
    }

    // ==================== REPORTES ANALÍTICOS - PRÉSTAMOS ACTIVOS ====================
    private void exportPeopleWithLoansCsv() {
        if (!validateExport("btnPeopleWithLoansCsv")) return;
        try {
            Report report = reportService.peopleWithActiveLoans();
            FileChooser fc = new FileChooser();
            fc.setTitle("Exportar Personas con Préstamos Activos - CSV");
            fc.setInitialFileName("prestamos_activos_" + getTimestamp() + ".csv");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
            File f = fc.showSaveDialog(btnPeopleWithLoansCsv.getScene().getWindow());
            if (f == null) return;
            exportToCsv(report, f);
            showSuccess("CSV exportado exitosamente: " + f.getName());
        } catch (Exception ex) {
            Logs.error(getClass(), "Error exportando préstamos activos a CSV", ex);
            showError("No se pudo exportar el reporte: " + ex.getMessage());
        }
    }

    private void exportPeopleWithLoansPdf() {
        if (!validateExport("btnPeopleWithLoansPdf")) return;
        try {
            Report report = reportService.peopleWithActiveLoans();
            FileChooser fc = new FileChooser();
            fc.setTitle("Exportar Personas con Préstamos Activos - PDF");
            fc.setInitialFileName("prestamos_activos_" + getTimestamp() + ".pdf");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
            File f = fc.showSaveDialog(btnPeopleWithLoansPdf.getScene().getWindow());
            if (f == null) return;
            String description = "Una fila por préstamo, con responsable, contacto y productos. Vencidos primero; excluye cerrados e invalidados.";
            exportToPdfAsync(report, f, description);
        } catch (Exception ex) {
            Logs.error(getClass(), "Error exportando préstamos activos a PDF", ex);
            showError("No se pudo exportar el reporte: " + ex.getMessage());
        }
    }

    // ==================== REPORTES ANALÍTICOS - CONSUMO MENSUAL ====================
    private void exportMonthlyConsumptionCsv() {
        if (!validateExport("btnMonthlyConsumptionCsv")) return;
        try {
            Report report = reportService.monthlyConsumption(12); // Últimos 12 meses
            FileChooser fc = new FileChooser();
            fc.setTitle("Exportar Consumo Mensual - CSV");
            fc.setInitialFileName("consumo_mensual_" + getTimestamp() + ".csv");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
            File f = fc.showSaveDialog(btnMonthlyConsumptionCsv.getScene().getWindow());
            if (f == null) return;
            exportToCsv(report, f);
            showSuccess("CSV exportado exitosamente: " + f.getName());
        } catch (Exception ex) {
            Logs.error(getClass(), "Error exportando consumo mensual a CSV", ex);
            showError("No se pudo exportar el reporte: " + ex.getMessage());
        }
    }

    private void exportMonthlyConsumptionPdf() {
        if (!validateExport("btnMonthlyConsumptionPdf")) return;
        try {
            Report report = reportService.monthlyConsumption(12); // Últimos 12 meses
            FileChooser fc = new FileChooser();
            fc.setTitle("Exportar Consumo Mensual - PDF");
            fc.setInitialFileName("consumo_mensual_" + getTimestamp() + ".pdf");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
            File f = fc.showSaveDialog(btnMonthlyConsumptionPdf.getScene().getWindow());
            if (f == null) return;
            String description = "Egresos vigentes por producto y mes, con cantidad y número de egresos. Últimos 12 meses; mes actual hasta hoy. No incluye préstamos ni invalidados.";
            exportToPdfAsync(report, f, description);
        } catch (Exception ex) {
            Logs.error(getClass(), "Error exportando consumo mensual a PDF", ex);
            showError("No se pudo exportar el reporte: " + ex.getMessage());
        }
    }

    // ==================== REPORTES ANALÍTICOS - PRODUCTOS SIN MOVIMIENTO ====================
    private void exportProductsNoMovementCsv() {
        if (!validateExport("btnProductsNoMovementCsv")) return;
        try {
            Report report = reportService.productsWithoutMovement(90); // Últimos 90 días
            FileChooser fc = new FileChooser();
            fc.setTitle("Exportar Productos Sin Movimiento - CSV");
            fc.setInitialFileName("productos_sin_movimiento_" + getTimestamp() + ".csv");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
            File f = fc.showSaveDialog(btnProductsNoMovementCsv.getScene().getWindow());
            if (f == null) return;
            exportToCsv(report, f);
            showSuccess("CSV exportado exitosamente: " + f.getName());
        } catch (Exception ex) {
            Logs.error(getClass(), "Error exportando productos sin movimiento a CSV", ex);
            showError("No se pudo exportar el reporte: " + ex.getMessage());
        }
    }

    private void exportProductsNoMovementPdf() {
        if (!validateExport("btnProductsNoMovementPdf")) return;
        try {
            Report report = reportService.productsWithoutMovement(90); // Últimos 90 días
            FileChooser fc = new FileChooser();
            fc.setTitle("Exportar Productos Sin Movimiento - PDF");
            fc.setInitialFileName("productos_sin_movimiento_" + getTimestamp() + ".pdf");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
            File f = fc.showSaveDialog(btnProductsNoMovementPdf.getScene().getWindow());
            if (f == null) return;
            String description = "Sin movimientos vigentes durante al menos 90 días; excluye invalidados y artículos cancelados. Si nunca hubo movimientos, se cuenta desde la creación del producto.";
            exportToPdfAsync(report, f, description);
        } catch (Exception ex) {
            Logs.error(getClass(), "Error exportando productos sin movimiento a PDF", ex);
            showError("No se pudo exportar el reporte: " + ex.getMessage());
        }
    }

    // ==================== REPORTES ANALÍTICOS - ACTIVIDAD POR USUARIO ====================
    private void exportUserActivityCsv() {
        if (!validateExport("btnUserActivityCsv")) return;
        UserSelectionDialog.UserSelection userSel = UserSelectionDialog.showDialog("Actividad por Usuario");
        if (userSel == null) return;
        DateRangeDialog.DateRange dateRange = DateRangeDialog.showDialog("Actividad por fecha de registro", true);
        if (dateRange == null) return;
        try {
            Report report = reportService.userActivity(userSel.getUserId(), dateRange.getFromString(), dateRange.getToString());
            FileChooser fc = new FileChooser();
            fc.setTitle("Exportar Actividad por Usuario - CSV");
            fc.setInitialFileName("actividad_usuario_" + userSel.getUserId() + "_" + getTimestamp() + ".csv");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
            File f = fc.showSaveDialog(btnUserActivityCsv.getScene().getWindow());
            if (f == null) return;
            exportToCsv(report, f);
            showSuccess("CSV exportado exitosamente: " + f.getName());
        } catch (Exception ex) {
            Logs.error(getClass(), "Error exportando actividad de usuario a CSV", ex);
            showError("No se pudo exportar el reporte: " + ex.getMessage());
        }
    }

    private void exportUserActivityPdf() {
        if (!validateExport("btnUserActivityPdf")) return;
        UserSelectionDialog.UserSelection userSel = UserSelectionDialog.showDialog("Actividad por Usuario");
        if (userSel == null) return;
        DateRangeDialog.DateRange dateRange = DateRangeDialog.showDialog("Actividad por fecha de registro", true);
        if (dateRange == null) return;
        try {
            Report report = reportService.userActivity(userSel.getUserId(), dateRange.getFromString(), dateRange.getToString());
            FileChooser fc = new FileChooser();
            fc.setTitle("Exportar Actividad por Usuario - PDF");
            fc.setInitialFileName("actividad_usuario_" + userSel.getUserId() + "_" + getTimestamp() + ".pdf");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
            File f = fc.showSaveDialog(btnUserActivityPdf.getScene().getWindow());
            if (f == null) return;
            String description = "Transacciones registradas por " + userSel.getUserName()
                + (dateRange.isAllDates() ? "; todas las fechas." : "; del " + dateRange.getFromString() + " al " + dateRange.getToString() + ".")
                + " Incluye productos, estados actuales, notas e invalidaciones; una fila por transacción.";
            exportToPdfAsync(report, f, description);
        } catch (Exception ex) {
            Logs.error(getClass(), "Error exportando actividad de usuario a PDF", ex);
            showError("No se pudo exportar el reporte: " + ex.getMessage());
        }
    }

    // ==================== REPORTES ANALÍTICOS - DASHBOARD MENSUAL ====================
    private void exportMonthlyDashboardCsv() {
        if (!validateExport("btnMonthlyDashboardCsv")) return;
        MonthYearDialog.MonthYearSelection monthYear = MonthYearDialog.showDialog("Dashboard Mensual");
        if (monthYear == null) return;
        try {
            Report report = reportService.monthlyDashboard(monthYear.getYearMonth());
            FileChooser fc = new FileChooser();
            fc.setTitle("Exportar Dashboard Mensual - CSV");
            fc.setInitialFileName("dashboard_" + monthYear.getYearMonth() + ".csv");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
            File f = fc.showSaveDialog(btnMonthlyDashboardCsv.getScene().getWindow());
            if (f == null) return;
            exportToCsv(report, f);
            showSuccess("CSV exportado exitosamente: " + f.getName());
        } catch (Exception ex) {
            Logs.error(getClass(), "Error exportando dashboard mensual a CSV", ex);
            showError("No se pudo exportar el reporte: " + ex.getMessage());
        }
    }

    private void exportMonthlyDashboardPdf() {
        if (!validateExport("btnMonthlyDashboardPdf")) return;
        MonthYearDialog.MonthYearSelection monthYear = MonthYearDialog.showDialog("Dashboard Mensual");
        if (monthYear == null) return;
        try {
            Report report = reportService.monthlyDashboard(monthYear.getYearMonth());
            FileChooser fc = new FileChooser();
            fc.setTitle("Exportar Dashboard Mensual - PDF");
            fc.setInitialFileName("dashboard_" + monthYear.getYearMonth() + ".pdf");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
            File f = fc.showSaveDialog(btnMonthlyDashboardPdf.getScene().getWindow());
            if (f == null) return;
            writeMonthlyDashboardRichPdf(report, f, monthYear.getFormatted());
            showSuccess("Dashboard mensual PDF exportado: " + f.getName());
        } catch (Exception ex) {
            Logs.error(getClass(), "Error exportando dashboard mensual a PDF", ex);
            showError("No se pudo exportar el reporte: " + ex.getMessage());
        }
    }

    // Exportación enriquecida con layout de KPIs y gráfico simple
    private void writeMonthlyDashboardRichPdf(Report report, File file, String periodo) throws IOException {
        try (org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument()) {
            PdfFonts fonts = loadFonts(doc);
            PDFont regularPdfFont = fonts.regular;
            PDFont boldPdfFont = fonts.bold;
            org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage(org.apache.pdfbox.pdmodel.common.PDRectangle.A4);
            doc.addPage(page);
            org.apache.pdfbox.pdmodel.PDPageContentStream cs = new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page);
            final float MARGIN = 40f;
            float y = page.getMediaBox().getHeight() - MARGIN;
            float width = page.getMediaBox().getWidth() - 2 * MARGIN;

            // Título
            cs.beginText();
            cs.setFont(boldPdfFont, 18);
            cs.newLineAtOffset(MARGIN, y);
            cs.showText("Dashboard Mensual - " + periodo);
            cs.endText();
            y -= 30;

            // Fecha de generación
            cs.beginText();
            cs.setFont(regularPdfFont, 10);
            cs.newLineAtOffset(MARGIN, y);
            cs.showText("Generado: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
            cs.endText();
            y -= 20;

            // KPIs principales (tomar algunas métricas clave)
            // Buscamos por nombre conocido en el reporte
            java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
            for (java.util.List<String> row : report.getRows()) {
                if (row.size() < 3) continue;
                String k = row.get(1);
                String v = row.get(2);
                if (k.startsWith("---")) continue; // separador
                map.put(k, v);
            }

            map.put("Total de Productos en Sistema", map.get("Productos no eliminados"));
            map.put("Productos con Stock Bajo", map.get("Bajo el mínimo"));
            map.put("Productos Sin Stock", map.get("Sin stock"));
            map.put("Total Transacciones", map.get("Total de transacciones registradas"));
            map.put("Ingresos", map.getOrDefault("Ingresos — Activos", "0"));
            map.put("Egresos", map.getOrDefault("Egresos — Activos", "0"));
            map.put("Préstamos", map.getOrDefault("Préstamos — Activos", "0"));
            map.put("Usuarios Activos", map.get("Usuarios con operaciones"));

            // Selección de KPIs importantes
            String[] kpisOrden = {
                "Total de Productos en Sistema",
                "Productos con Stock Bajo",
                "Productos Sin Stock",
                "Total Transacciones",
                "Ingresos",
                "Egresos",
                "Préstamos",
                "Usuarios Activos"
            };

            float cardHeight = 60f;
            float cardWidth = (width - 20) / 2f; // dos columnas
            float cardYStart = y;
            int col = 0;
            int rowIdx = 0;

            for (String kpi : kpisOrden) {
                String val = map.get(kpi);
                if (val == null) continue;
                float x = MARGIN + col * (cardWidth + 20);
                float cardY = cardYStart - rowIdx * (cardHeight + 15);
                if (cardY - cardHeight < 80) { // nueva página si no hay espacio
                    cs.close();
                    page = new org.apache.pdfbox.pdmodel.PDPage(org.apache.pdfbox.pdmodel.common.PDRectangle.A4);
                    doc.addPage(page);
                    cs = new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page);
                    cardYStart = page.getMediaBox().getHeight() - MARGIN - 30;
                    rowIdx = 0;
                    cardY = cardYStart;
                }

                // Dibujar tarjeta
                drawRoundedRect(cs, x, cardY - cardHeight, cardWidth, cardHeight, new float[]{0.95f,0.95f,0.95f});
                // KPI nombre
                cs.beginText();
                cs.setFont(regularPdfFont, 10);
                cs.setNonStrokingColor(60/255f,60/255f,60/255f);
                cs.newLineAtOffset(x + 10, cardY - 20);
                cs.showText(kpi);
                cs.endText();
                // KPI valor
                cs.beginText();
                cs.setFont(boldPdfFont, 16);
                cs.setNonStrokingColor(34/255f,102/255f,153/255f);
                cs.newLineAtOffset(x + 10, cardY - 45);
                cs.showText(val);
                cs.endText();

                if (col == 0) {
                    col = 1;
                } else {
                    col = 0;
                    rowIdx++;
                }
            }

            y = cardYStart - (rowIdx + (col==1?1:0)) * (cardHeight + 15) - 30;

            // Mini gráfico de barras: Ingresos/Egresos/Préstamos
            Long ingresos = parseLong(map.get("Ingresos"));
            Long egresos = parseLong(map.get("Egresos"));
            Long prestamos = parseLong(map.get("Préstamos"));
            if (ingresos == null) ingresos = 0L;
            if (egresos == null) egresos = 0L;
            if (prestamos == null) prestamos = 0L;
            float chartHeight = 160f;
            float chartY = y - chartHeight;

            // Fondo gráfico
            drawRoundedRect(cs, MARGIN, chartY, width, chartHeight, new float[]{1f,1f,1f});

            cs.beginText();
            cs.setFont(boldPdfFont, 12);
            cs.setNonStrokingColor(0f,0f,0f);
            cs.newLineAtOffset(MARGIN + 10, chartY + chartHeight - 20);
            cs.showText("Transacciones vigentes del mes");
            cs.endText();

            long max = java.util.Collections.max(java.util.Arrays.asList(ingresos, egresos, prestamos, 1L));
            float barAreaHeight = chartHeight - 75;
            float barWidth = 60f;
            float gap = 40f;
            float startX = MARGIN + 40f;
            float baseY = chartY + 30f;

            // Dibujar barras
            drawBar(cs, startX, baseY, barWidth, scaleValue(ingresos, max, barAreaHeight), new float[]{0.20f,0.60f,0.86f});
            drawBar(cs, startX + barWidth + gap, baseY, barWidth, scaleValue(egresos, max, barAreaHeight), new float[]{0.91f,0.30f,0.24f});
            drawBar(cs, startX + 2*(barWidth + gap), baseY, barWidth, scaleValue(prestamos, max, barAreaHeight), new float[]{0.56f,0.27f,0.68f});

            // Etiquetas barras
            writeCenteredPdf(cs, regularPdfFont, 10, startX + barWidth/2, baseY - 12, "Ingresos");
            writeCenteredPdf(cs, regularPdfFont, 10, startX + barWidth + gap + barWidth/2, baseY - 12, "Egresos");
            writeCenteredPdf(cs, regularPdfFont, 10, startX + 2*(barWidth + gap) + barWidth/2, baseY - 12, "Préstamos");
            writeCenteredPdf(cs, boldPdfFont, 11, startX + barWidth/2, baseY + scaleValue(ingresos, max, barAreaHeight) + 5, String.valueOf(ingresos));
            writeCenteredPdf(cs, boldPdfFont, 11, startX + barWidth + gap + barWidth/2, baseY + scaleValue(egresos, max, barAreaHeight) + 5, String.valueOf(egresos));
            writeCenteredPdf(cs, boldPdfFont, 11, startX + 2*(barWidth + gap) + barWidth/2, baseY + scaleValue(prestamos, max, barAreaHeight) + 5, String.valueOf(prestamos));

            cs.close();
            writeMonthlyIndicatorPages(doc, report, periodo, regularPdfFont, boldPdfFont);
            doc.save(file);
        }
    }

    /** Tarjetas por ámbito; las existencias son actuales y las operaciones corresponden al mes elegido. */
    private void writeMonthlyIndicatorPages(PDDocument doc, Report report, String periodo,
                                            PDFont regular, PDFont bold) throws IOException {
        java.util.Map<String, List<List<String>>> sections = new java.util.LinkedHashMap<>();
        for (List<String> row : report.getRows()) {
            String section = row.get(0).startsWith("Existencias actuales") ? "Existencias actuales"
                : row.get(0).startsWith("Actividad vigente") ? "Participación del mes"
                : row.get(0).startsWith("Actividad de") ? "Operaciones del mes" : row.get(0);
            sections.computeIfAbsent(section, key -> new java.util.ArrayList<>()).add(List.of(row.get(1), row.get(2)));
        }
        long total = report.getRows().stream().filter(r -> r.get(1).equals("Total de transacciones registradas"))
            .mapToLong(r -> Long.parseLong(r.get(2))).findFirst().orElse(0);
        long active = 0, invalidated = 0, cancelled = 0;
        for (List<String> row : report.getRows()) {
            if (row.get(1).endsWith(" — Activos")) active += Long.parseLong(row.get(2));
            if (row.get(1).endsWith(" — Invalidados")) invalidated += Long.parseLong(row.get(2));
            if (row.get(1).endsWith(" — Cancelados")) cancelled += Long.parseLong(row.get(2));
        }
        List<List<String>> rates = new java.util.ArrayList<>();
        for (var metric : List.of(List.of("Transacciones vigentes (%)", String.valueOf(active)),
                List.of("Transacciones invalidadas (%)", String.valueOf(invalidated)),
                List.of("Transacciones canceladas (%)", String.valueOf(cancelled)))) {
            String percent = total == 0 ? "Sin operaciones" : String.format(java.util.Locale.ROOT,
                "%.1f %%", Long.parseLong(metric.get(1)) * 100.0 / total);
            rates.add(List.of(metric.get(0), percent));
        }
        sections.put("Indicadores de operaciones del mes", rates);
        final float margin = 40, gap = 10, cardWidth = (PDRectangle.A4.getWidth() - margin * 2 - gap * 2) / 3;
        PDPageContentStream cs = null;
        float y = 0;
        try {
            for (var section : sections.entrySet()) {
                List<List<String>> metrics = section.getValue();
                for (int offset = 0; offset < metrics.size(); offset += 3) {
                    boolean heading = offset == 0;
                    if (cs == null || y - (heading ? 26 : 0) - 68 < margin) {
                        if (cs != null) cs.close();
                        PDPage page = new PDPage(PDRectangle.A4); doc.addPage(page);
                        cs = new PDPageContentStream(doc, page);
                        y = page.getMediaBox().getHeight() - margin;
                        dashboardText(cs, bold, 16, margin, y, "Indicadores complementarios - " + periodo);
                        y -= 20;
                        dashboardText(cs, regular, 9, margin, y, "Operaciones: mes seleccionado. Existencias y registros: al generar el reporte.");
                        y -= 28;
                        heading = true;
                    }
                    if (heading) {
                        dashboardText(cs, bold, 11, margin, y, section.getKey());
                        y -= 18;
                    }
                    for (int col = 0; col < 3 && offset + col < metrics.size(); col++) {
                        List<String> metric = metrics.get(offset + col);
                        float x = margin + col * (cardWidth + gap);
                        float height = 58;
                        drawRoundedRect(cs, x, y - height, cardWidth, height, new float[]{0.95f,0.95f,0.95f});
                        String lower = metric.get(0).toLowerCase(java.util.Locale.ROOT);
                        float[] color = lower.contains("invalid") || lower.contains("cancel") || lower.contains("sin stock")
                            ? new float[]{0.80f,0.23f,0.20f} : lower.contains("mínimo")
                            ? new float[]{0.75f,0.47f,0.05f} : new float[]{0.13f,0.40f,0.60f};
                        drawBar(cs, x, y - height, 3, height, color);
                        List<String> label = wrapTextByWidth(metric.get(0), regular, 9, cardWidth - 20, Integer.MAX_VALUE);
                        float textY = y - 13;
                        for (String line : label) {
                            dashboardText(cs, regular, 9, x + 10, textY, line); textY -= 10;
                        }
                        cs.setNonStrokingColor(color[0],color[1],color[2]);
                        cs.beginText(); cs.setFont(bold, 15); cs.newLineAtOffset(x + 10, y - 46);
                        cs.showText(metric.get(1)); cs.endText();
                    }
                    y -= 68;
                }
                y -= 8;
            }
        } finally { if (cs != null) cs.close(); }
    }

    private void dashboardText(PDPageContentStream cs, PDFont font, float size, float x, float y, String text) throws IOException {
        cs.setNonStrokingColor(0.15f,0.15f,0.15f);
        cs.beginText(); cs.setFont(font,size); cs.newLineAtOffset(x,y); cs.showText(text); cs.endText();
    }

    private Long parseLong(String v) {
        try { return v == null ? null : Long.parseLong(v.replaceAll("[^0-9]", "")); } catch (Exception e) { return null; }
    }

    private float scaleValue(long value, long max, float height) {
        if (max <= 0) return 0f;
        return (value / (float) max) * height;
    }

    private void drawBar(org.apache.pdfbox.pdmodel.PDPageContentStream cs, float x, float y, float w, float h, float[] rgb) throws java.io.IOException {
        cs.setNonStrokingColor(rgb[0], rgb[1], rgb[2]);
        cs.addRect(x, y, w, h);
        cs.fill();
    }

    private void writeCenteredPdf(org.apache.pdfbox.pdmodel.PDPageContentStream cs, PDFont font, float size, float cx, float y, String text) throws java.io.IOException {
        float textWidth = font.getStringWidth(text) / 1000f * size;
        cs.beginText();
        cs.setFont(font, size);
        cs.setNonStrokingColor(0f,0f,0f);
        cs.newLineAtOffset(cx - textWidth/2, y);
        cs.showText(text);
        cs.endText();
    }

    private void drawRoundedRect(org.apache.pdfbox.pdmodel.PDPageContentStream cs, float x, float y, float w, float h, float[] rgb) throws java.io.IOException {
        cs.setNonStrokingColor(rgb[0], rgb[1], rgb[2]);
        cs.addRect(x, y, w, h);
        cs.fill();
    }

    // ==================== MÉTODOS DE EXPORTACIÓN ====================

    private void exportToCsv(Report report, File file) throws IOException {
        try (BufferedWriter bw = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            // Escribir encabezados
            bw.write(report.getHeaders().stream().map(this::quoteCsv).collect(java.util.stream.Collectors.joining(",")));
            bw.write("\n");

            // Escribir filas
            for (List<String> row : report.getRows()) {
                List<String> quoted = new ArrayList<>(row.size());
                for (String s : row) {
                    quoted.add(quoteCsv(s));
                }
                bw.write(String.join(",", quoted));
                bw.write("\n");
            }
            bw.flush();
        }
    }

    // Wrapper asíncrono con barra de progreso
    private void exportToPdfAsync(Report report, File file, String description) {
        Dialog<Void> progressDialog = new Dialog<>();
        progressDialog.setTitle("Generando PDF");
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
        final boolean[] cancelled = {false};
        progressDialog.getDialogPane().lookupButton(cancelType).addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            if (!cancelled[0]) {
                cancelled[0] = true;
                lblStatus.setText("Cancelando...");
                e.consume(); // mantener diálogo hasta que termine cancelación
            }
        });

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                try {
                    // Consumidor de progreso adaptado al Task
                    Consumer<Double> progressConsumer = p -> {
                        updateProgress(p, 1.0);
                        updateMessage(String.format("Progreso %.0f%%", p * 100));
                    };
                    Supplier<Boolean> cancelSupplier = () -> cancelled[0];
                    updateMessage("Iniciando...");
                    exportToPdfWithProgress(report, file, description, progressConsumer, cancelSupplier);
                    if (cancelled[0]) {
                        updateMessage("Cancelado");
                    } else {
                        updateMessage("Completado");
                    }
                } catch (Exception ex) {
                    Logs.error(getClass(), "Error generando PDF", ex);
                    Platform.runLater(() -> showError("No se pudo generar el PDF: " + ex.getMessage()));
                }
                return null;
            }
        };

        pb.progressProperty().bind(task.progressProperty());
        lblStatus.textProperty().bind(task.messageProperty());

        task.setOnSucceeded(ev -> {
            if (!cancelled[0]) showSuccess("PDF exportado exitosamente: " + file.getName());
            progressDialog.close();
        });
        task.setOnFailed(ev -> {
            showError("Fallo al generar PDF");
            progressDialog.close();
        });
        task.setOnCancelled(ev -> {
            cancelled[0] = true;
            showError("Exportación cancelada");
            progressDialog.close();
        });

        progressDialog.setOnCloseRequest(e -> {
            if (!task.isDone()) {
                cancelled[0] = true;
                task.cancel();
                e.consume();
            }
        });

        Thread th = new Thread(task, "reports-pdf-export");
        th.setDaemon(true);
        th.start();
        progressDialog.initOwner(btnMonthlyDashboardPdf.getScene().getWindow());
        progressDialog.show();
    }

    // Método interno con progreso
    private void exportToPdfWithProgress(Report report, File file, String description, Consumer<Double> progressConsumer, Supplier<Boolean> cancelled) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PdfFonts fonts = loadFonts(doc);
            PDFont regularFont = fonts.regular;
            PDFont boldFont = fonts.bold;

            int totalRows = report.getRows().size();
            int steps = 6 + totalRows; // fuentes, página, encabezado, descripción, encabezados tabla, filas, guardado
            AtomicInteger currentStep = new AtomicInteger(0);
            Runnable step = () -> {
                int cs = currentStep.incrementAndGet();
                progressConsumer.accept(Math.min(1.0, cs / (double) steps));
            };
            if (cancelled.get()) return;
            step.run(); // carga inicial

            PDRectangle pageSize = report.isLandscape()
                ? new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth())
                : report.getHeaders().size() >= 7
                    ? new PDRectangle(PDRectangle.LETTER.getHeight(), PDRectangle.LETTER.getWidth()) : PDRectangle.LETTER;
            PDPage page = new PDPage(pageSize);
            doc.addPage(page);
            step.run();
            if (cancelled.get()) return;

            float margin = 50f;
            float pageWidth = page.getMediaBox().getWidth();
            float pageHeight = page.getMediaBox().getHeight();
            float y = pageHeight - margin;

            PDPageContentStream cs = new PDPageContentStream(doc, page);

            // Logo
            try (java.io.InputStream is = getClass().getResourceAsStream("/images/logo.png")) {
                if (is != null) {
                    byte[] imgBytes = is.readAllBytes();
                    org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject pdImage =
                        org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject.createFromByteArray(doc, imgBytes, "logo");
                    float imgW = 60f; float imgH = 60f; float imgY = y - imgH;
                    cs.drawImage(pdImage, margin, imgY, imgW, imgH);
                    y = imgY;
                }
            } catch (Exception ex) {
                Logs.warn(getClass(), "Logo no cargado: {}", ex.getMessage());
            }

            // Título
            cs.setFont(boldFont, 14);
            for (String titleLine : wrapTextByWidth(report.getTitle(), boldFont, 14, pageWidth - margin * 2 - 75, 8)) {
                cs.beginText(); cs.newLineAtOffset(margin + 75, y + 35); cs.showText(titleLine); cs.endText();
                y -= 18f;
            }
            y -= 20f;

            // Línea
            cs.setLineWidth(1f); cs.moveTo(margin, y); cs.lineTo(pageWidth - margin, y); cs.stroke(); y -= 20f;

            // Descripción
            cs.setFont(regularFont, 11);
            List<String> descLines = wrapTextByWidth(description, regularFont, 11, pageWidth - margin * 2, 8);
            for (String line : descLines) {
                if (cancelled.get()) { cs.close(); return; }
                cs.beginText(); cs.newLineAtOffset(margin, y); cs.showText(line); cs.endText(); y -= 14f;
            }
            y -= 5f;
            step.run(); // descripción procesada

            // Usuario y fecha
            String username = Session.getCurrentUsername(); if (username == null || username.isEmpty()) username = "Sistema";
            cs.setFont(boldFont, 10); cs.beginText(); cs.newLineAtOffset(margin, y); cs.showText("Generado por: "); cs.endText();
            float userLabelWidth = boldFont.getStringWidth("Generado por: ") / 1000 * 10;
            cs.setFont(regularFont, 10); cs.beginText(); cs.newLineAtOffset(margin + userLabelWidth, y); cs.showText(safe(username)); cs.endText(); y -= 14f;
            String fecha = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
            cs.setFont(boldFont, 10); cs.beginText(); cs.newLineAtOffset(margin, y); cs.showText("Fecha: "); cs.endText();
            float fechaLabelWidth = boldFont.getStringWidth("Fecha: ") / 1000 * 10;
            cs.setFont(regularFont, 10); cs.beginText(); cs.newLineAtOffset(margin + fechaLabelWidth, y); cs.showText(fecha); cs.endText(); y -= 25f;

            // Tabla
            List<String> headers = new ArrayList<>(report.getHeaders());
            int groupIndex = report.getGroupByColumn() == null ? -1 : headers.indexOf(report.getGroupByColumn());
            if (groupIndex >= 0) headers.remove(groupIndex);
            Map<String, List<List<String>>> groups = new java.util.LinkedHashMap<>();
            for (List<String> source : report.getRows()) {
                List<String> row = new ArrayList<>(source);
                String group = groupIndex < 0 ? "" : row.remove(groupIndex);
                groups.computeIfAbsent(group, key -> new ArrayList<>()).add(row);
            }
            if (groups.isEmpty()) groups.put("", List.of());
            int numCols = headers.size();
            float tableWidth = pageWidth - 2 * margin;
            float[] colWidths = new float[numCols];
            float totalWeight = 0;
            for (int i = 0; i < numCols; i++) {
                String header = headers.get(i);
                boolean inventory = headers.contains("Nivel de stock");
                float weight = inventory ? (header.equals("Producto") || header.equals("Descripción") ? 2.3f
                    : header.equals("Nivel de stock") ? 2.2f : header.equals("ID") ? 0.6f
                    : header.equals("Stock actual") || header.equals("Stock mínimo") ? 1.1f : 1.6f)
                    : header.equals("Productos") ? 2.5f : header.equals("Invalidación") ? 2.1f
                    : header.equals("Notas") || header.equals("Persona") ? 1.8f
                    : header.equals("DNI") || header.equals("Código") ? 1.7f
                    : header.equals("ID") || header.equals("ID préstamo") || header.equals("Días de atraso") ? 1.0f : 1.6f;
                colWidths[i] = weight; totalWeight += weight;
            }
            for (int i = 0; i < numCols; i++) colWidths[i] = tableWidth * colWidths[i] / totalWeight;
            float cellPaddingX = 3f; float cellPaddingY = 2f; int headerFontSize = 9; int bodyFontSize = 8;
            cs.setFont(boldFont, headerFontSize);
            float headerRowHeight = 0f;
            List<List<String>> headerLines = new ArrayList<>();
            for (int headerIndex = 0; headerIndex < numCols; headerIndex++) {
                String header = headers.get(headerIndex);
                if (header.equals("ID préstamo")) header = "ID";
                if (header.equals("Días de atraso")) header = "Días atraso";
                List<String> lines = wrapTextByWidth(header, boldFont, headerFontSize,
                    colWidths[headerIndex] - cellPaddingX * 2, Integer.MAX_VALUE);
                headerLines.add(lines);
                headerRowHeight = Math.max(headerRowHeight, lines.size() * (headerFontSize + cellPaddingY));
            }
            headerRowHeight = Math.max(headerRowHeight, headerFontSize + 6);
            step.run(); // encabezados preparados
            boolean firstGroup = true;
            for (Map.Entry<String, List<List<String>>> group : groups.entrySet()) {
                String groupTitle = groupIndex < 0 || group.getValue().isEmpty() ? null
                    : "Usuarios — " + Roles.getRoleFullName(group.getKey()) + " (" + group.getValue().size() + ")";
                float captionHeight = groupTitle == null ? 0 : wrapTextByWidth(groupTitle, boldFont, 10,
                    tableWidth, Integer.MAX_VALUE).size() * 12 + 8;
                int firstRowLines = 1;
                if (!group.getValue().isEmpty()) {
                    List<String> firstRow = group.getValue().get(0);
                    for (int i = 0; i < numCols; i++) {
                        String value = i < firstRow.size() ? firstRow.get(i) : "";
                        firstRowLines = Math.max(firstRowLines, wrapTextByWidth(value, regularFont, bodyFontSize,
                            colWidths[i] - cellPaddingX * 2, Integer.MAX_VALUE).size());
                    }
                }
                if (!firstGroup) y -= 18;
                int fullPageLines = Math.max(1, (int) ((pageHeight - margin * 2 - captionHeight - headerRowHeight - 10)
                    / (bodyFontSize + cellPaddingY)));
                float needed = captionHeight + headerRowHeight + 4
                    + (firstRowLines > fullPageLines ? 1 : firstRowLines) * (bodyFontSize + cellPaddingY) + 5;
                if (groupIndex >= 0 && y - needed < margin) {
                    cs.close(); page = new PDPage(pageSize); doc.addPage(page);
                    cs = new PDPageContentStream(doc, page); y = pageHeight - margin;
                }
                y = drawPdfTableHeader(cs, groupTitle, headerLines, colWidths, boldFont,
                    margin, y, cellPaddingX, cellPaddingY, headerFontSize, headerRowHeight, tableWidth);
                cs.setFont(regularFont, bodyFontSize);
                firstGroup = false;
                for (List<String> row : group.getValue()) {
                    if (cancelled.get()) { cs.close(); return; }
                    List<List<String>> rowLines = new ArrayList<>();
                    int maxLines = 1;
                    for (int i = 0; i < numCols; i++) {
                        String value = i < row.size() && row.get(i) != null ? row.get(i) : "";
                        if (headers.get(i).equalsIgnoreCase("Rol")) value = Roles.getRoleFullName(value);
                        List<String> lines = wrapTextByWidth(value, regularFont, bodyFontSize,
                            colWidths[i] - cellPaddingX * 2, Integer.MAX_VALUE);
                        rowLines.add(lines); maxLines = Math.max(maxLines, lines.size());
                    }
                    float lineHeight = bodyFontSize + cellPaddingY;
                    int lineOffset = 0;
                    while (lineOffset < maxLines) {
                        if (cancelled.get()) { cs.close(); return; }
                        int available = (int) ((y - margin - 5) / lineHeight);
                        int fullPage = (int) ((pageHeight - margin * 2 - captionHeight - headerRowHeight - 10) / lineHeight);
                        if (available <= 0 || (lineOffset == 0 && maxLines <= fullPage && maxLines > available)) {
                            cs.close(); page = new PDPage(pageSize); doc.addPage(page);
                            cs = new PDPageContentStream(doc, page); y = pageHeight - margin;
                            y = drawPdfTableHeader(cs, groupTitle, headerLines, colWidths, boldFont,
                                margin, y, cellPaddingX, cellPaddingY, headerFontSize, headerRowHeight, tableWidth);
                            cs.setFont(regularFont, bodyFontSize);
                            available = Math.max(1, (int) ((y - margin - 5) / lineHeight));
                        }
                        int count = Math.min(maxLines - lineOffset, available);
                        float x = margin;
                        for (int i = 0; i < numCols; i++) {
                            List<String> lines = rowLines.get(i);
                            boolean stockBadge = headers.get(i).equals("Nivel de stock");
                            boolean transactionBadge = headers.get(i).equals("Estado") && headers.contains("Productos") && headers.contains("Tipo");
                            if ((stockBadge || transactionBadge) && lineOffset < lines.size()) {
                                String level = row.get(i);
                                int visibleLines = Math.min(count, lines.size() - lineOffset);
                                List<String> visible = lines.subList(lineOffset, lineOffset + visibleLines);
                                if (stockBadge) drawStockLevelBadge(cs, regularFont, bodyFontSize, level,
                                    visible, x, y, colWidths[i], lineHeight);
                                else drawTransactionStateBadge(cs, regularFont, bodyFontSize, level,
                                    visible, x, y, colWidths[i], lineHeight);
                            }
                            for (int j = 0; j < count && lineOffset + j < lines.size(); j++) {
                                cs.beginText(); cs.newLineAtOffset(x + cellPaddingX, y - j * lineHeight);
                                cs.showText(lines.get(lineOffset + j)); cs.endText();
                            }
                            cs.setNonStrokingColor(0f, 0f, 0f);
                            x += colWidths[i];
                        }
                        y -= count * lineHeight + 5;
                        lineOffset += count;
                    }
                    step.run();
                }
            }
            cs.close();
            doc.save(file);
            step.run(); // guardado
        }
    }

    private void drawStockLevelBadge(PDPageContentStream cs, PDFont font, int size, String label,
                                     List<String> lines, float x, float y, float columnWidth, float lineHeight) throws IOException {
        com.joshuamc1.sicam.core.enums.StockLevel level = java.util.Arrays.stream(
            com.joshuamc1.sicam.core.enums.StockLevel.values()).filter(v -> v.getLabel().equals(label)).findFirst().orElse(null);
        if (level == null) { cs.setNonStrokingColor(0f,0f,0f); return; }
        java.awt.Color color = java.awt.Color.decode(level.getColor());
        drawReportBadge(cs, font, size, lines, x, y, columnWidth, lineHeight, color,
            level == com.joshuamc1.sicam.core.enums.StockLevel.NEAR_MINIMUM);
    }

    private void drawTransactionStateBadge(PDPageContentStream cs, PDFont font, int size, String state,
                                          List<String> lines, float x, float y, float width, float lineHeight) throws IOException {
        java.awt.Color color = java.awt.Color.decode("Activo".equals(state) ? "#4CAF50"
            : "Invalidado".equals(state) ? "#F44336" : "#9E9E9E");
        drawReportBadge(cs, font, size, lines, x, y, width, lineHeight, color, false);
    }

    private void drawReportBadge(PDPageContentStream cs, PDFont font, int size, List<String> lines,
                                 float x, float y, float columnWidth, float lineHeight,
                                 java.awt.Color color, boolean darkText) throws IOException {
        float textWidth = 0;
        for (String line : lines) textWidth = Math.max(textWidth, font.getStringWidth(line) / 1000f * size);
        float left = x + 1, bottom = y - (lines.size() - 1) * lineHeight - 3;
        float w = Math.min(columnWidth - 2, textWidth + 6), h = (lines.size() - 1) * lineHeight + size + 5;
        float r = 3, k = 0.55228475f * r;
        cs.setNonStrokingColor(color);
        cs.moveTo(left+r,bottom); cs.lineTo(left+w-r,bottom);
        cs.curveTo(left+w-r+k,bottom,left+w,bottom+r-k,left+w,bottom+r);
        cs.lineTo(left+w,bottom+h-r);
        cs.curveTo(left+w,bottom+h-r+k,left+w-r+k,bottom+h,left+w-r,bottom+h);
        cs.lineTo(left+r,bottom+h);
        cs.curveTo(left+r-k,bottom+h,left,bottom+h-r+k,left,bottom+h-r);
        cs.lineTo(left,bottom+r);
        cs.curveTo(left,bottom+r-k,left+r-k,bottom,left+r,bottom); cs.closePath(); cs.fill();
        cs.setNonStrokingColor(darkText ? new java.awt.Color(45,35,0) : java.awt.Color.WHITE);
    }

    private float drawPdfTableHeader(PDPageContentStream cs, String title, List<List<String>> lines,
            float[] widths, PDFont font, float margin, float y, float paddingX, float paddingY,
            int fontSize, float height, float tableWidth) throws IOException {
        if (title != null) {
            cs.setFont(font, 10);
            for (String line : wrapTextByWidth(title, font, 10, tableWidth, Integer.MAX_VALUE)) {
                cs.beginText(); cs.newLineAtOffset(margin, y); cs.showText(line); cs.endText(); y -= 12;
            }
            y -= 8;
        }
        cs.setFont(font, fontSize);
        float x = margin;
        for (int i = 0; i < widths.length; i++) {
            float lineY = y;
            for (String line : lines.get(i)) {
                cs.beginText(); cs.newLineAtOffset(x + paddingX, lineY); cs.showText(line); cs.endText();
                lineY -= fontSize + paddingY;
            }
            x += widths[i];
        }
        return y - height - 4;
    }

    // --- Helpers de PDF para wrapping por ancho ---
    private List<String> wrapTextByWidth(String text, PDFont font, int fontSize, float maxWidth, int maxLines) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;
        for (String paragraph : text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            StringBuilder current = new StringBuilder();
            for (String word : paragraph.split("\\s+")) {
                if (word.isEmpty()) continue;
                String candidate = current.length() == 0 ? word : current + " " + word;
                if (safeStringWidth(font, candidate, fontSize) <= maxWidth) {
                    current.setLength(0); current.append(candidate);
                } else {
                    if (current.length() > 0) { lines.add(current.toString()); current.setLength(0); }
                    List<String> parts = splitLongWord(word, font, fontSize, maxWidth);
                    for (int i = 0; i < parts.size() - 1; i++) lines.add(parts.get(i));
                    if (!parts.isEmpty()) current.append(parts.get(parts.size() - 1));
                }
            }
            lines.add(current.toString());
        }
        if (lines.size() > maxLines) return new ArrayList<>(lines.subList(0, maxLines));
        return lines;
    }

    private float safeStringWidth(PDFont font, String text, int fontSize) {
        try {
            return font.getStringWidth(text)/1000 * fontSize;
        } catch (IOException ex) {
            Logs.warn(getClass(), "Fallo midiendo ancho de texto: {}", ex.getMessage());
            return text.length() * fontSize * 0.5f; // aproximación conservadora
        }
    }

    private List<String> splitLongWord(String word, PDFont font, int fontSize, float maxWidth) {
        List<String> parts = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (char c : word.toCharArray()) {
            sb.append(c);
            if (safeStringWidth(font, sb.toString(), fontSize) > maxWidth && sb.length()>1) {
                sb.setLength(sb.length()-1);
                parts.add(sb.toString());
                sb.setLength(0); sb.append(c);
            }
        }
        if (sb.length()>0) parts.add(sb.toString());
        return parts;
    }

    private static class PdfFonts { final PDFont regular; final PDFont bold; PdfFonts(PDFont r, PDFont b){ this.regular=r; this.bold=b;} }

    private PdfFonts loadFonts(PDDocument doc) throws IOException {
        // No usar PDType1Font como respaldo: inicializa el escaneo de fuentes del sistema.
        try (var regular = getClass().getResourceAsStream("/fonts/DejaVuSans.ttf");
             var bold = getClass().getResourceAsStream("/fonts/DejaVuSans-Bold.ttf")) {
            if (regular == null || bold == null) throw new IOException("Faltan las fuentes incluidas para los reportes PDF");
            return new PdfFonts(PDType0Font.load(doc, regular, true), PDType0Font.load(doc, bold, true));
        }
    }

    private String safe(String s) { return s == null ? "" : s; }

    private String quoteCsv(String s) {
        if (s == null) return "";
        String esc = s.replace("\"", "\"\"");
        if (s.contains("\"") || esc.contains(",") || esc.contains("\n") || esc.contains("\r")) {
            return "\"" + esc + "\"";
        }
        return esc;
    }

    private String getTimestamp() {
        return java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    }

    private void showSuccess(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Éxito");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
