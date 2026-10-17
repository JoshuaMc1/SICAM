package com.joshuamc1.sicam.models;

import org.javalite.activejdbc.Model;
import org.javalite.activejdbc.annotations.Table;

import java.sql.Timestamp;

@Table("people")
public class Person extends Model {

    // Estados posibles
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_INACTIVE = "inactive";
    public static final String STATUS_DELETED = "deleted";

    public String getFullName() {
        return getString("first_name") + " " + getString("last_name");
    }

    // ===== MÉTODOS DE SOFT DELETE Y RESTAURACIÓN =====

    /**
     * Realiza eliminación lógica de la persona
     * @param deletedBy ID del usuario que elimina
     */
    public void softDelete(Integer deletedBy) {
        this.set("status", STATUS_DELETED);
        this.set("deleted_at", new Timestamp(System.currentTimeMillis()));
        this.set("deleted_by", deletedBy);
        this.saveIt();
    }

    /**
     * Restaura una persona eliminada lógicamente
     */
    public void restore() {
        this.set("status", STATUS_ACTIVE);
        this.set("deleted_at", null);
        this.set("deleted_by", null);
        this.saveIt();
    }

    /**
     * Verifica si la persona está eliminada
     * @return true si está eliminada
     */
    public boolean isDeleted() {
        return STATUS_DELETED.equals(this.getString("status"));
    }

    /**
     * Verifica si la persona está activa
     * @return true si está activa
     */
    public boolean isActive() {
        String status = this.getString("status");
        return status == null || STATUS_ACTIVE.equals(status);
    }

    /**
     * Verifica si la persona está inactiva
     * @return true si está inactiva
     */
    public boolean isInactive() {
        return STATUS_INACTIVE.equals(this.getString("status"));
    }

    /**
     * Cambia el estado de la persona a inactivo
     */
    public void markAsInactive() {
        this.set("status", STATUS_INACTIVE);
        this.saveIt();
    }

    /**
     * Cambia el estado de la persona a activo
     */
    public void markAsActive() {
        this.set("status", STATUS_ACTIVE);
        this.saveIt();
    }
}