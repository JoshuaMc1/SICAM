package com.joshuamc1.sicam.services;

import com.joshuamc1.sicam.core.DatabaseManager;
import com.joshuamc1.sicam.core.Session;
import com.joshuamc1.sicam.models.Product;
import com.joshuamc1.sicam.models.Transaction;
import com.joshuamc1.sicam.models.TransactionItem;
import com.joshuamc1.sicam.models.dto.TransactionItemDTO;
import com.joshuamc1.sicam.repositories.TransactionRepository;
import com.joshuamc1.sicam.repositories.TransactionItemRepository;
import org.javalite.activejdbc.Base;

import java.time.LocalDate;
import java.util.*;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import com.joshuamc1.sicam.core.Authorization;

public class TransactionService {
    private final TransactionRepository repo = new TransactionRepository();
    private final TransactionItemRepository itemRepo = new TransactionItemRepository();
    private final AuditService auditService = new AuditService();

    public Transaction createTransaction(Integer productId, String type, double quantity, Integer userId,
                                         Integer personId, String notes) {
        return createMultiProductTransaction(type, null, userId, personId, notes,
            List.of(new TransactionItemDTO(productId, quantity, notes)));
    }

    public Transaction createTransaction(Integer productId, String type, double quantity, String date,
                                         Integer personId, String notes) {
        return createMultiProductTransaction(type, date, Session.getCurrentUserId(), personId, notes,
            List.of(new TransactionItemDTO(productId, quantity, notes)));
    }

    public List<Transaction> findByProduct(Integer productId) {
        return DatabaseManager.callInConnection(() ->
                repo.findByProduct(productId)
        );
    }

    public List<Transaction> findByType(String type) {
        requireTypeRead(type);
        return DatabaseManager.callInConnection(() ->
                repo.findByType(type)
        );
    }

    public long countByType(String type) {
        requireTypeRead(type);
        return DatabaseManager.callInConnection(() -> repo.countByType(type));
    }

    public List<Transaction> findByTypePage(String type, int limit, int offset) {
        requireTypeRead(type);
        return DatabaseManager.callInConnection(() -> repo.findByTypePage(type, limit, offset));
    }

    public long countToday() {
        com.joshuamc1.sicam.core.Authorization.require("DASHBOARD","READ");
        return DatabaseManager.callInConnection(repo::countToday);
    }

    public List<Transaction> findLatest(int limit) {
        com.joshuamc1.sicam.core.Authorization.require("DASHBOARD","READ");
        return DatabaseManager.callInConnection(() -> repo.findLatest(limit));
    }

    public long countThisMonth() {
        com.joshuamc1.sicam.core.Authorization.require("DASHBOARD","READ");
        return DatabaseManager.callInConnection(repo::countThisMonth);
    }

    public long countLastMonth() {
        com.joshuamc1.sicam.core.Authorization.require("DASHBOARD","READ");
        return DatabaseManager.callInConnection(repo::countLastMonth);
    }

    public long countActiveLoans() {
        com.joshuamc1.sicam.core.Authorization.require("DASHBOARD","READ");
        return DatabaseManager.callInConnection(repo::countActiveLoans);
    }

    public List<Transaction> findLast30Days() {
        com.joshuamc1.sicam.core.Authorization.require("DASHBOARD","READ");
        return DatabaseManager.callInConnection(repo::findLast30Days);
    }

    public java.util.Map<String, Long> getMostMovedProducts(int limit) {
        com.joshuamc1.sicam.core.Authorization.require("DASHBOARD","READ");
        return DatabaseManager.callInConnection(() -> repo.getMostMovedProducts(limit));
    }

    public java.util.Map<String, Long> getTransactionsByType() {
        com.joshuamc1.sicam.core.Authorization.require("DASHBOARD","READ");
        return DatabaseManager.callInConnection(repo::getTransactionsByType);
    }

    public java.util.Map<Integer, Long> getWeeklyActivity() {
        com.joshuamc1.sicam.core.Authorization.require("DASHBOARD","READ");
        return DatabaseManager.callInConnection(repo::getWeeklyActivity);
    }

