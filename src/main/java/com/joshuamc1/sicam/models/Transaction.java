package com.joshuamc1.sicam.models;

import org.javalite.activejdbc.Model;
import org.javalite.activejdbc.annotations.HasMany;
import org.javalite.activejdbc.annotations.Table;

import java.sql.Timestamp;
import java.util.List;

/**
 * Modelo para representar una transacción (header).
 * Una transacción puede contener múltiples productos (items).
 */
@Table("transactions")
@HasMany(child = TransactionItem.class, foreignKeyName = "transaction_id")
public class Transaction extends Model {

    public static final String TYPE_IN = "in";
    public static final String TYPE_OUT = "out";
    public static final String TYPE_LOAN = "loan";

    // Estados de transacciones
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_CANCELLED = "cancelled";
    public static final String STATUS_INVALIDATED = "invalidated";

    // Estados de préstamos
    public static final String LOAN_STATUS_PENDING = "pending";
    public static final String LOAN_STATUS_RETURNED = "returned";
    public static final String LOAN_STATUS_PAID = "paid";
    public static final String LOAN_STATUS_REPLACED = "replaced";
    public static final String LOAN_STATUS_OVERDUE = "overdue";

    // Tipos de devolución
    public static final String RETURN_TYPE_SAME_PRODUCT = "same_product";
    public static final String RETURN_TYPE_PURCHASED = "purchased";
    public static final String RETURN_TYPE_REPLACED = "replaced";
    public static final String RETURN_TYPE_OTHER = "other";

    /**
     * Obtiene todos los items de esta transacción
     * @return lista de TransactionItem
     */
    public List<TransactionItem> getItems() {
        return this.getAll(TransactionItem.class);
    }

    /**
     * Agrega un producto a la transacción
     * @param productId ID del producto
     * @param quantity cantidad del producto
     * @param notes notas específicas del item
     * @return TransactionItem creado
     */
    public TransactionItem addItem(Integer productId, double quantity, String notes) {
        TransactionItem item = new TransactionItem();
        item.set("transaction_id", this.getId());
        item.set("product_id", productId);
        item.set("quantity", quantity);
        item.set("notes", notes);
        item.saveIt();

        // Actualizar contador
        this.set("total_items", this.getItems().size());
        this.saveIt();

        return item;
    }

    /**
     * Calcula la cantidad total de productos en la transacción
     * @return suma total de cantidades
     */
    public double getTotalQuantity() {
        return this.getItems().stream()
            .mapToDouble(item -> item.getDouble("quantity"))
            .sum();
    }

    /**
     * Obtiene el número total de items en la transacción
     * @return número de items
     */
    public Integer getTotalItems() {
        return getInteger("total_items");
    }

    // ===== MÉTODOS DE SOFT DELETE / INVALIDACIÓN =====

    /**
     * Invalida la transacción (para transacciones erróneas)
     * @param invalidatedBy ID del usuario que invalida
     * @param reason razón de la invalidación
     */
    public void invalidate(Integer invalidatedBy, String reason) {
        this.set("status", STATUS_INVALIDATED);
        this.set("invalidated_at", new Timestamp(System.currentTimeMillis()));
        this.set("invalidated_by", invalidatedBy);
        this.set("invalidation_reason", reason);
        this.saveIt();
    }

    /**
     * Restaura una transacción invalidada
     */
    public void restore() {
        this.set("status", STATUS_ACTIVE);
        this.set("invalidated_at", null);
        this.set("invalidated_by", null);
        this.set("invalidation_reason", null);
        this.saveIt();
    }

    /**
     * Verifica si la transacción está invalidada
     * @return true si está invalidada
     */
    public boolean isInvalidated() {
        return STATUS_INVALIDATED.equals(this.getString("status"));
    }

    /**
     * Verifica si la transacción está activa
     * @return true si está activa
     */
    public boolean isActive() {
        String status = this.getString("status");
        return status == null || STATUS_ACTIVE.equals(status);
    }

