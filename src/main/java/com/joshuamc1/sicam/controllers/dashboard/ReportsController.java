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
import org.apache.pdfbox.pdmodel.font.PDType1Font;
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
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.concurrent.Task;
import javafx.scene.layout.VBox;
import javafx.application.Platform;
import java.util.logging.Logger;
import java.util.logging.Level;
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

    private final ReportService reportService = new ReportService();

    @FXML
    public void initialize() {
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
        com.joshuamc1.sicam.core.Logs.info(getClass(), "Configurando permisos de reportes para: {}",
            com.joshuamc1.sicam.core.Session.getCurrentUser().getString("username"));

        // Todos los botones de reportes requieren REPORTES.READ
        // Los reportes se generan si tiene el permiso READ del módulo correspondiente
        // Por simplicidad, mostramos todos los botones si tiene REPORTES.READ
        // Las validaciones específicas se harán en cada método

        com.joshuamc1.sicam.core.Logs.info(getClass(), "Permisos de reportes configurados - Validación granular por tipo de reporte");
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
        // Validar permisos de reportes y usuarios
        if (!validateReportPermission("USUARIOS")) return;

        try {
            Report report = reportService.usersExcludingRoot();
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
        // Validar permisos de reportes y usuarios
        if (!validateReportPermission("USUARIOS")) return;

        try {
            Report report = reportService.usersExcludingRoot();
            FileChooser fc = new FileChooser();
            fc.setTitle("Exportar Usuarios - PDF");
            fc.setInitialFileName("usuarios_" + getTimestamp() + ".pdf");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
            File f = fc.showSaveDialog(btnUsersNoRootPdf.getScene().getWindow());
            if (f == null) return;

            String description = "Lista completa de todos los usuarios del sistema.";
            exportToPdfAsync(report, f, description);
        } catch (Exception ex) {
            Logs.error(getClass(), "Error exportando usuarios sin root a PDF", ex);
            showError("No se pudo exportar el reporte: " + ex.getMessage());
        }
    }

    // ==================== USUARIOS AGRUPADOS POR ROL ====================

    private void exportUsersGroupedCsv() {
        try {
            Report report = reportService.usersGroupedByRole();
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
        try {
            Report report = reportService.usersGroupedByRole();
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
        // Validar permisos de reportes e inventario
        if (!validateReportPermission("INVENTARIO")) return;

        try {
            Report report = reportService.inventoryComplete();
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
        try {
            Report report = reportService.inventoryComplete();
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
        try {
            Report report = reportService.inventoryLowStockReport();
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
        try {
            Report report = reportService.inventoryLowStockReport();
            FileChooser fc = new FileChooser();
            fc.setTitle("Exportar Productos con Stock Bajo - PDF");
            fc.setInitialFileName("productos_stock_bajo_" + getTimestamp() + ".pdf");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
            File f = fc.showSaveDialog(btnInventoryLowStockPdf.getScene().getWindow());
            if (f == null) return;
            String description = "Productos cuyo stock actual es igual o menor al stock mínimo establecido. Ideal para planificar compras.";
            exportToPdfAsync(report, f, description);
        } catch (Exception ex) {
            Logs.error(getClass(), "Error exportando productos con stock bajo a PDF", ex);
            showError("No se pudo exportar el reporte: " + ex.getMessage());
        }
    }

    // ==================== PRODUCTOS SIN STOCK ====================

    private void exportInventoryOutOfStockCsv() {
        try {
            Report report = reportService.inventoryOutOfStock();
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
        try {
            Report report = reportService.inventoryOutOfStock();
            FileChooser fc = new FileChooser();
            fc.setTitle("Exportar Productos Sin Stock - PDF");
            fc.setInitialFileName("productos_sin_stock_" + getTimestamp() + ".pdf");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
            File f = fc.showSaveDialog(btnInventoryOutOfStockPdf.getScene().getWindow());
            if (f == null) return;
            String description = "Productos que actualmente tienen stock en cero. Requieren reabastecimiento urgente.";
            exportToPdfAsync(report, f, description);
        } catch (Exception ex) {
            Logs.error(getClass(), "Error exportando productos sin stock a PDF", ex);
            showError("No se pudo exportar el reporte: " + ex.getMessage());
        }
    }

    // ==================== MOVIMIENTOS DE INVENTARIO ====================

    private void exportInventoryMovementsCsv() {
        try {
            Report report = reportService.inventoryMovements();
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
        try {
            Report report = reportService.inventoryMovements();
            FileChooser fc = new FileChooser();
            fc.setTitle("Exportar Movimientos de Inventario - PDF");
            fc.setInitialFileName("movimientos_inventario_" + getTimestamp() + ".pdf");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
            File f = fc.showSaveDialog(btnInventoryMovementsPdf.getScene().getWindow());
            if (f == null) return;
            String description = "Historial de todas las transacciones: ingresos, egresos y préstamos realizados en el último mes.";
            exportToPdfAsync(report, f, description);
        } catch (Exception ex) {
            Logs.error(getClass(), "Error exportando movimientos de inventario a PDF", ex);
            showError("No se pudo exportar el reporte: " + ex.getMessage());
        }
    }

    // ==================== MOVIMIENTOS POR RANGO DE FECHAS ====================

    private void exportMovementsByDateRangeCsv() {
        DateRangeDialog.DateRange dateRange = DateRangeDialog.showDialog("Movimientos por Rango de Fechas");
        if (dateRange == null) return;

        try {
            Report report = reportService.inventoryMovementsByDateRange(
                dateRange.getFromString(),
                dateRange.getToString()
            );
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
        DateRangeDialog.DateRange dateRange = DateRangeDialog.showDialog("Movimientos por Rango de Fechas");
        if (dateRange == null) return;

        try {
            Report report = reportService.inventoryMovementsByDateRange(
                dateRange.getFromString(),
                dateRange.getToString()
            );
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
        DateRangeDialog.DateRange dateRange = DateRangeDialog.showDialog("Productos Registrados por Fecha");
        if (dateRange == null) return;

        try {
            Report report = reportService.inventoryProductsByDateRange(
                dateRange.getFromString(),
                dateRange.getToString()
            );
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
        DateRangeDialog.DateRange dateRange = DateRangeDialog.showDialog("Productos Registrados por Fecha");
        if (dateRange == null) return;

        try {
            Report report = reportService.inventoryProductsByDateRange(
                dateRange.getFromString(),
                dateRange.getToString()
            );
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
        try {
            Report report = reportService.transactionsAll();
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
        try {
            Report report = reportService.transactionsAll();
            FileChooser fc = new FileChooser();
            fc.setTitle("Exportar Historial de Transacciones - PDF");
            fc.setInitialFileName("transacciones_historial_" + getTimestamp() + ".pdf");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
            File f = fc.showSaveDialog(btnTransactionsAllPdf.getScene().getWindow());
            if (f == null) return;
            String description = "Todas las transacciones registradas en el sistema";
            exportToPdfAsync(report, f, description);
        } catch (Exception ex) {
            Logs.error(getClass(), "Error exportando historial de transacciones a PDF", ex);
            showError("No se pudo exportar el reporte: " + ex.getMessage());
        }
    }

    // ==================== TRANSACCIONES - POR TIPO ====================
    private void exportTransactionsByTypeCsv() {
        TransactionTypeDialog.TransactionType type = TransactionTypeDialog.showDialog("Transacciones por Tipo");
        if (type == null) return;
        try {
            Report report = reportService.transactionsByTypeReport(type.getCode());
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
        TransactionTypeDialog.TransactionType type = TransactionTypeDialog.showDialog("Transacciones por Tipo");
        if (type == null) return;
        try {
            Report report = reportService.transactionsByTypeReport(type.getCode());
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
        TransactionTypeDialog.TransactionType type = TransactionTypeDialog.showDialog("Transacciones por Tipo y Fecha");
        if (type == null) return;
        DateRangeDialog.DateRange dateRange = DateRangeDialog.showDialog("Seleccione el rango de fechas");
        if (dateRange == null) return;
        try {
            Report report = reportService.transactionsByTypeAndDateRange(type.getCode(), dateRange.getFromString(), dateRange.getToString());
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
        TransactionTypeDialog.TransactionType type = TransactionTypeDialog.showDialog("Transacciones por Tipo y Fecha");
        if (type == null) return;
        DateRangeDialog.DateRange dateRange = DateRangeDialog.showDialog("Seleccione el rango de fechas");
        if (dateRange == null) return;
        try {
            Report report = reportService.transactionsByTypeAndDateRange(type.getCode(), dateRange.getFromString(), dateRange.getToString());
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
        UserSelectionDialog.UserSelection userSel = UserSelectionDialog.showDialog("Transacciones por Usuario");
        if (userSel == null) return;
        try {
            Report report = reportService.transactionsByUserReport(userSel.getUserId());
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
        UserSelectionDialog.UserSelection userSel = UserSelectionDialog.showDialog("Transacciones por Usuario");
        if (userSel == null) return;
        try {
            Report report = reportService.transactionsByUserReport(userSel.getUserId());
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
        PersonSelectionDialog.PersonSelection personSel = PersonSelectionDialog.showDialog("Transacciones por Persona");
        if (personSel == null) return;
        try {
            Report report = reportService.transactionsByPersonReport(personSel.getPersonId());
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
        PersonSelectionDialog.PersonSelection personSel = PersonSelectionDialog.showDialog("Transacciones por Persona");
        if (personSel == null) return;
        try {
            Report report = reportService.transactionsByPersonReport(personSel.getPersonId());
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
        DateRangeDialog.DateRange dateRange = DateRangeDialog.showDialog("Productos Más Movidos");
        if (dateRange == null) return;
        try {
            Report report = reportService.productsMostMoved(dateRange.getFromString(), dateRange.getToString());
            FileChooser fc = new FileChooser();
            fc.setTitle("Exportar Productos Más Movidos - CSV");
            fc.setInitialFileName("productos_mas_movidos_" + getTimestamp() + ".csv");
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
        DateRangeDialog.DateRange dateRange = DateRangeDialog.showDialog("Productos Más Movidos");
        if (dateRange == null) return;
        try {
            Report report = reportService.productsMostMoved(dateRange.getFromString(), dateRange.getToString());
            FileChooser fc = new FileChooser();
            fc.setTitle("Exportar Productos Más Movidos - PDF");
            fc.setInitialFileName("productos_mas_movidos_" + getTimestamp() + ".pdf");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
            File f = fc.showSaveDialog(btnProductsMostMovedPdf.getScene().getWindow());
            if (f == null) return;
            String description = String.format("Top 20 productos con más transacciones desde %s hasta %s.",
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
        try {
            Report report = reportService.peopleWithActiveLoans();
            FileChooser fc = new FileChooser();
            fc.setTitle("Exportar Personas con Préstamos Activos - PDF");
            fc.setInitialFileName("prestamos_activos_" + getTimestamp() + ".pdf");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
            File f = fc.showSaveDialog(btnPeopleWithLoansPdf.getScene().getWindow());
            if (f == null) return;
            String description = "Control de préstamos activos con información detallada de días transcurridos.";
            exportToPdfAsync(report, f, description);
        } catch (Exception ex) {
            Logs.error(getClass(), "Error exportando préstamos activos a PDF", ex);
            showError("No se pudo exportar el reporte: " + ex.getMessage());
        }
    }

    // ==================== REPORTES ANALÍTICOS - CONSUMO MENSUAL ====================
    private void exportMonthlyConsumptionCsv() {
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
        try {
            Report report = reportService.monthlyConsumption(12); // Últimos 12 meses
            FileChooser fc = new FileChooser();
            fc.setTitle("Exportar Consumo Mensual - PDF");
            fc.setInitialFileName("consumo_mensual_" + getTimestamp() + ".pdf");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
            File f = fc.showSaveDialog(btnMonthlyConsumptionPdf.getScene().getWindow());
            if (f == null) return;
            String description = "Análisis de consumo de productos de los últimos 12 meses con desglose mensual.";
            exportToPdfAsync(report, f, description);
        } catch (Exception ex) {
            Logs.error(getClass(), "Error exportando consumo mensual a PDF", ex);
            showError("No se pudo exportar el reporte: " + ex.getMessage());
        }
    }

    // ==================== REPORTES ANALÍTICOS - PRODUCTOS SIN MOVIMIENTO ====================
    private void exportProductsNoMovementCsv() {
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
        try {
            Report report = reportService.productsWithoutMovement(90); // Últimos 90 días
            FileChooser fc = new FileChooser();
            fc.setTitle("Exportar Productos Sin Movimiento - PDF");
            fc.setInitialFileName("productos_sin_movimiento_" + getTimestamp() + ".pdf");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
            File f = fc.showSaveDialog(btnProductsNoMovementPdf.getScene().getWindow());
            if (f == null) return;
            String description = "Productos que no han tenido movimientos en los últimos 90 días. Detecta productos obsoletos.";
            exportToPdfAsync(report, f, description);
        } catch (Exception ex) {
            Logs.error(getClass(), "Error exportando productos sin movimiento a PDF", ex);
            showError("No se pudo exportar el reporte: " + ex.getMessage());
        }
    }

    // ==================== REPORTES ANALÍTICOS - ACTIVIDAD POR USUARIO ====================
    private void exportUserActivityCsv() {
        UserSelectionDialog.UserSelection userSel = UserSelectionDialog.showDialog("Actividad por Usuario");
        if (userSel == null) return;
        DateRangeDialog.DateRange dateRange = DateRangeDialog.showDialog("Seleccione el rango de fechas");
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
        UserSelectionDialog.UserSelection userSel = UserSelectionDialog.showDialog("Actividad por Usuario");
        if (userSel == null) return;
        DateRangeDialog.DateRange dateRange = DateRangeDialog.showDialog("Seleccione el rango de fechas");
        if (dateRange == null) return;
        try {
            Report report = reportService.userActivity(userSel.getUserId(), dateRange.getFromString(), dateRange.getToString());
            FileChooser fc = new FileChooser();
            fc.setTitle("Exportar Actividad por Usuario - PDF");
            fc.setInitialFileName("actividad_usuario_" + userSel.getUserId() + "_" + getTimestamp() + ".pdf");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
            File f = fc.showSaveDialog(btnUserActivityPdf.getScene().getWindow());
            if (f == null) return;
            String description = String.format("Actividad detallada del usuario %s desde %s hasta %s con desglose diario.",
                userSel.getUserName(),
                dateRange.getFrom().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                dateRange.getTo().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            exportToPdfAsync(report, f, description);
        } catch (Exception ex) {
            Logs.error(getClass(), "Error exportando actividad de usuario a PDF", ex);
            showError("No se pudo exportar el reporte: " + ex.getMessage());
        }
    }

    // ==================== REPORTES ANALÍTICOS - DASHBOARD MENSUAL ====================
    private void exportMonthlyDashboardCsv() {
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
            exportMonthlyDashboardRichPdf(report, f, monthYear.getFormatted());
        } catch (Exception ex) {
            Logs.error(getClass(), "Error exportando dashboard mensual a PDF", ex);
            showError("No se pudo exportar el reporte: " + ex.getMessage());
        }
    }

    // Exportación enriquecida con layout de KPIs y gráfico simple
    private void exportMonthlyDashboardRichPdf(Report report, File file, String periodo) {
        try (org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument()) {
            org.apache.pdfbox.pdmodel.font.PDType0Font font = loadEmbeddedFont(doc);
            // Si no se carga fuente embebida se usarán las estándar
            PDFont regularPdfFont = font != null ? font : PDType1Font.HELVETICA;
            PDFont boldPdfFont = font != null ? font : PDType1Font.HELVETICA_BOLD;
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
                if (row.size() < 2) continue;
                String k = row.get(0);
                String v = row.get(1);
                if (k.startsWith("---")) continue; // separador
                map.put(k, v);
            }

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
            cs.showText("Distribución de Transacciones del Mes");
            cs.endText();

            long max = java.util.Collections.max(java.util.Arrays.asList(ingresos, egresos, prestamos, 1L));
            float barAreaHeight = chartHeight - 50;
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
            doc.save(file);
            showSuccess("Dashboard mensual PDF exportado: " + file.getName());
        } catch (Exception e) {
            Logs.error(getClass(), "Error creando PDF enriquecido dashboard: {}", e.getMessage());
            showError("Fallo exportando dashboard mensual: " + e.getMessage());
        }
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
            bw.write(String.join(",", report.getHeaders()));
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
        configurePdfEnvironment();
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

            PDPage page = new PDPage(PDRectangle.LETTER);
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
            cs.setFont(boldFont, 18);
            cs.beginText(); cs.newLineAtOffset(margin + 75, y + 35); cs.showText(safe(report.getTitle())); cs.endText();
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
            List<String> headers = report.getHeaders(); int numCols = headers.size();
            float tableWidth = pageWidth - 2 * margin; float colWidth = tableWidth / numCols;
            float cellPaddingX = 3f; float cellPaddingY = 2f; int headerFontSize = 9; int bodyFontSize = 8;
            cs.setFont(boldFont, headerFontSize);
            float headerMaxWidth = colWidth - cellPaddingX * 2; float headerRowHeight = 0f;
            List<List<String>> headerLines = new ArrayList<>();
            for (String h : headers) {
                List<String> lines = wrapTextByWidth(h, boldFont, headerFontSize, headerMaxWidth, 3);
                headerLines.add(lines);
                headerRowHeight = Math.max(headerRowHeight, lines.size() * (headerFontSize + cellPaddingY));
            }
            headerRowHeight = Math.max(headerRowHeight, headerFontSize + 6);
            for (int i = 0; i < numCols; i++) {
                float x = margin + i * colWidth; List<String> lines = headerLines.get(i); float lineY = y;
                for (String line : lines) { cs.beginText(); cs.newLineAtOffset(x + cellPaddingX, lineY); cs.showText(line); cs.endText(); lineY -= (headerFontSize + cellPaddingY);} }
            y -= headerRowHeight + 4f;
            step.run(); // encabezados tabla

            cs.setFont(regularFont, bodyFontSize);
            for (List<String> row : report.getRows()) {
                if (cancelled.get()) { cs.close(); return; }
                List<List<String>> rowLines = new ArrayList<>(); float rowHeight = 0f;
                for (int i = 0; i < numCols; i++) {
                    String cellValue = (i < row.size()) ? row.get(i) : ""; if (cellValue == null) cellValue = "";
                    if (headers.get(i).equalsIgnoreCase("Rol")) { cellValue = Roles.getRoleFullName(cellValue); }
                    List<String> lines = wrapTextByWidth(cellValue, regularFont, bodyFontSize, colWidth - cellPaddingX * 2, 6);
                    rowLines.add(lines); rowHeight = Math.max(rowHeight, lines.size() * (bodyFontSize + cellPaddingY));
                }
                rowHeight = Math.max(rowHeight, bodyFontSize + 6);
                if (y - rowHeight < margin) {
                    cs.close();
                    page = new PDPage(PDRectangle.LETTER); doc.addPage(page); y = pageHeight - margin;
                    cs = new PDPageContentStream(doc, page); cs.setFont(boldFont, headerFontSize);
                    for (int i = 0; i < numCols; i++) { float x = margin + i * colWidth; List<String> lines = headerLines.get(i); float lineY = y; for (String line : lines) { cs.beginText(); cs.newLineAtOffset(x + cellPaddingX, lineY); cs.showText(line); cs.endText(); lineY -= (headerFontSize + cellPaddingY);} }
                    y -= headerRowHeight + 4f; cs.setFont(regularFont, bodyFontSize);
                }
                for (int i = 0; i < numCols; i++) {
                    float x = margin + i * colWidth; List<String> lines = rowLines.get(i); float lineY = y;
                    for (String line : lines) { cs.beginText(); cs.newLineAtOffset(x + cellPaddingX, lineY); cs.showText(line); cs.endText(); lineY -= (bodyFontSize + cellPaddingY);} }
            y -= rowHeight + 2f;
            step.run(); // fila procesada
            }
            cs.close();
            doc.save(file);
            step.run(); // guardado
        }
    }

    // --- Helpers de PDF para wrapping por ancho ---
    private List<String> wrapTextByWidth(String text, PDFont font, int fontSize, float maxWidth, int maxLines) {
        List<String> result = new ArrayList<>();
        if (text == null) return result;
        String sanitized = text.replace('\r',' ').replace('\n',' ');
        String[] words = sanitized.split(" ");
        StringBuilder current = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            String candidate = current.length()==0? w : current + " " + w;
            float width = safeStringWidth(font, candidate, fontSize);
            if (width <= maxWidth) {
                current.setLength(0); current.append(candidate);
            } else {
                if (current.length()>0) result.add(current.toString());
                float wordWidth = safeStringWidth(font, w, fontSize);
                if (wordWidth > maxWidth && w.length() > 1) {
                    result.addAll(splitLongWord(w, font, fontSize, maxWidth));
                } else {
                    current.setLength(0); current.append(w);
                }
            }
            if (result.size() >= maxLines) break;
        }
        if (result.size() < maxLines && current.length()>0) result.add(current.toString());
        // Ellipsis si se alcanzó límite de líneas y aún queda texto
        if (result.size() >= maxLines && sanitized.length() > result.stream().mapToInt(String::length).sum()) {
            String last = result.get(result.size()-1);
            if (!last.endsWith("...")) {
                while (safeStringWidth(font, last + "...", fontSize) > maxWidth && last.length()>1) {
                    last = last.substring(0, last.length()-1);
                }
                result.set(result.size()-1, last + "...");
            }
        }
        return result;
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

    private PdfFonts loadFonts(PDDocument doc) {
        // Intenta cargar fuentes embebidas para evitar escaneo del sistema (que provoca EOF en fuentes corruptas)
        try (var reg = getClass().getResourceAsStream("/fonts/DejaVuSans.ttf");
             var bold = getClass().getResourceAsStream("/fonts/DejaVuSans-Bold.ttf")) {
            if (reg != null && bold != null) {
                PDFont r = PDType0Font.load(doc, reg, true);
                PDFont b = PDType0Font.load(doc, bold, true);
                return new PdfFonts(r, b);
            } else if (reg != null) {
                PDFont r = PDType0Font.load(doc, reg, true);
                return new PdfFonts(r, r); // Simula bold
            }
        } catch (Exception ex) {
            Logs.warn(getClass(), "No se pudieron cargar fuentes embebidas: {}", ex.getMessage());
        }
        // Fallback a fuentes estándar; puede generar escaneo, por eso ya configuramos logging
        return new PdfFonts(PDType1Font.HELVETICA, PDType1Font.HELVETICA_BOLD);
    }

    // Carga simple de una fuente embebida (fallback a Helvetica)
    private org.apache.pdfbox.pdmodel.font.PDType0Font loadEmbeddedFont(org.apache.pdfbox.pdmodel.PDDocument doc) {
        try (var is = getClass().getResourceAsStream("/fonts/DejaVuSans.ttf")) {
            if (is != null) {
                return org.apache.pdfbox.pdmodel.font.PDType0Font.load(doc, is, true);
            }
        } catch (Exception e) {
            Logs.warn(getClass(), "No se pudo cargar fuente embebida: {}", e.getMessage());
        }
        return null;
    }

    private static volatile boolean pdfEnvConfigured = false;
    private void configurePdfEnvironment() {
        if (pdfEnvConfigured) return;
        pdfEnvConfigured = true;
        try {
            // Establecer cache en temp para evitar re-build repetido
            Path cachePath = Path.of(System.getProperty("java.io.tmpdir"), "pdfbox-font-cache");
            System.setProperty("pdfbox.fontcache", cachePath.toString());
            // Silenciar WARNING de fuentes corruptas
            Logger.getLogger("org.apache.pdfbox.pdmodel.font").setLevel(Level.SEVERE);
            Logger.getLogger("org.apache.fontbox.ttf").setLevel(Level.SEVERE);
        } catch (Exception ex) {
            Logs.warn(getClass(), "No se pudo configurar entorno PDF: {}", ex.getMessage());
        }
    }

    private String safe(String s) { return s == null ? "" : s; }

    private String quoteCsv(String s) {
        if (s == null) return "";
        String esc = s.replace("\"", "\"\"");
        if (esc.contains(",") || esc.contains("\n") || esc.contains("\r")) {
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