    public long countByTypeThisMonth(String type) {
        com.joshuamc1.sicam.core.Authorization.require("DASHBOARD","READ");
        return DatabaseManager.callInConnection(() -> repo.countByTypeThisMonth(type));
    }

    public long countByTypeLastMonth(String type) {
        com.joshuamc1.sicam.core.Authorization.require("DASHBOARD","READ");
        return DatabaseManager.callInConnection(() -> repo.countByTypeLastMonth(type));
    }

    public java.util.Map<String, java.util.Map<String, Long>> getQuarterlyTrend() {
        com.joshuamc1.sicam.core.Authorization.require("DASHBOARD","READ");
        return DatabaseManager.callInConnection(repo::getQuarterlyTrend);
    }

    /**
     * Crear transacción con múltiples productos
     * @param type Tipo de transacción (in, out, loan)
     * @param date Fecha de la transacción
     * @param userId Usuario que realiza la transacción
     * @param personId Persona relacionada (proveedor/beneficiario)
     * @param notes Notas generales de la transacción
     * @param items Lista de items con productos, cantidades y notas
     * @return Transaction creada con sus items
     */
    public Transaction createMultiProductTransaction(
            String type,
            String date,
            Integer userId,
            Integer personId,
            String notes,
            List<TransactionItemDTO> items) {
        requireAction(type, "CREATE");
        requireActor(userId);
        List<TransactionItemDTO> safeItems = validateItems(items);
        String transactionDate = validDate(date);
        return DatabaseManager.callInTransaction(() -> {
            Transaction transaction = createHeader(type, transactionDate, userId, personId, notes, safeItems.size());
            writeItems(transaction.getInteger("id"), safeItems, "in".equals(type) ? 1 : -1);
            auditService.logAction(userId, "create_multi_transaction", "transactions", transaction.getInteger("id"),
                "type=" + type + ",total_items=" + safeItems.size());
            return transaction;
        });
    }

    /**
     * Obtener todos los items de una transacción
     * @param transactionId ID de la transacción
     * @return lista de items
     */
    public List<TransactionItem> getTransactionItems(Integer transactionId) {
        com.joshuamc1.sicam.core.Authorization.requireActiveSession();
        return DatabaseManager.callInConnection(() -> {
            Transaction transaction=repo.findById(transactionId);
            if(transaction==null) return List.of();
            requireTypeRead(transaction.getString("type"));
            if(!transaction.isActive()) com.joshuamc1.sicam.core.Authorization.require("SISTEMA","VIEW_DELETED");
            return itemRepo.findByTransactionId(transactionId);
        });
    }

    /**
     * Método actualizado para mantener compatibilidad con código existente
     * Internamente usa el nuevo método multi-producto con un solo item
     */
    public Transaction createTransactionCompat(Integer productId, String type,
                                               double quantity, Integer userId,
                                               Integer personId, String notes) {
        List<TransactionItemDTO> items = List.of(
                new TransactionItemDTO(productId, quantity, notes)
        );

        return createMultiProductTransaction(
                type,
                LocalDate.now().toString(),
                userId,
                personId,
                notes,
                items
        );
    }

    // ===== INVALIDACIÓN Y RESTAURACIÓN =====

    /**
     * Invalida una transacción (para errores)
     * Revierte el impacto en el stock automáticamente
     */
    public void invalidate(Transaction transaction, String reason) {
        Authorization.requireActiveSession();
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Indique el motivo de invalidación");
        changeValidity(transaction, reason.trim(), false);
    }

    public void restore(Transaction transaction) {
        Authorization.requireActiveSession();
        changeValidity(transaction, null, true);
    }

