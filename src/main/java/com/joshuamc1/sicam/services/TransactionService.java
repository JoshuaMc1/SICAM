package com.joshuamc1.sicam.services;

import com.joshuamc1.sicam.core.DatabaseManager;
import com.joshuamc1.sicam.core.Session;
import com.joshuamc1.sicam.core.PermissionHelper;
import com.joshuamc1.sicam.models.Product;
import com.joshuamc1.sicam.models.Transaction;
import com.joshuamc1.sicam.models.TransactionItem;
import com.joshuamc1.sicam.models.dto.TransactionItemDTO;
import com.joshuamc1.sicam.repositories.ProductRepository;
import com.joshuamc1.sicam.repositories.TransactionRepository;
import com.joshuamc1.sicam.repositories.TransactionItemRepository;
import org.javalite.activejdbc.Base;

import java.time.LocalDate;
import java.util.List;

public class TransactionService {
    private final ProductRepository productRepo = new ProductRepository();
    private final TransactionRepository repo = new TransactionRepository();
    private final TransactionItemRepository itemRepo = new TransactionItemRepository();
    private final AuditService auditService = new AuditService();

    public Transaction createTransaction(Integer productId, String type,
                                         double quantity, Integer userId,
                                         Integer personId, String notes) {

        // Validar permisos específicos por tipo; ROOT siempre pasa
        boolean isRoot = Session.getCurrentUser() != null && Session.getCurrentUser().isRootRole();
        boolean allowed = switch (type) {
            case "in" -> PermissionHelper.canAccess("INGRESOS", "CREATE");
            case "out" -> PermissionHelper.canAccess("EGRESOS", "CREATE");
            case "loan" -> PermissionHelper.canAccess("PRESTAMOS", "CREATE");
            default -> false;
        };
        if (!isRoot && !allowed) {
            throw new IllegalStateException("Acceso denegado: no tiene permiso para crear " + type);
        }

        return DatabaseManager.callInConnection(() -> {

            Base.openTransaction();

            Product product = productRepo.findById(productId);

            if (product == null)
                throw new IllegalArgumentException("Product not found");

            if ("in".equals(type)) {
                product.updateStock(quantity);
            } else {
                product.updateStock(-quantity);
            }

            productRepo.save(product);

            Transaction t = new Transaction();
            t.set("product_id", productId);
            t.set("type", type);
            t.set("quantity", quantity);
            t.set("user_id", userId);
            t.set("person_id", personId);
            t.set("notes", notes);

            repo.create(t);

            auditService.logAction(
                    Session.getCurrentUserId(),
                    "create_transaction",
                    "transactions",
                    t.getInteger("id"),
                    String.format("product_id=%d,type=%s,quantity=%.2f,user_id=%d,person_id=%d,notes=%s",
                            productId, type, quantity, userId, personId, notes)
            );

            Base.commitTransaction();
            return t;
        });
    }

    // Sobrecarga para permitir fecha personalizada
    public Transaction createTransaction(Integer productId, String type,
                                         double quantity, String date,
                                         Integer personId, String notes) {

        boolean isRoot = Session.getCurrentUser() != null && Session.getCurrentUser().isRootRole();
        boolean allowed = switch (type) {
            case "in" -> PermissionHelper.canAccess("INGRESOS", "CREATE");
            case "out" -> PermissionHelper.canAccess("EGRESOS", "CREATE");
            case "loan" -> PermissionHelper.canAccess("PRESTAMOS", "CREATE");
            default -> false;
        };
        if (!isRoot && !allowed) {
            throw new IllegalStateException("Acceso denegado: no tiene permiso para crear " + type);
        }

        return DatabaseManager.callInConnection(() -> {

            Base.openTransaction();

            Product product = productRepo.findById(productId);

            if (product == null)
                throw new IllegalArgumentException("Product not found");

            if ("in".equals(type)) {
                product.updateStock(quantity);
            } else {
                product.updateStock(-quantity);
            }

            productRepo.save(product);

            Transaction t = new Transaction();
            t.set("product_id", productId);
            t.set("type", type);
            t.set("quantity", quantity);
            t.set("date", date);
            t.set("user_id", Session.getCurrentUserId());
            t.set("person_id", personId);
            t.set("notes", notes);

            repo.create(t);

            auditService.logAction(
                    Session.getCurrentUserId(),
                    "create_transaction",
                    "transactions",
                    t.getInteger("id"),
                    String.format("product_id=%d,type=%s,quantity=%.2f,date=%s,person_id=%s,notes=%s",
                            productId, type, quantity, date, personId, notes)
            );

            Base.commitTransaction();
            return t;
        });
    }

