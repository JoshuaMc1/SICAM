package com.joshuamc1.sicam.services;

import com.joshuamc1.sicam.core.DatabaseManager;
import com.joshuamc1.sicam.core.Session;
import com.joshuamc1.sicam.models.Report;
import com.joshuamc1.sicam.repositories.ReportRepository;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class ReportService {
    private final ReportRepository repo = new ReportRepository();

    public Report usersWithRoles() {
        return DatabaseManager.callInConnection(repo::usersWithRoles);
    }

    // --- NUEVOS: reportes de usuarios ---
    public Report usersExcludingRoot() {
        // Si el usuario actual es ROOT, incluirlo en el reporte
        boolean isRoot = "ROOT".equalsIgnoreCase(Session.getCurrentUserRole());
        if (isRoot) {
            return DatabaseManager.callInConnection(repo::usersWithRoles);
        }
        return DatabaseManager.callInConnection(repo::usersExcludingRoot);
    }

    public Report usersGroupedByRole() {
        // Si el usuario actual es ROOT, incluirlo en el reporte
        boolean isRoot = "ROOT".equalsIgnoreCase(Session.getCurrentUserRole());
        if (isRoot) {
            return DatabaseManager.callInConnection(repo::usersWithRoles);
        }
        return DatabaseManager.callInConnection(repo::usersGroupedByRole);
    }

    // --- NUEVOS: reportes de inventario ---
    public Report inventoryComplete() {
        return DatabaseManager.callInConnection(repo::inventoryComplete);
    }

    public Report inventoryLowStockReport() {
        return DatabaseManager.callInConnection(repo::inventoryLowStockReport);
    }

    public Report inventoryOutOfStock() {
        return DatabaseManager.callInConnection(repo::inventoryOutOfStock);
    }

    public Report inventoryMovements() {
        return DatabaseManager.callInConnection(repo::inventoryMovements);
    }

    // --- REPORTES CON RANGO DE FECHAS PERSONALIZADO ---
    public Report inventoryMovementsByDateRange(String from, String to) {
        String[] r = normalizeRange(from, to);
        return DatabaseManager.callInConnection(() -> repo.inventoryMovementsByDateRange(r[0], r[1]));
    }

    public Report inventoryProductsByDateRange(String from, String to) {
        String[] r = normalizeRange(from, to);
        return DatabaseManager.callInConnection(() -> repo.inventoryProductsByDateRange(r[0], r[1]));
    }

    // --- REPORTES DE TRANSACCIONES ---
    public Report transactionsAll() {
        return DatabaseManager.callInConnection(repo::transactionsAll);
    }

    public Report transactionsByTypeReport(String type) {
        return DatabaseManager.callInConnection(() -> repo.transactionsByTypeReport(type));
    }

    public Report transactionsByTypeAndDateRange(String type, String from, String to) {
        String[] r = normalizeRange(from, to);
        return DatabaseManager.callInConnection(() -> repo.transactionsByTypeAndDateRange(type, r[0], r[1]));
    }

    public Report transactionsByUserReport(int userId) {
        return DatabaseManager.callInConnection(() -> repo.transactionsByUserReport(userId));
    }

    public Report transactionsByPersonReport(int personId) {
        return DatabaseManager.callInConnection(() -> repo.transactionsByPersonReport(personId));
    }

    // --- REPORTES ANALÍTICOS Y DE GESTIÓN ---
    public Report productsMostMoved(String from, String to) {
        String[] r = normalizeRange(from, to);
        return DatabaseManager.callInConnection(() -> repo.productsMostMoved(r[0], r[1]));
    }

    public Report peopleWithActiveLoans() {
        return DatabaseManager.callInConnection(repo::peopleWithActiveLoans);
    }

    public Report monthlyConsumption(int months) {
        return DatabaseManager.callInConnection(() -> repo.monthlyConsumption(months));
    }

    public Report productsWithoutMovement(int days) {
        return DatabaseManager.callInConnection(() -> repo.productsWithoutMovement(days));
    }

    public Report userActivity(int userId, String from, String to) {
        String[] r = normalizeRange(from, to);
        return DatabaseManager.callInConnection(() -> repo.userActivity(userId, r[0], r[1]));
    }

    public Report monthlyDashboard(String yearMonth) {
        return DatabaseManager.callInConnection(() -> repo.monthlyDashboard(yearMonth));
    }

    public Report inventoryTotal() {
        return DatabaseManager.callInConnection(repo::inventoryTotal);
    }

    public Report inventoryLowStock() {
        return DatabaseManager.callInConnection(repo::inventoryLowStock);
    }

    public Report inventoryByDateRange(String from, String to) {
        String[] r = normalizeRange(from, to);
        return DatabaseManager.callInConnection(() -> repo.inventoryByDateRange(r[0], r[1]));
    }

    public Report transactions(String from, String to) {
        String[] r = normalizeRange(from, to);
        return DatabaseManager.callInConnection(() -> repo.transactions(r[0], r[1]));
    }

    public Report transactionsByType(String type, String from, String to) {
        String[] r = normalizeRange(from, to);
        return DatabaseManager.callInConnection(() -> repo.transactionsByType(type, r[0], r[1]));
    }

    public Report transactionsByPerson(int personId, String from, String to) {
        String[] r = normalizeRange(from, to);
        return DatabaseManager.callInConnection(() -> repo.transactionsByPerson(personId, r[0], r[1]));
    }

    public Report peopleRegistered(String from, String to) {
        String[] r = normalizeRange(from, to);
        return DatabaseManager.callInConnection(() -> repo.peopleRegistered(r[0], r[1]));
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