    private void changeValidity(Transaction supplied, String reason, boolean restoring) {
        if (supplied == null || supplied.getInteger("id") == null) throw new IllegalArgumentException("Transacción no válida");
        Integer id = supplied.getInteger("id");
        DatabaseManager.callInTransaction(() -> {
            Transaction initial = repo.findById(id);
            if (initial == null) throw new IllegalArgumentException("Transacción no encontrada");
            // Siempre bloquear primero el préstamo y después su devolución.
            Transaction loan = initial.isLoanReturn() ? lockedTransaction(initial.getInteger("related_transaction_id")) : null;
            Transaction current = lockedTransaction(id);
            requireAction(current.getString("type"), restoring ? "RESTORE" : "DELETE");
            if (restoring ? !current.isInvalidated() : !current.isActive())
                throw new IllegalStateException(restoring ? "La transacción no está invalidada" : "La transacción ya no está activa");
            if (current.isLoan() && hasActiveReturn(id))
                throw new IllegalStateException("Primero debe invalidar la devolución activa del préstamo");
            if (loan != null) {
                if (!loan.isLoan() || !loan.isActive()) throw new IllegalStateException("El préstamo relacionado no está activo");
                if (restoring) {
                    requireOpenLoan(loan);
                    if (hasActiveReturn(loan.getInteger("id"))) throw new IllegalStateException("El préstamo ya tiene una devolución activa");
                } else if (!Objects.equals(loan.getInteger("related_transaction_id"), id)) {
                    throw new IllegalStateException("La devolución no corresponde al cierre vigente del préstamo");
                }
            }
            List<TransactionItem> items = itemRepo.findByTransactionId(id);
            if (items.isEmpty()) throw new IllegalStateException("La transacción no tiene productos; requiere revisión");
            Map<Integer, BigDecimal> quantities = new TreeMap<>();
            for (TransactionItem item : items) {
                if (restoring ? !item.isCancelled() : !item.isActive()) throw new IllegalStateException("Estado de productos inconsistente");
                quantities.merge(item.getInteger("product_id"), positiveQuantity(item.getDouble("quantity")), BigDecimal::add);
            }
            int direction = "in".equals(current.getString("type")) ? 1 : -1;
            adjustStock(quantities, restoring ? direction : -direction, restoring);
            for (TransactionItem item : items) {
                if (restoring) item.markAsActive(); else item.markAsCancelled();
            }
            if (restoring) repo.restore(current); else repo.invalidate(current, Session.getCurrentUserId(), reason);
            if (loan != null) {
                if (restoring) loan.markAsReturned(id, current.getString("return_type"), LocalDate.parse(current.getString("date")), current.getString("notes"));
                else {
                    loan.set("loan_status", openLoanStatus(loan));
                    loan.set("related_transaction_id", null);
                    loan.set("actual_return_date", null);
                    loan.set("return_type", null);
                    loan.set("return_notes", null);
                    loan.saveIt();
                }
            }
            auditService.logAction(Session.getCurrentUserId(), restoring ? "restore_transaction" : "invalidate_transaction",
                "transactions", id, "type=" + current.getString("type") + ",reason=" + reason);
            return null;
        });
    }

    public Transaction createLoan(String date, Integer userId, Integer personId, String notes,
                                  LocalDate expectedReturnDate, List<TransactionItemDTO> items) {
        requireAction("loan", "CREATE");
        requireActor(userId);
        List<TransactionItemDTO> safeItems = validateItems(items);
        String transactionDate = validDate(date);
        if (expectedReturnDate == null || expectedReturnDate.isBefore(LocalDate.parse(transactionDate)))
            throw new IllegalArgumentException("La devolución debe ser igual o posterior a la fecha del préstamo");
        return DatabaseManager.callInTransaction(() -> {
            if (personId == null) throw new IllegalArgumentException("Seleccione la persona del préstamo");
            lockRow("roles", 1);
            lockRow("people", personId);
            com.joshuamc1.sicam.models.Person person = new com.joshuamc1.sicam.repositories.PersonRepository().findById(personId);
            if (person == null || !"active".equals(person.getString("status"))) throw new IllegalArgumentException("La persona no está activa");
            // El bloqueo global de préstamos serializa códigos con las mismas iniciales.
            String code = com.joshuamc1.sicam.core.LoanCodeGenerator.generateLoanCode(person, LocalDate.parse(transactionDate));
            Transaction loan = createHeader("loan", transactionDate, userId, personId, notes, safeItems.size());
            loan.set("loan_code", code);
            loan.set("loan_status", Transaction.LOAN_STATUS_PENDING);
            loan.set("expected_return_date", expectedReturnDate.toString());
            loan.saveIt();
            writeItems(loan.getInteger("id"), safeItems, -1);
            auditService.logAction(userId, "create_loan", "transactions", loan.getInteger("id"), "loan_code=" + code);
            return loan;
        });
    }

