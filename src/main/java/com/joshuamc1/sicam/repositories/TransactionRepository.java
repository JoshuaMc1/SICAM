package com.joshuamc1.sicam.repositories;

import com.joshuamc1.sicam.models.Transaction;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class TransactionRepository {
    public Transaction create(@NotNull Transaction transaction) {
        transaction.saveIt();
        return transaction;
    }

    public Transaction findById(Integer id) {
        return Transaction.findById(id);
    }

    // ===== MÉTODOS DE BÚSQUEDA CON FILTRADO DE ESTADO =====

    public List<Transaction> findByProduct(Integer productId) {
        return Transaction.where("product_id = ?", productId).orderBy("created_at DESC");
    }

    /**
     * Busca transacciones por tipo (excluye invalidadas por defecto)
     */
    public List<Transaction> findByType(String type) {
        return Transaction.where("type = ? AND status = ?", type, Transaction.STATUS_ACTIVE)
            .orderBy("created_at DESC");
    }

    /**
     * Busca transacciones por tipo incluyendo invalidadas
     */
    public List<Transaction> findByTypeIncludingInvalidated(String type) {
        return Transaction.where("type = ?", type)
            .orderBy("created_at DESC");
    }

    /**
     * Busca solo transacciones invalidadas por tipo
     */
    public List<Transaction> findInvalidatedByType(String type) {
        return Transaction.where("type = ? AND status = ?", type, Transaction.STATUS_INVALIDATED)
            .orderBy("invalidated_at DESC");
    }

    // ===== MÉTODOS DE CONTEO =====

    /**
     * Cuenta transacciones por tipo (solo activas)
     */
    public long countByType(String type) {
        Long c = Transaction.count("type = ? AND status = ?", type, Transaction.STATUS_ACTIVE);
        return c == null ? 0L : c;
    }

    /**
     * Obtiene página de transacciones por tipo (solo activas)
     */
    public List<Transaction> findByTypePage(String type, int limit, int offset) {
        return Transaction.where("type = ? AND status = ?", type, Transaction.STATUS_ACTIVE)
                .orderBy("created_at DESC")
                .limit(limit)
                .offset(offset);
    }

    /**
     * Cuenta movimientos del día (solo activos)
     */
    public long countToday() {
        Long count = Transaction.count("DATE(created_at) = CURDATE() AND status = ?",
            Transaction.STATUS_ACTIVE);
        return count == null ? 0L : count;
    }

    /**
     * Obtiene últimas N transacciones (solo activas)
     */
    public List<Transaction> findLatest(int limit) {
        return Transaction.where("status = ?", Transaction.STATUS_ACTIVE)
            .orderBy("created_at DESC")
            .limit(limit);
    }

    public long countThisMonth() {
        Long c = Transaction.count(
            "YEAR(created_at) = YEAR(CURDATE()) AND MONTH(created_at) = MONTH(CURDATE()) AND status = ?",
            Transaction.STATUS_ACTIVE
        );
        return c == null ? 0L : c;
    }

    public long countLastMonth() {
        Long c = Transaction.count(
            "YEAR(created_at) = YEAR(DATE_SUB(CURDATE(), INTERVAL 1 MONTH)) AND MONTH(created_at) = MONTH(DATE_SUB(CURDATE(), INTERVAL 1 MONTH)) AND status = ?",
            Transaction.STATUS_ACTIVE
        );
        return c == null ? 0L : c;
    }

    /**
     * Cuenta préstamos activos pendientes
     */
    public long countActiveLoans() {
        Long c = Transaction.count(
            "type = ? AND loan_status = ? AND status = ?",
            Transaction.TYPE_LOAN,
            Transaction.LOAN_STATUS_PENDING,
            Transaction.STATUS_ACTIVE
        );
        return c == null ? 0L : c;
    }

    public List<Transaction> findLast30Days() {
        return Transaction.where(
            "created_at >= DATE_SUB(CURDATE(), INTERVAL 30 DAY) AND status = ?",
            Transaction.STATUS_ACTIVE
        ).orderBy("created_at DESC");
    }

    public java.util.Map<String, Long> getMostMovedProducts(int limit) {
        // ...existing code...
        java.util.Map<String, Long> result = new java.util.LinkedHashMap<>();

        List<com.joshuamc1.sicam.models.TransactionItem> allItems =
            com.joshuamc1.sicam.models.TransactionItem.where(
                "status = ?",
                com.joshuamc1.sicam.models.TransactionItem.STATUS_ACTIVE
            );

        java.util.Map<Integer, Long> countByProductId = new java.util.HashMap<>();

        for (var item : allItems) {
            Integer productId = item.getInteger("product_id");
            if (productId != null) {
                countByProductId.merge(productId, 1L, Long::sum);
            }
        }

        countByProductId.entrySet().stream()
            .sorted((e1, e2) -> Long.compare(e2.getValue(), e1.getValue()))
            .limit(limit)
            .forEach(entry -> {
                com.joshuamc1.sicam.models.Product p =
                    com.joshuamc1.sicam.models.Product.findById(entry.getKey());
                if (p != null && p.isActive()) {
                    String name = p.getString("name");
                    result.put(name != null ? name : "N/A", entry.getValue());
                }
            });

        return result;
    }

    public java.util.Map<String, Long> getTransactionsByType() {
        java.util.Map<String, Long> result = new java.util.HashMap<>();
        String[] types = {"in", "out", "loan"};
        for (String type : types) {
            Long count = Transaction.count("type = ? AND status = ?", type, Transaction.STATUS_ACTIVE);
            if (count != null && count > 0) {
                result.put(type, count);
            }
        }
        return result;
    }

    public java.util.Map<Integer, Long> getWeeklyActivity() {
        // ...existing code...
        java.util.Map<Integer, Long> result = new java.util.HashMap<>();

        List<Transaction> recent = Transaction.where(
            "created_at >= DATE_SUB(CURDATE(), INTERVAL 30 DAY) AND status = ?",
            Transaction.STATUS_ACTIVE
        );

        for (Transaction t : recent) {
            String createdAt = t.getString("created_at");
            if (createdAt != null && createdAt.length() >= 10) {
                try {
                    java.time.LocalDate date = java.time.LocalDate.parse(createdAt.substring(0, 10));
                    int dayOfWeek = date.getDayOfWeek().getValue();
                    result.merge(dayOfWeek, 1L, Long::sum);
                } catch (Exception ignored) {}
            }
        }

        return result;
    }

    public long countByTypeThisMonth(String type) {
        Long c = Transaction.count(
            "type = ? AND YEAR(created_at) = YEAR(CURDATE()) AND MONTH(created_at) = MONTH(CURDATE()) AND status = ?",
            type,
            Transaction.STATUS_ACTIVE
        );
        return c == null ? 0L : c;
    }

    public long countByTypeLastMonth(String type) {
        Long c = Transaction.count(
            "type = ? AND YEAR(created_at) = YEAR(DATE_SUB(CURDATE(), INTERVAL 1 MONTH)) AND MONTH(created_at) = MONTH(DATE_SUB(CURDATE(), INTERVAL 1 MONTH)) AND status = ?",
            type,
            Transaction.STATUS_ACTIVE
        );
        return c == null ? 0L : c;
    }

    public java.util.Map<String, java.util.Map<String, Long>> getQuarterlyTrend() {
        // ...existing code...
        java.util.Map<String, java.util.Map<String, Long>> result = new java.util.LinkedHashMap<>();

        List<Transaction> recent = Transaction.where(
            "created_at >= DATE_SUB(CURDATE(), INTERVAL 3 MONTH) AND status = ?",
            Transaction.STATUS_ACTIVE
        );

        for (Transaction t : recent) {
            String createdAt = t.getString("created_at");
            String type = t.getString("type");

            if (createdAt != null && createdAt.length() >= 7 && type != null) {
                try {
                    String month = createdAt.substring(0, 7);

                    result.computeIfAbsent(month, k -> new java.util.HashMap<>());
                    result.get(month).merge(type, 1L, Long::sum);
                } catch (Exception ignored) {}
            }
        }

        java.util.Map<String, java.util.Map<String, Long>> sortedResult = new java.util.LinkedHashMap<>();
        result.entrySet().stream()
            .sorted(java.util.Map.Entry.comparingByKey())
            .forEach(entry -> sortedResult.put(entry.getKey(), entry.getValue()));

        return sortedResult;
    }

    // ===== MÉTODOS DE INVALIDACIÓN Y RESTAURACIÓN =====

    /**
     * Invalida una transacción
     */
    public void invalidate(Transaction transaction, Integer invalidatedBy, String reason) {
        transaction.invalidate(invalidatedBy, reason);
    }

    /**
     * Restaura una transacción invalidada
     */
    public void restore(Transaction transaction) {
        transaction.restore();
    }

    /**
     * Cuenta transacciones invalidadas
     */
    public long countInvalidated() {
        Long c = Transaction.count("status = ?", Transaction.STATUS_INVALIDATED);
        return c == null ? 0L : c;
    }

    // ===== MÉTODOS DE GESTIÓN DE PRÉSTAMOS (FASE 10) =====

    /**
     * Busca préstamos pendientes (activos y con loan_status = pending)
     */
    public List<Transaction> findPendingLoans() {
        return Transaction.where(
            "type = ? AND loan_status = ? AND status = ?",
            Transaction.TYPE_LOAN,
            Transaction.LOAN_STATUS_PENDING,
            Transaction.STATUS_ACTIVE
        ).orderBy("expected_return_date ASC");
    }

    /**
     * Busca préstamos vencidos (con estado 'overdue' o 'pending' con fecha pasada)
     */
    public List<Transaction> findOverdueLoans() {
        return Transaction.where(
            "type = ? AND ((loan_status = ? AND expected_return_date < CURDATE()) OR loan_status = ?) AND status = ?",
            Transaction.TYPE_LOAN,
            Transaction.LOAN_STATUS_PENDING,
            Transaction.LOAN_STATUS_OVERDUE,
            Transaction.STATUS_ACTIVE
        ).orderBy("expected_return_date ASC");
    }

    /**
     * Obtiene el historial completo de un préstamo (préstamo + devolución)
     */
    public List<Transaction> getLoanHistory(Integer loanId) {
        List<Transaction> history = new ArrayList<>();

        // Obtener préstamo original
        Transaction loan = findById(loanId);
        if (loan != null && loan.isLoan()) {
            history.add(loan);

            // Buscar devolución asociada
            Transaction returnTx = Transaction.findFirst(
                "related_transaction_id = ? AND type = ?",
                loanId,
                Transaction.TYPE_IN
            );
            if (returnTx != null) {
                history.add(returnTx);
            }
        }

        return history;
    }

    /**
     * Busca un préstamo por su código
     */
    public Transaction findByLoanCode(String loanCode) {
        if (loanCode == null || loanCode.trim().isEmpty()) {
            return null;
        }

        return Transaction.findFirst(
            "loan_code = ? AND type = ?",
            loanCode.trim().toUpperCase(),
            Transaction.TYPE_LOAN
        );
    }

    /**
     * Busca préstamos pendientes que coincidan con el texto de búsqueda
     * Solo devuelve préstamos con loan_status = 'pending' y status = 1
     * Útil para autocompletado
     * @param searchText Texto a buscar en loan_code
     * @return Lista de hasta 10 préstamos que coinciden
     */
    public List<Transaction> searchPendingLoansByCode(String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            return new java.util.ArrayList<>();
        }

        String pattern = "%" + searchText.trim().toUpperCase() + "%";

        return Transaction.where(
            "type = ? AND loan_status = ? AND status = ? AND loan_code LIKE ? ORDER BY loan_code DESC LIMIT 10",
            Transaction.TYPE_LOAN,
            "pending",
            Transaction.STATUS_ACTIVE,
            pattern
        );
    }

    /**
     * Cuenta préstamos por estado
     */
    public long countLoansByStatus(String loanStatus) {
        Long c = Transaction.count(
            "type = ? AND loan_status = ? AND status = ?",
            Transaction.TYPE_LOAN,
            loanStatus,
            Transaction.STATUS_ACTIVE
        );
        return c == null ? 0L : c;
    }

    /**
     * Busca préstamos próximos a vencer (en los próximos N días)
     */
    public List<Transaction> findLoansExpiringSoon(int days) {
        return Transaction.where(
            "type = ? AND loan_status = ? AND expected_return_date BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL ? DAY) AND status = ?",
            Transaction.TYPE_LOAN,
            Transaction.LOAN_STATUS_PENDING,
            days,
            Transaction.STATUS_ACTIVE
        ).orderBy("expected_return_date ASC");
    }

    /**
     * Busca préstamos de una persona específica
     */
    public List<Transaction> findLoansByPerson(Integer personId) {
        return Transaction.where(
            "type = ? AND person_id = ? AND status = ?",
            Transaction.TYPE_LOAN,
            personId,
            Transaction.STATUS_ACTIVE
        ).orderBy("created_at DESC");
    }

    /**
     * Busca préstamos pendientes de una persona específica
     */
    public List<Transaction> findPendingLoansByPerson(Integer personId) {
        return Transaction.where(
            "type = ? AND person_id = ? AND loan_status = ? AND status = ?",
            Transaction.TYPE_LOAN,
            personId,
            Transaction.LOAN_STATUS_PENDING,
            Transaction.STATUS_ACTIVE
        ).orderBy("expected_return_date ASC");
    }
}
