package com.joshuamc1.sicam.models;

import org.javalite.activejdbc.Model;
import org.javalite.activejdbc.annotations.Table;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Timestamp;

@Table("users")
public class User extends Model {

    // Estados posibles
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_INACTIVE = "inactive";
    public static final String STATUS_DELETED = "deleted";

    public boolean validatePassword(String password) {
        return BCrypt.checkpw(password, getString("password"));
    }

    public void setPassword(String password) {
        String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());
        set("password", hashedPassword);
    }

    /**
     * Verifica si es admin (compatibilidad con sistema de roles)
     */
    public boolean isAdmin() {
        Rol rol = getRol();
        if (rol != null) {
            return "ADMIN".equalsIgnoreCase(rol.getString("nombre"));
        }
        return false;
    }

    /**
     * Verifica si es root (compatibilidad con sistema de roles)
     */
    public boolean isRoot() {
        return isRootRole();
    }

    /**
     * Verifica si es operador (compatibilidad con sistema de roles)
     */
    public boolean isOperator() {
        Rol rol = getRol();
        if (rol != null) {
            return "OPERADOR".equalsIgnoreCase(rol.getString("nombre"));
        }
        return false;
    }

    /**
     * Obtiene el rol del usuario desde el nuevo sistema
     */
    public Rol getRol() {
        Object rolId = get("rol_id");
        if (rolId != null) {
            return Rol.findById(rolId);
        }
        return null;
    }

    /**
     * Verifica si el usuario tiene el rol ROOT
     */
    public boolean isRootRole() {
        Rol rol = getRol();
        return rol != null && rol.isRoot();
    }

    /**
     * Verifica si el usuario tiene un permiso específico
     */
    public boolean tienePermiso(String modulo, String accion) {
        Rol rol = getRol();
        return rol != null && rol.tienePermiso(modulo, accion);
    }

    /**
     * Obtiene el nombre del rol del usuario
     */
    public String getRolNombre() {
        Rol rol = getRol();
        return rol != null ? rol.getString("nombre") : "SIN ROL";
    }

    // ===== MÉTODOS DE SOFT DELETE Y RESTAURACIÓN =====

    /**
     * Realiza eliminación lógica del usuario
     * @param deletedBy ID del usuario que elimina
     */
    public void softDelete(Integer deletedBy) {
        this.set("status", STATUS_DELETED);
        this.set("deleted_at", new Timestamp(System.currentTimeMillis()));
        this.set("deleted_by", deletedBy);
        this.saveIt();
    }

    /**
     * Restaura un usuario eliminado lógicamente
     */
    public void restore() {
        this.set("status", STATUS_ACTIVE);
        this.set("deleted_at", null);
        this.set("deleted_by", null);
        this.saveIt();
    }

    /**
     * Verifica si el usuario está eliminado
     * @return true si está eliminado
     */
    public boolean isDeleted() {
        return STATUS_DELETED.equals(this.getString("status"));
    }

    /**
     * Verifica si el usuario está activo (puede hacer login)
     * @return true si está activo
     */
    public boolean isActiveStatus() {
        String status = this.getString("status");
        return status == null || STATUS_ACTIVE.equals(status);
    }

    /**
     * Verifica si el usuario está inactivo
     * @return true si está inactivo
     */
    public boolean isInactiveStatus() {
        return STATUS_INACTIVE.equals(this.getString("status"));
    }

    /**
     * Cambia el estado del usuario a inactivo (no puede hacer login)
     */
    public void markAsInactive() {
        this.set("status", STATUS_INACTIVE);
        this.saveIt();
    }

    /**
     * Cambia el estado del usuario a activo
     */
    public void markAsActive() {
        this.set("status", STATUS_ACTIVE);
        this.saveIt();
    }
}