    public Transaction createLoanReturn(Integer loanId, String returnType, String returnNotes, List<TransactionItemDTO> items) {
        return createLoanReturn(loanId, LocalDate.now().toString(), returnType, returnNotes, items);
    }

    public Transaction createLoanReturn(Integer loanId, String date, String returnType, String returnNotes, List<TransactionItemDTO> items) {
        Authorization.require("INGRESOS", "CREATE");
        Authorization.require("PRESTAMOS", "READ");
        String transactionDate = validDate(date);
        LocalDate returnDate = LocalDate.parse(transactionDate);
        if (returnType == null || !Set.of("same_product", "purchased", "replaced", "other").contains(returnType))
            throw new IllegalArgumentException("Tipo de devolución inválido");
        List<TransactionItemDTO> safeItems = validateItems(items);
        if (Set.of("replaced", "other").contains(returnType) && (returnNotes == null || returnNotes.isBlank()))
            throw new IllegalArgumentException("Detalle cómo los productos entregados liquidan el préstamo completo");
        return DatabaseManager.callInTransaction(() -> {
            Transaction loan = lockedTransaction(loanId);
            requireOpenLoan(loan);
            if (returnDate.isBefore(LocalDate.parse(loan.getString("date")))) {
                throw new IllegalArgumentException("La fecha de devolución no puede ser anterior a la fecha del préstamo");
            }
            if (hasActiveReturn(loanId)) throw new IllegalStateException("El préstamo ya tiene una devolución activa");
            if (Set.of("same_product", "purchased").contains(returnType)) {
                Map<Integer, BigDecimal> expected = new TreeMap<>();
                for (TransactionItem item : itemRepo.findByTransactionId(loanId)) {
                    if (!item.isActive()) throw new IllegalStateException("El préstamo tiene productos cancelados");
                    expected.merge(item.getInteger("product_id"), positiveQuantity(item.getDouble("quantity")), BigDecimal::add);
                }
                Map<Integer, BigDecimal> received = quantities(safeItems);
                if (!expected.keySet().equals(received.keySet()) || expected.entrySet().stream().anyMatch(e -> e.getValue().compareTo(received.get(e.getKey())) != 0))
                    throw new IllegalArgumentException("La devolución debe incluir todos los productos y cantidades del préstamo. No se admiten devoluciones parciales");
            }
            Integer userId = Session.getCurrentUserId();
            Transaction returned = createHeader("in", transactionDate, userId, loan.getInteger("person_id"), returnNotes, safeItems.size());
            returned.set("related_transaction_id", loanId);
            returned.set("return_type", returnType);
            returned.saveIt();
            writeItems(returned.getInteger("id"), safeItems, 1);
            loan.markAsReturned(returned.getInteger("id"), returnType, returnDate, returnNotes);
            auditService.logAction(userId, "loan_return", "transactions", returned.getInteger("id"), "loan_id=" + loanId + ",return_type=" + returnType);
            return returned;
        });
    }

    private void requireAction(String type, String action) {
        String module;
        if ("in".equals(type)) module = "INGRESOS";
        else if ("out".equals(type)) module = "EGRESOS";
        else if ("loan".equals(type)) module = "PRESTAMOS";
        else throw new IllegalArgumentException("Tipo de transacción inválido");
        Authorization.require(module, action);
    }

    private void requireActor(Integer userId) {
        if (userId == null || !Objects.equals(userId, Session.getCurrentUserId())) throw new SecurityException("Usuario de la transacción no válido");
    }

    private String validDate(String date) {
        try { return date == null ? LocalDate.now().toString() : LocalDate.parse(date).toString(); }
        catch (java.time.DateTimeException ex) { throw new IllegalArgumentException("Fecha de transacción inválida", ex); }
    }

    private BigDecimal positiveQuantity(Double quantity) {
        return com.joshuamc1.sicam.core.TransactionQuantity.requirePositive(quantity);
    }