    public List<Transaction> findByProduct(Integer productId) {
        return DatabaseManager.callInConnection(() ->
                repo.findByProduct(productId)
        );
    }

    public List<Transaction> findByType(String type) {
        return DatabaseManager.callInConnection(() ->
                repo.findByType(type)
        );
    }

    public long countByType(String type) {
        return DatabaseManager.callInConnection(() -> repo.countByType(type));
    }

    public List<Transaction> findByTypePage(String type, int limit, int offset) {
        return DatabaseManager.callInConnection(() -> repo.findByTypePage(type, limit, offset));
    }

    public long countToday() {
        return DatabaseManager.callInConnection(repo::countToday);
    }

    public List<Transaction> findLatest(int limit) {
        return DatabaseManager.callInConnection(() -> repo.findLatest(limit));
    }

    public long countThisMonth() {
        return DatabaseManager.callInConnection(repo::countThisMonth);
    }

    public long countLastMonth() {
        return DatabaseManager.callInConnection(repo::countLastMonth);
    }

    public long countActiveLoans() {
        return DatabaseManager.callInConnection(repo::countActiveLoans);
    }

    public List<Transaction> findLast30Days() {
        return DatabaseManager.callInConnection(repo::findLast30Days);
    }

    public java.util.Map<String, Long> getMostMovedProducts(int limit) {
        return DatabaseManager.callInConnection(() -> repo.getMostMovedProducts(limit));
    }

    public java.util.Map<String, Long> getTransactionsByType() {
        return DatabaseManager.callInConnection(repo::getTransactionsByType);
    }

    public java.util.Map<Integer, Long> getWeeklyActivity() {
        return DatabaseManager.callInConnection(repo::getWeeklyActivity);
    }

    public long countByTypeThisMonth(String type) {
        return DatabaseManager.callInConnection(() -> repo.countByTypeThisMonth(type));
    }

    public long countByTypeLastMonth(String type) {
        return DatabaseManager.callInConnection(() -> repo.countByTypeLastMonth(type));
    }

    public java.util.Map<String, java.util.Map<String, Long>> getQuarterlyTrend() {
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

        // Validar permisos específicos por tipo; ROOT siempre pasa
        boolean isRoot = Session.getCurrentUser() != null && Session.getCurrentUser().isRootRole();
        boolean allowed = switch (type) {
            case "in" -> PermissionHelper.canAccess("INGRESOS", "CREATE");
            case "out" -> PermissionHelper.canAccess("EGRESOS", "CREATE");
            case "loan" -> PermissionHelper.canAccess("PRESTAMOS", "CREATE");
            default -> false;
        };

        if (!isRoot && !allowed) {
            throw new IllegalStateException("Acceso denegado: no tiene permiso para crear " + type);
        }

        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("La transacción debe contener al menos un producto");
        }

