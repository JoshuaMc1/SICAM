package com.joshuamc1.sicam.models;

import org.javalite.activejdbc.Base;
import org.javalite.activejdbc.Model;
import org.javalite.activejdbc.annotations.Table;

import java.sql.Timestamp;
import java.util.List;

@Table("roles")
public class Rol extends Model {

    // Estados posibles
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_INACTIVE = "inactive";
    public static final String STATUS_DELETED = "deleted";

    /**
     * Obtiene todos los permisos asociados a este rol
     */
    public List<Permiso> getPermisos() {
        String sql = "SELECT p.* FROM permisos p " +
                    "INNER JOIN rol_permisos rp ON p.id = rp.permiso_id " +
                    "WHERE rp.rol_id = ?";
        return Permiso.findBySQL(sql, getId());
    }

    /**
     * Asigna un permiso a este rol
     */
    public void asignarPermiso(int permisoId) {
        String sql = "INSERT INTO rol_permisos (rol_id, permiso_id) VALUES (?, ?) " +
                    "ON DUPLICATE KEY UPDATE rol_id = rol_id";
        Base.exec(sql, getId(), permisoId);
    }

    /**
     * Elimina un permiso de este rol
     */
    public void removerPermiso(int permisoId) {
        String sql = "DELETE FROM rol_permisos WHERE rol_id = ? AND permiso_id = ?";
        Base.exec(sql, getId(), permisoId);
    }

    /**
     * Verifica si el rol tiene un permiso especifico
     */
    public boolean tienePermiso(String modulo, String accion) {
        String sql = "SELECT COUNT(*) as count FROM permisos p " +
                    "INNER JOIN rol_permisos rp ON p.id = rp.permiso_id " +
                    "WHERE rp.rol_id = ? AND p.modulo = ? AND p.accion = ?";
        Long count = (Long) Base.firstCell(sql, getId(), modulo, accion);
        return count != null && count > 0;
    }

    /**
     * Sincroniza los permisos del rol con una lista nueva
     */
    public void sincronizarPermisos(List<Integer> permisosIds) {
        // Eliminar todos los permisos actuales
        String deleteSql = "DELETE FROM rol_permisos WHERE rol_id = ?";
        Base.exec(deleteSql, getId());

        // Asignar los nuevos permisos
        if (permisosIds != null && !permisosIds.isEmpty()) {
            for (Integer permisoId : permisosIds) {
                asignarPermiso(permisoId);
            }
        }
    }

    /**
     * Obtiene todos los usuarios con este rol
     */
    public List<User> getUsuarios() {
        return User.where("rol_id = ?", getId());
    }

    /**
     * Verifica si el rol es ROOT
     */
    public boolean isRoot() {
        return "ROOT".equalsIgnoreCase(getString("nombre"));
    }

    /**
     * Verifica si el rol esta activo
     */
    public boolean isActivo() {
        Object activo = get("activo");
        return activo != null && (activo.equals(1) || activo.equals(true));
    }

    // ===== MÉTODOS DE SOFT DELETE Y RESTAURACIÓN =====

    /**
     * Realiza eliminación lógica del rol
     * @param deletedBy ID del usuario que elimina
     */
    public void softDelete(Integer deletedBy) {
        this.set("status", STATUS_DELETED);
        this.set("deleted_at", new Timestamp(System.currentTimeMillis()));
        this.set("deleted_by", deletedBy);
        this.set("activo", 0); // Mantener compatibilidad
        this.saveIt();
    }

    /**
     * Restaura un rol eliminado lógicamente
     */
    public void restore() {
        this.set("status", STATUS_ACTIVE);
        this.set("deleted_at", null);
        this.set("deleted_by", null);
        this.set("activo", 1); // Mantener compatibilidad
        this.saveIt();
    }

    /**
     * Verifica si el rol está eliminado
     * @return true si está eliminado
     */
    public boolean isDeleted() {
        return STATUS_DELETED.equals(this.getString("status"));
    }

    /**
     * Verifica si el rol está activo (usando nuevo campo status)
     * @return true si está activo
     */
    public boolean isActiveStatus() {
        String status = this.getString("status");
        return status == null || STATUS_ACTIVE.equals(status);
    }

    /**
     * Verifica si el rol está inactivo
     * @return true si está inactivo
     */
    public boolean isInactiveStatus() {
        return STATUS_INACTIVE.equals(this.getString("status"));
    }

    /**
     * Cambia el estado del rol a inactivo
     */
    public void markAsInactive() {
        this.set("status", STATUS_INACTIVE);
        this.set("activo", 0); // Mantener compatibilidad
        this.saveIt();
    }

    /**
     * Cambia el estado del rol a activo
     */
    public void markAsActive() {
        this.set("status", STATUS_ACTIVE);
        this.set("activo", 1); // Mantener compatibilidad
        this.saveIt();
    }
}

