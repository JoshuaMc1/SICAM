package com.joshuamc1.sicam.services;

import com.joshuamc1.sicam.core.DatabaseManager;
import com.joshuamc1.sicam.core.Session;
import com.joshuamc1.sicam.core.Authorization;
import com.joshuamc1.sicam.models.Report;
import com.joshuamc1.sicam.repositories.ReportRepository;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class ReportService {
    private final ReportRepository repo = new ReportRepository();

    public Report usersWithRoles() {
        requireReport("USUARIOS");
        return usersReport(false,false);
    }

    // --- NUEVOS: reportes de usuarios ---
    public Report usersExcludingRoot() {
        requireReport("USUARIOS");
        return usersReport(false,false);
    }

    public Report usersGroupedByRole() {
        requireReport("USUARIOS");
        return usersReport(true, false);
    }

    public Report usersReport(boolean grouped, boolean includeDeleted) {
        requireReport("USUARIOS");
        if (includeDeleted) Authorization.require("SISTEMA","VIEW_DELETED");
        return DatabaseManager.callInConnection(() -> repo.users(false, grouped, includeDeleted));
    }

    public Report inventoryReport(String title, String from, String to, boolean includeDeleted,
                                  com.joshuamc1.sicam.core.enums.StockLevel level) {
        return inventoryReport(title, from, to, includeDeleted, level, false);
    }

    public Report inventoryReport(String title, String from, String to, boolean includeDeleted,
                                  com.joshuamc1.sicam.core.enums.StockLevel level, boolean includeCode) {
        requireReport("INVENTARIO");
        if (includeDeleted) Authorization.require("SISTEMA","VIEW_DELETED");
        String[] range = from == null ? null : normalizeRange(from, to);
        String scope = title + (range == null ? "" : " — registro " + range[0] + " a " + range[1])
            + (includeDeleted ? " — incluye eliminados" : " — excluye eliminados")
            + (level == null ? "" : " — " + level.getLabel());
        return DatabaseManager.callInConnection(() -> repo.inventory(scope,
            range == null ? null : range[0], range == null ? null : range[1], includeDeleted, level, includeCode));
    }

    public Report transactionReport(String title, String type, String from, String to,
                                    Integer userId, Integer personId, String status) {
        requireReport(); requireTransactionTypes(type);
        if (userId != null) Authorization.require("USUARIOS","READ");
        if (personId != null) Authorization.require("PERSONAS","READ");
        String effectiveStatus = status;
        if (effectiveStatus == null && !Authorization.canAccess("SISTEMA","VIEW_DELETED")) effectiveStatus = "active";
        if (effectiveStatus != null && !"active".equals(effectiveStatus)) Authorization.require("SISTEMA","VIEW_DELETED");
        final String selectedStatus = effectiveStatus;
        String[] range = from == null ? null : normalizeRange(from, to);
        String typeLabel = type == null ? "" : switch(type) {
            case "in" -> "Ingresos"; case "out" -> "Egresos"; case "loan" -> "Préstamos";
            default -> throw new IllegalArgumentException("Tipo no válido");
        };
        String scope = title + (type == null ? "" : " — " + typeLabel)
            + (range == null ? "" : " — del " + LocalDate.parse(range[0]).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                + " al " + LocalDate.parse(range[1]).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
            + (userId == null ? "" : " — usuario #" + userId)
            + (personId == null ? "" : " — persona #" + personId);
        return DatabaseManager.callInConnection(() -> repo.transactionDetails(scope, type,
            range == null ? null : range[0], range == null ? null : range[1], userId, personId, selectedStatus));
    }

    // --- NUEVOS: reportes de inventario ---
    public Report inventoryComplete() {
        requireReport("INVENTARIO");
        return DatabaseManager.callInConnection(repo::inventoryComplete);
    }

    public Report inventoryLowStockReport() {
        requireReport("INVENTARIO");
        return DatabaseManager.callInConnection(repo::inventoryLowStockReport);
    }

    public Report inventoryOutOfStock() {
        requireReport("INVENTARIO");
        return DatabaseManager.callInConnection(repo::inventoryOutOfStock);
    }

    public Report inventoryMovements() {
        requireReport("INVENTARIO","INGRESOS","EGRESOS","PRESTAMOS");
        return transactionReport("Movimientos últimos 30 días",null,LocalDate.now().minusDays(29).toString(),LocalDate.now().toString(),null,null,null);
    }

    // --- REPORTES CON RANGO DE FECHAS PERSONALIZADO ---
    public Report inventoryMovementsByDateRange(String from, String to) {
        requireReport("INVENTARIO","INGRESOS","EGRESOS","PRESTAMOS");
        return transactionReport("Movimientos",null,from,to,null,null,null);
    }

    public Report inventoryProductsByDateRange(String from, String to) {
        requireReport("INVENTARIO");
        String[] r = normalizeRange(from, to);
        return DatabaseManager.callInConnection(() -> repo.inventoryProductsByDateRange(r[0], r[1]));
    }

    // --- REPORTES DE TRANSACCIONES ---
    public Report transactionsAll() {
        requireReport("INGRESOS","EGRESOS","PRESTAMOS");
        return transactionReport("Historial",null,null,null,null,null,null);
    }

    public Report transactionsByTypeReport(String type) {
        requireReport(); requireTransactionTypes(type);
        return transactionReport("Transacciones por tipo",type,null,null,null,null,null);
    }

    public Report transactionsByTypeAndDateRange(String type, String from, String to) {
        requireReport(); requireTransactionTypes(type);
        return transactionReport("Transacciones por tipo",type,from,to,null,null,null);
    }

    public Report transactionsByUserReport(int userId) {
        requireReport("INGRESOS","EGRESOS","PRESTAMOS");
        return transactionReport("Transacciones por usuario",null,null,null,userId,null,null);
    }

    public Report transactionsByPersonReport(int personId) {
        requireReport("INGRESOS","EGRESOS","PRESTAMOS");
        return transactionReport("Transacciones por persona",null,null,null,null,personId,null);
    }

    // --- REPORTES ANALÍTICOS Y DE GESTIÓN ---
    public Report productsMostMoved(String from, String to) {
        requireReport("INVENTARIO","INGRESOS","EGRESOS","PRESTAMOS");
        String[] r = normalizeRange(from, to);
        return DatabaseManager.callInConnection(() -> repo.productsMostMoved(r[0], r[1]));
    }

    public Report peopleWithActiveLoans() {
        requireReport("PRESTAMOS","PERSONAS");
        return DatabaseManager.callInConnection(repo::peopleWithActiveLoans);
    }

    public Report monthlyConsumption(int months) {
        requireReport("INVENTARIO","EGRESOS");
        return DatabaseManager.callInConnection(() -> repo.monthlyConsumption(months));
    }

    public Report productsWithoutMovement(int days) {
        requireReport("INVENTARIO","INGRESOS","EGRESOS","PRESTAMOS");
        return DatabaseManager.callInConnection(() -> repo.productsWithoutMovement(days));
    }

    public Report userActivity(int userId, String from, String to) {
        requireReport("USUARIOS","PERSONAS","INGRESOS","EGRESOS","PRESTAMOS");
        if ((from == null) != (to == null)) throw new IllegalArgumentException("Indique ambas fechas o todas las actividades");
        String[] r = from == null ? null : normalizeRange(from, to);
        String status = Authorization.canAccess("SISTEMA", "VIEW_DELETED") ? null : "active";
        return DatabaseManager.callInConnection(() -> repo.userActivity(userId, r == null ? null : r[0], r == null ? null : r[1], status));
    }

    public Report monthlyDashboard(String yearMonth) {
        requireReport("DASHBOARD");
        return DatabaseManager.callInConnection(() -> repo.monthlyDashboard(yearMonth));
    }

    public Report inventoryTotal() {
        requireReport("INVENTARIO");
        return DatabaseManager.callInConnection(repo::inventoryTotal);
    }

    public Report inventoryLowStock() {
        requireReport("INVENTARIO");
        return DatabaseManager.callInConnection(repo::inventoryLowStock);
    }

    public Report inventoryByDateRange(String from, String to) {
        requireReport("INVENTARIO");
        String[] r = normalizeRange(from, to);
        return DatabaseManager.callInConnection(() -> repo.inventoryByDateRange(r[0], r[1]));
    }

    public Report transactions(String from, String to) {
        requireReport("INGRESOS","EGRESOS","PRESTAMOS");
        return transactionReport("Transacciones",null,from,to,null,null,null);
    }

    public Report transactionsByType(String type, String from, String to) {
        requireReport(); requireTransactionTypes(type);
        return transactionReport("Transacciones por tipo",type,from,to,null,null,null);
    }

    public Report transactionsByPerson(int personId, String from, String to) {
        requireReport("INGRESOS","EGRESOS","PRESTAMOS");
        return transactionReport("Transacciones por persona",null,from,to,null,personId,null);
    }

    public Report peopleRegistered(String from, String to) {
        requireReport("PERSONAS");
        String[] r = normalizeRange(from, to);
        return DatabaseManager.callInConnection(() -> repo.peopleRegistered(r[0], r[1]));
    }

    private void requireReport(String... modules) {
        Authorization.require("REPORTES","READ");
        Authorization.require("REPORTES","GENERATE");
        for (String module: modules) Authorization.require(module,"READ");
    }
    private void requireTransactionTypes(String type) {
        if (type == null) { for(String module: new String[]{"INGRESOS","EGRESOS","PRESTAMOS"}) Authorization.require(module,"READ"); }
        else {
            String module = switch(type) { case "in" -> "INGRESOS"; case "out" -> "EGRESOS"; case "loan" -> "PRESTAMOS"; default -> throw new IllegalArgumentException("Tipo no válido"); };
            Authorization.require(module,"READ");
        }
    }

    // --- helpers ---
    @Contract("_, _ -> new")
    private String @NotNull [] normalizeRange(String from, String to) {
        // Defaults amplios si vienen nulos
        LocalDate f = (from != null && !from.isBlank()) ? LocalDate.parse(from) : LocalDate.of(1900, 1, 1);
        LocalDate t = (to != null && !to.isBlank()) ? LocalDate.parse(to) : LocalDate.of(9999, 12, 31);
        if (f.isAfter(t)) { LocalDate tmp = f; f = t; t = tmp; }
        return new String[]{ f.format(DateTimeFormatter.ISO_DATE), t.format(DateTimeFormatter.ISO_DATE) };
    }
}