    /**
     * Verifica si la transacción está cancelada
     * @return true si está cancelada
     */
    public boolean isCancelled() {
        return STATUS_CANCELLED.equals(this.getString("status"));
    }

    // ===== MÉTODOS DE GESTIÓN DE PRÉSTAMOS (FASE 10) =====

    /**
     * Verifica si esta transacción es un préstamo
     * @return true si es un préstamo
     */
    public boolean isLoan() {
        return TYPE_LOAN.equals(this.getString("type"));
    }

    /**
     * Verifica si el préstamo está pendiente
     * @return true si está pendiente
     */
    public boolean isLoanPending() {
        return isLoan() && LOAN_STATUS_PENDING.equals(this.getString("loan_status"));
    }

    /**
     * Verifica si el préstamo está vencido
     * @return true si está vencido
     */
    public boolean isLoanOverdue() {
        if (!isLoan() || !isActive()) return false;
        java.sql.Date expectedReturn = getDate("expected_return_date");
        return com.joshuamc1.sicam.utils.LoanDueDate.isOverdue(getString("loan_status"),
            expectedReturn == null ? null : expectedReturn.toLocalDate(), java.time.LocalDate.now());
    }

    /**
     * Marca el préstamo como devuelto
     * @param returnTransactionId ID de la transacción de devolución
     * @param returnType tipo de devolución
     * @param returnDate fecha de devolución
     * @param notes notas sobre la devolución
     */
    public void markAsReturned(Integer returnTransactionId, String returnType,
                              java.time.LocalDate returnDate, String notes) {
        this.set("loan_status", getStatusForReturnType(returnType));
        this.set("actual_return_date", returnDate.toString());
        this.set("related_transaction_id", returnTransactionId);
        this.set("return_type", returnType);
        this.set("return_notes", notes);
        this.saveIt();
    }

    /**
     * Obtiene el estado según el tipo de devolución
     * @param returnType tipo de devolución
     * @return estado correspondiente
     */
    private String getStatusForReturnType(String returnType) {
        return switch (returnType) {
            case RETURN_TYPE_SAME_PRODUCT -> LOAN_STATUS_RETURNED;
            case RETURN_TYPE_PURCHASED -> LOAN_STATUS_PAID;
            case RETURN_TYPE_REPLACED -> LOAN_STATUS_REPLACED;
            default -> LOAN_STATUS_RETURNED;
        };
    }

    /**
     * Obtiene la transacción de préstamo relacionada (si existe)
     * @return Transaction del préstamo original o null
     */
    public Transaction getRelatedLoan() {
        Integer relatedId = this.getInteger("related_transaction_id");
        if (relatedId == null) return null;

        return Transaction.findById(relatedId);
    }

    /**
     * Verifica si este ingreso es una devolución de préstamo
     * @return true si es devolución
     */
    public boolean isLoanReturn() {
        return TYPE_IN.equals(this.getString("type"))
            && this.getInteger("related_transaction_id") != null;
    }

    /**
     * Obtiene el código del préstamo
     * @return código del préstamo o null
     */
    public String getLoanCode() {
        return this.getString("loan_code");
    }

    /**
     * Obtiene el estado del préstamo
     * @return estado del préstamo
     */
    public String getLoanStatus() {
        return this.getString("loan_status");
    }

    /**
     * Verifica si el préstamo fue devuelto
     * @return true si fue devuelto
     */
    public boolean isLoanReturned() {
        return isLoan() && LOAN_STATUS_RETURNED.equals(this.getString("loan_status"));
    }

    /**
     * Verifica si el préstamo fue pagado
     * @return true si fue pagado
     */
    public boolean isLoanPaid() {
        return isLoan() && LOAN_STATUS_PAID.equals(this.getString("loan_status"));
    }

    /**
     * Verifica si el préstamo fue reemplazado
     * @return true si fue reemplazado
     */
    public boolean isLoanReplaced() {
        return isLoan() && LOAN_STATUS_REPLACED.equals(this.getString("loan_status"));
    }
}