    private List<TransactionItemDTO> validateItems(List<TransactionItemDTO> items) {
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("Agregue al menos un producto");
        List<TransactionItemDTO> safe = new ArrayList<>();
        for (TransactionItemDTO item : items) {
            if (item == null || item.getProductId() == null || item.getProductId() <= 0) throw new IllegalArgumentException("Producto inválido");
            safe.add(new TransactionItemDTO(item.getProductId(), positiveQuantity(item.getQuantity()).doubleValue(), item.getNotes()));
        }
        return List.copyOf(safe);
    }

    private Map<Integer, BigDecimal> quantities(List<TransactionItemDTO> items) {
        Map<Integer, BigDecimal> quantities = new TreeMap<>();
        for (TransactionItemDTO item : items) quantities.merge(item.getProductId(), positiveQuantity(item.getQuantity()), BigDecimal::add);
        return quantities;
    }

    private Transaction createHeader(String type, String date, Integer userId, Integer personId, String notes, int size) {
        Transaction header = new Transaction();
        header.set("type", type); header.set("date", date); header.set("user_id", userId);
        header.set("person_id", personId); header.set("notes", notes); header.set("total_items", size);
        header.set("status", Transaction.STATUS_ACTIVE);
        return repo.create(header);
    }

    private void writeItems(Integer transactionId, List<TransactionItemDTO> items, int direction) {
        adjustStock(quantities(items), direction, true);
        for (TransactionItemDTO dto : items) {
            TransactionItem item = new TransactionItem();
            item.set("transaction_id", transactionId); item.set("product_id", dto.getProductId());
            item.set("quantity", dto.getQuantity()); item.set("notes", dto.getNotes()); item.set("status", TransactionItem.STATUS_ACTIVE);
            itemRepo.create(item);
        }
    }

    private void adjustStock(Map<Integer, BigDecimal> quantities, int direction, boolean requireActive) {
        // TreeMap fija el orden de bloqueo de productos en operaciones con varios productos.
        for (Map.Entry<Integer, BigDecimal> entry : quantities.entrySet()) {
            List<Product> matches = Product.findBySQL("SELECT * FROM products WHERE id = ?" + lockingSuffix(), entry.getKey());
            Product product = matches.isEmpty() ? null : matches.get(0);
            if (product == null || (requireActive && !"active".equals(product.getString("status"))))
                throw new IllegalStateException("Producto inexistente o no activo: " + entry.getKey());
            BigDecimal old = new BigDecimal(Objects.toString(product.get("current_stock"), "0"));
            BigDecimal next = old.add(entry.getValue().multiply(BigDecimal.valueOf(direction)));
            if (next.signum() < 0) throw new IllegalStateException("Stock insuficiente para el producto " + product.getString("name") + ". Disponible: " + old);
            if (next.compareTo(new BigDecimal("99999999.99")) > 0) throw new IllegalStateException("El stock supera el máximo permitido");
            Base.exec("UPDATE products SET current_stock = ? WHERE id = ?", next, entry.getKey());
        }
    }

