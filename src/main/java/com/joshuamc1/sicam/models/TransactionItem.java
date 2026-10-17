package com.joshuamc1.sicam.models;

import org.javalite.activejdbc.Model;
import org.javalite.activejdbc.annotations.Table;

/**
 * Modelo para representar un item dentro de una transacción.
 * Cada item representa un producto con su cantidad dentro de una transacción multi-producto.
 */
@Table("transaction_items")
public class TransactionItem extends Model {

    // Estados posibles
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_CANCELLED = "cancelled";

    /**
     * Obtiene el producto asociado a este item
     * @return Product asociado
     */
    public Product getProduct() {
        return this.parent(Product.class);
    }

    /**
     * Obtiene la transacción asociada a este item
     * @return Transaction asociada
     */
    public Transaction getTransaction() {
        return this.parent(Transaction.class);
    }

    /**
     * Obtiene la cantidad del producto en este item
     * @return cantidad
     */
    public Double getQuantity() {
        return getDouble("quantity");
    }

    /**
     * Obtiene las notas específicas de este item
     * @return notas del item
     */
    public String getNotes() {
        return getString("notes");
    }

    // ===== MÉTODOS DE ESTADO =====

    /**
     * Verifica si el item está activo
     * @return true si está activo
     */
    public boolean isActive() {
        String status = this.getString("status");
        return status == null || STATUS_ACTIVE.equals(status);
    }

    /**
     * Verifica si el item está cancelado
     * @return true si está cancelado
     */
    public boolean isCancelled() {
        return STATUS_CANCELLED.equals(this.getString("status"));
    }

    /**
     * Marca el item como cancelado
     */
    public void markAsCancelled() {
        this.set("status", STATUS_CANCELLED);
        this.saveIt();
    }

    /**
     * Marca el item como activo
     */
    public void markAsActive() {
        this.set("status", STATUS_ACTIVE);
        this.saveIt();
    }
}