        return DatabaseManager.callInConnection(() -> {
            Base.openTransaction();

            // 1. Crear header de transacción
            Transaction transaction = new Transaction();
            transaction.set("type", type);
            transaction.set("date", date != null ? date : LocalDate.now().toString());
            transaction.set("user_id", userId);
            transaction.set("person_id", personId);
            transaction.set("notes", notes);
            transaction.set("total_items", items.size());
            repo.create(transaction);

            Integer transactionId = transaction.getInteger("id");

            // 2. Crear items y actualizar stock
            for (TransactionItemDTO itemDTO : items) {
                Product product = productRepo.findById(itemDTO.getProductId());

                if (product == null) {
                    Base.rollbackTransaction();
                    throw new IllegalArgumentException("Producto no encontrado: " + itemDTO.getProductId());
                }

                // Actualizar stock según tipo
                if ("in".equals(type)) {
                    product.updateStock(itemDTO.getQuantity());
                } else {
                    product.updateStock(-itemDTO.getQuantity());
                }
                productRepo.save(product);

                // Crear item de transacción
                TransactionItem item = new TransactionItem();
                item.set("transaction_id", transactionId);
                item.set("product_id", itemDTO.getProductId());
                item.set("quantity", itemDTO.getQuantity());
                item.set("notes", itemDTO.getNotes());
                itemRepo.create(item);
            }

            // 3. Auditoría
            auditService.logAction(
                    userId,
                    "create_multi_transaction",
                    "transactions",
                    transactionId,
                    String.format("type=%s, total_items=%d", type, items.size())
            );

            Base.commitTransaction();
            return transaction;
        });
    }

    /**
     * Obtener todos los items de una transacción
     * @param transactionId ID de la transacción
     * @return lista de items
     */
    public List<TransactionItem> getTransactionItems(Integer transactionId) {
        return DatabaseManager.callInConnection(() ->
                itemRepo.findByTransactionId(transactionId)
        );
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

    // ===== MÉTODOS DE INVALIDACIÓN Y RESTAURACIÓN =====

    /**
     * Invalida una transacción (para errores)
     * Revierte el impacto en el stock automáticamente
     */
    public void invalidate(Transaction transaction, String reason) {
        Integer currentUserId = Session.getCurrentUserId();
        Integer transactionId = transaction.getInteger("id");

        // Verificar permisos según tipo
        String type = transaction.getString("type");
        boolean allowed = switch (type) {
            case "in" -> PermissionHelper.canAccess("INGRESOS", "DELETE");
            case "out" -> PermissionHelper.canAccess("EGRESOS", "DELETE");
            case "loan" -> PermissionHelper.canAccess("PRESTAMOS", "DELETE");
            default -> false;
        };

        if (!allowed) {
            throw new SecurityException("No tiene permiso para invalidar esta transacción");
        }

        if (transaction.isInvalidated()) {
            throw new IllegalStateException("La transacción ya está invalidada");
        }

        DatabaseManager.runInConnection(() -> {
            Base.openTransaction();

            // Revertir el stock para cada item
            List<TransactionItem> items = itemRepo.findByTransactionId(transactionId);
            for (TransactionItem item : items) {
                Product product = productRepo.findById(item.getInteger("product_id"));
                if (product != null) {
                    // Revertir el movimiento
                    if ("in".equals(type)) {
                        product.updateStock(-item.getDouble("quantity"));
                    } else {
                        product.updateStock(item.getDouble("quantity"));
                    }
                    productRepo.save(product);
                }

                // Marcar item como cancelado
                item.markAsCancelled();
            }

            // Invalidar transacción
            repo.invalidate(transaction, currentUserId, reason);

            auditService.logAction(
                currentUserId,
                "invalidate_transaction",
                "transactions",
                transactionId,
                String.format("type=%s,reason=%s", type, reason)
            );

            Base.commitTransaction();
        });
    }

    /**
     * Restaura una transacción invalidada
     * Reaplicael impacto en el stock
     */
    public void restore(Transaction transaction) {
        Integer currentUserId = Session.getCurrentUserId();
        Integer transactionId = transaction.getInteger("id");

        // Verificar permisos según tipo
        String type = transaction.getString("type");
        boolean allowed = switch (type) {
            case "in" -> PermissionHelper.canAccess("INGRESOS", "RESTORE");
            case "out" -> PermissionHelper.canAccess("EGRESOS", "RESTORE");
            case "loan" -> PermissionHelper.canAccess("PRESTAMOS", "RESTORE");
            default -> false;
        };

        if (!allowed) {
            throw new SecurityException("No tiene permiso para restaurar esta transacción");
        }

        if (!transaction.isInvalidated()) {
            throw new IllegalStateException("La transacción no está invalidada");
        }

        DatabaseManager.runInConnection(() -> {
            Base.openTransaction();

            // Reaplicar el stock para cada item
            List<TransactionItem> items = itemRepo.findByTransactionId(transactionId);
            for (TransactionItem item : items) {
                Product product = productRepo.findById(item.getInteger("product_id"));
                if (product != null) {
                    // Reaplicar el movimiento
                    if ("in".equals(type)) {
                        product.updateStock(item.getDouble("quantity"));
                    } else {
                        product.updateStock(-item.getDouble("quantity"));
                    }
                    productRepo.save(product);
                }

                // Marcar item como activo
                item.markAsActive();
            }

            // Restaurar transacción
            repo.restore(transaction);

            auditService.logAction(
                currentUserId,
                "restore_transaction",
                "transactions",
                transactionId,
                String.format("type=%s", type)
            );

            Base.commitTransaction();
        });
    }

    // ===== MÉTODOS DE GESTIÓN DE PRÉSTAMOS (FASE 10) =====

    /**
     * Crea un préstamo con código automático
     */
    public Transaction createLoan(
            String date,
            Integer userId,
            Integer personId,
            String notes,
            LocalDate expectedReturnDate,
            List<TransactionItemDTO> items) {

        if (!PermissionHelper.canAccess("PRESTAMOS", "CREATE")) {
            throw new SecurityException("No tiene permiso para crear préstamos");
        }

        if (expectedReturnDate == null) {
            throw new IllegalArgumentException("La fecha esperada de devolución es obligatoria");
        }

        return DatabaseManager.callInConnection(() -> {
            Base.openTransaction();

            // Generar código único de préstamo
            com.joshuamc1.sicam.models.Person person =
                new com.joshuamc1.sicam.repositories.PersonRepository().findById(personId);

            if (person == null) {
                Base.rollbackTransaction();
                throw new IllegalArgumentException("Persona no encontrada");
            }

            String loanCode = com.joshuamc1.sicam.core.LoanCodeGenerator
                .generateLoanCode(person, LocalDate.now());

            // Crear transacción de préstamo
            Transaction transaction = new Transaction();
            transaction.set("type", Transaction.TYPE_LOAN);
            transaction.set("date", date != null ? date : LocalDate.now().toString());
            transaction.set("user_id", userId);
            transaction.set("person_id", personId);
            transaction.set("notes", notes);
            transaction.set("total_items", items.size());
            transaction.set("loan_code", loanCode);
            transaction.set("loan_status", Transaction.LOAN_STATUS_PENDING);
            transaction.set("expected_return_date", expectedReturnDate.toString());
            transaction.set("status", Transaction.STATUS_ACTIVE);
            repo.create(transaction);

            Integer transactionId = transaction.getInteger("id");

            // Crear items y actualizar stock
            for (TransactionItemDTO itemDTO : items) {
                Product product = productRepo.findById(itemDTO.getProductId());
                if (product == null) {
                    Base.rollbackTransaction();
                    throw new IllegalArgumentException("Producto no encontrado: " + itemDTO.getProductId());
                }

                // Restar stock (es un egreso)
                product.updateStock(-itemDTO.getQuantity());
                productRepo.save(product);

                TransactionItem item = new TransactionItem();
                item.set("transaction_id", transactionId);
                item.set("product_id", itemDTO.getProductId());
                item.set("quantity", itemDTO.getQuantity());
                item.set("notes", itemDTO.getNotes());
                item.set("status", TransactionItem.STATUS_ACTIVE);
                itemRepo.create(item);
            }

            auditService.logAction(
                userId,
                "create_loan",
                "transactions",
                transactionId,
                String.format("loan_code=%s,person_id=%d,total_items=%d,expected_return=%s",
                    loanCode, personId, items.size(), expectedReturnDate)
            );

            Base.commitTransaction();
            return transaction;
        });
    }

    /**
     * Crea una devolución de préstamo
     */
    public Transaction createLoanReturn(
            Integer loanId,
            String returnType,
            String returnNotes,
            List<TransactionItemDTO> items) {

        if (!PermissionHelper.canAccess("PRESTAMOS", "CREATE") &&
            !PermissionHelper.canAccess("INGRESOS", "CREATE")) {
            throw new SecurityException("No tiene permiso para registrar devoluciones");
        }

        Integer currentUserId = Session.getCurrentUserId();

        return DatabaseManager.callInConnection(() -> {
            Base.openTransaction();

            // Obtener préstamo original
            Transaction loan = repo.findById(loanId);
            if (loan == null || !loan.isLoan()) {
                Base.rollbackTransaction();
                throw new IllegalArgumentException("Préstamo no encontrado");
            }

            if (!loan.isLoanPending()) {
                Base.rollbackTransaction();
                throw new IllegalStateException("El préstamo no está pendiente");
            }

            // Crear transacción de devolución (ingreso)
            Transaction returnTransaction = new Transaction();
            returnTransaction.set("type", Transaction.TYPE_IN);
            returnTransaction.set("date", LocalDate.now().toString());
            returnTransaction.set("user_id", currentUserId);
            returnTransaction.set("person_id", loan.getInteger("person_id"));
            returnTransaction.set("notes", returnNotes);
            returnTransaction.set("total_items", items.size());
            returnTransaction.set("related_transaction_id", loanId);
            returnTransaction.set("return_type", returnType);
            returnTransaction.set("status", Transaction.STATUS_ACTIVE);
            repo.create(returnTransaction);

            Integer returnTxId = returnTransaction.getInteger("id");

            // Crear items y actualizar stock
            for (TransactionItemDTO itemDTO : items) {
                Product product = productRepo.findById(itemDTO.getProductId());
                if (product == null) {
                    Base.rollbackTransaction();
                    throw new IllegalArgumentException("Producto no encontrado");
                }

                // Sumar stock (es un ingreso)
                product.updateStock(itemDTO.getQuantity());
                productRepo.save(product);

                TransactionItem item = new TransactionItem();
                item.set("transaction_id", returnTxId);
                item.set("product_id", itemDTO.getProductId());
                item.set("quantity", itemDTO.getQuantity());
                item.set("notes", itemDTO.getNotes());
                item.set("status", TransactionItem.STATUS_ACTIVE);
                itemRepo.create(item);
            }

            // Actualizar préstamo original
            loan.markAsReturned(returnTxId, returnType, new java.util.Date(), returnNotes);

            auditService.logAction(
                currentUserId,
                "loan_return",
                "transactions",
                returnTxId,
                String.format("loan_id=%d,return_type=%s", loanId, returnType)
            );

            Base.commitTransaction();
            return returnTransaction;
        });
    }

    /**
     * Busca préstamos pendientes
     */
    public List<Transaction> findPendingLoans() {
        return DatabaseManager.callInConnection(repo::findPendingLoans);
    }

    /**
     * Busca préstamos vencidos
     */
    public List<Transaction> findOverdueLoans() {
        return DatabaseManager.callInConnection(repo::findOverdueLoans);
    }

    /**
     * Obtiene el historial de un préstamo (préstamo + devolución)
     */
    public List<Transaction> getLoanHistory(Integer loanId) {
        return DatabaseManager.callInConnection(() -> repo.getLoanHistory(loanId));
    }

    /**
     * Busca un préstamo por código
     */
    public Transaction findByLoanCode(String loanCode) {
        return DatabaseManager.callInConnection(() -> repo.findByLoanCode(loanCode));
    }

    /**
     * Busca préstamos pendientes que coincidan con el código (para autocompletado)
     * Solo devuelve préstamos con loan_status = 'pending' y status = 1 (activos)
     * @param searchText Texto a buscar en loan_code
     * @return Lista de préstamos que coinciden con la búsqueda
     */
    public List<Transaction> searchPendingLoansByCode(String searchText) {
        return DatabaseManager.callInConnection(() -> repo.searchPendingLoansByCode(searchText));
    }

    /**
     * Busca préstamos próximos a vencer
     */
    public List<Transaction> findLoansExpiringSoon(int days) {
        return DatabaseManager.callInConnection(() -> repo.findLoansExpiringSoon(days));
    }

    /**
     * Cuenta préstamos por estado
     */
    public long countLoansByStatus(String loanStatus) {
        return DatabaseManager.callInConnection(() -> repo.countLoansByStatus(loanStatus));
    }

    /**
     * Obtiene transacciones por tipo incluyendo las invalidadas
     */
    public List<Transaction> findByTypeIncludingInvalidated(String type) {
        return DatabaseManager.callInConnection(() -> {
            return Transaction.where("type = ?", type)
                .orderBy("created_at DESC");
        });
    }
}