    private void lockRow(String table, Integer id) {
        if (id == null) throw new IllegalArgumentException("Registro no válido");
        try {
            String suffix = lockingSuffix();
            try (PreparedStatement statement = Base.connection().prepareStatement("SELECT id FROM " + table + " WHERE id = ?" + suffix)) {
                statement.setInt(1, id);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) throw new IllegalArgumentException("Registro no encontrado en " + table + ": " + id);
                }
            }
        } catch (SQLException ex) { throw new IllegalStateException("No se pudo bloquear el registro", ex); }
    }

    private Transaction lockedTransaction(Integer id) {
        lockRow("transactions", id);
        List<Transaction> matches = Transaction.findBySQL("SELECT * FROM transactions WHERE id = ?" + lockingSuffix(), id);
        return matches.get(0);
    }

    private String lockingSuffix() {
        try { return Base.connection().getMetaData().getDatabaseProductName().equalsIgnoreCase("MySQL") ? " FOR UPDATE" : ""; }
        catch (SQLException ex) { throw new IllegalStateException("No se pudo verificar la conexión", ex); }
    }

    private boolean hasActiveReturn(Integer loanId) {
        List<Transaction> returns = Transaction.findBySQL("SELECT * FROM transactions WHERE related_transaction_id = ? AND type = 'in' AND status = 'active'" + lockingSuffix(), loanId);
        return !returns.isEmpty();
    }

    private void requireOpenLoan(Transaction loan) {
        if (loan == null || !loan.isLoan() || !loan.isActive() ||
            !Set.of("pending", "overdue").contains(Objects.toString(loan.getString("loan_status"), "")))
            throw new IllegalStateException("El préstamo no está activo y pendiente de devolución");
    }

    private String openLoanStatus(Transaction loan) {
        java.sql.Date due = loan.getDate("expected_return_date");
        return due != null && due.toLocalDate().isBefore(LocalDate.now()) ? "overdue" : "pending";
    }

    /**
     * Busca préstamos pendientes
     */
    public List<Transaction> findPendingLoans() {
        com.joshuamc1.sicam.core.Authorization.require("PRESTAMOS","READ");
        return DatabaseManager.callInConnection(repo::findPendingLoans);
    }

    /**
     * Busca préstamos vencidos
     */
    public List<Transaction> findOverdueLoans() {
        com.joshuamc1.sicam.core.Authorization.require("PRESTAMOS","READ");
        return DatabaseManager.callInConnection(repo::findOverdueLoans);
    }

    /**
     * Obtiene el historial de un préstamo (préstamo + devolución)
     */
    public List<Transaction> getLoanHistory(Integer loanId) {
        com.joshuamc1.sicam.core.Authorization.require("PRESTAMOS","READ");
        return DatabaseManager.callInConnection(() -> repo.getLoanHistory(loanId));
    }

    /**
     * Busca un préstamo por código
     */
    public Transaction findByLoanCode(String loanCode) {
        com.joshuamc1.sicam.core.Authorization.require("PRESTAMOS","READ");
        return DatabaseManager.callInConnection(() -> repo.findByLoanCode(loanCode));
    }

    /**
     * Busca préstamos pendientes que coincidan con el código (para autocompletado)
     * Solo devuelve préstamos con loan_status = 'pending' y status = 1 (activos)
     * @param searchText Texto a buscar en loan_code
     * @return Lista de préstamos que coinciden con la búsqueda
     */
    public List<Transaction> searchPendingLoansByCode(String searchText) {
        com.joshuamc1.sicam.core.Authorization.require("PRESTAMOS","READ");
        return DatabaseManager.callInConnection(() -> repo.searchPendingLoansByCode(searchText));
    }

    /**
     * Busca préstamos próximos a vencer
     */
    public List<Transaction> findLoansExpiringSoon(int days) {
        com.joshuamc1.sicam.core.Authorization.require("PRESTAMOS","READ");
        return DatabaseManager.callInConnection(() -> repo.findLoansExpiringSoon(days));
    }

    /**
     * Cuenta préstamos por estado
     */
    public long countLoansByStatus(String loanStatus) {
        com.joshuamc1.sicam.core.Authorization.require("PRESTAMOS","READ");
        return DatabaseManager.callInConnection(() -> repo.countLoansByStatus(loanStatus));
    }

    /**
     * Obtiene solamente transacciones invalidadas por tipo
     */
    public List<Transaction> findInvalidatedByType(String type) {
        com.joshuamc1.sicam.core.Authorization.require("SISTEMA","VIEW_DELETED");
        requireTypeRead(type);
        return DatabaseManager.callInConnection(() -> repo.findInvalidatedByType(type));
    }

    /**
     * Obtiene transacciones por tipo incluyendo las invalidadas
     */
    public List<Transaction> findByTypeIncludingInvalidated(String type) {
        com.joshuamc1.sicam.core.Authorization.require("SISTEMA","VIEW_DELETED");
        requireTypeRead(type);
        return DatabaseManager.callInConnection(() -> {
            return Transaction.where("type = ?", type)
                .orderBy("id DESC");
        });
    }
    private void requireTypeRead(String type) {
        String module=switch(type) { case "in" -> "INGRESOS"; case "out" -> "EGRESOS"; case "loan" -> "PRESTAMOS"; default -> throw new IllegalArgumentException("Tipo no válido"); };
        com.joshuamc1.sicam.core.Authorization.require(module,"READ");
    }

}

