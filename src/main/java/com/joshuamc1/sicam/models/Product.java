package com.joshuamc1.sicam.models;

import org.javalite.activejdbc.Model;
import org.javalite.activejdbc.annotations.Table;

import java.sql.Timestamp;

@Table("products")
public class Product extends Model {

    // Estados posibles
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_INACTIVE = "inactive";
    public static final String STATUS_DELETED = "deleted";

    public boolean isLowStock() {
        Double currentStock = getDouble("current_stock");
        Double minimumStock = getDouble("minimum_stock");
        return currentStock != null && minimumStock != null && currentStock <= minimumStock;
    }

    public void updateStock(double quantity) {
        Double currentStock = getDouble("current_stock");
        set("current_stock", currentStock + quantity);
    }

    // ===== MÉTODOS DE SOFT DELETE Y RESTAURACIÓN =====

    /**
     * Realiza eliminación lógica del producto
     * @param deletedBy ID del usuario que elimina
     */
    public void softDelete(Integer deletedBy) {
        this.set("status", STATUS_DELETED);
        this.set("deleted_at", new Timestamp(System.currentTimeMillis()));
        this.set("deleted_by", deletedBy);
        this.saveIt();
    }

    /**
     * Restaura un producto eliminado lógicamente
     */
    public void restore() {
        this.set("status", STATUS_ACTIVE);
        this.set("deleted_at", null);
        this.set("deleted_by", null);
        this.saveIt();
    }

    /**
     * Verifica si el producto está eliminado
     * @return true si está eliminado
     */
    public boolean isDeleted() {
        return STATUS_DELETED.equals(this.getString("status"));
    }

    /**
     * Verifica si el producto está activo
     * @return true si está activo
     */
    public boolean isActive() {
        String status = this.getString("status");
        return status == null || STATUS_ACTIVE.equals(status);
    }

    /**
     * Verifica si el producto está inactivo
     * @return true si está inactivo
     */
    public boolean isInactive() {
        return STATUS_INACTIVE.equals(this.getString("status"));
    }

    /**
     * Cambia el estado del producto a inactivo
     */
    public void markAsInactive() {
        this.set("status", STATUS_INACTIVE);
        this.saveIt();
    }

    /**
     * Cambia el estado del producto a activo
     */
    public void markAsActive() {
        this.set("status", STATUS_ACTIVE);
        this.saveIt();
    }
}