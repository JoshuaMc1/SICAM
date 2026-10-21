package com.joshuamc1.sicam.repositories;

import com.joshuamc1.sicam.models.Rol;

import java.util.List;

public class RolRepository {

    // ===== MÉTODOS DE BÚSQUEDA CON FILTRADO DE ESTADO =====

    public Rol findById(Integer id) {
        return Rol.findById(id);
    }

    public Rol findByNombre(String nombre) {
        // Solo roles activos
        return Rol.findFirst("nombre = ? AND status = ?", nombre, Rol.STATUS_ACTIVE);
    }

    /**
     * Busca rol por nombre incluyendo inactivos/eliminados
     */
    public Rol findByNombreIncludingDeleted(String nombre) {
        return Rol.findFirst("nombre = ?", nombre);
    }

    /**
     * Lista todos los roles activos (excluye eliminados)
     */
    public List<Rol> findAll() {
        return Rol.where("status != ?", Rol.STATUS_DELETED)
            .orderBy("id DESC");
    }

    /**
     * Lista todos los roles incluyendo eliminados
     */
    public List<Rol> findAllIncludingDeleted() {
        return Rol.findAll().orderBy("id DESC");
    }

    /**
     * Lista solo roles eliminados
     */
    public List<Rol> findDeleted() {
        return Rol.where("status = ?", Rol.STATUS_DELETED)
            .orderBy("id DESC");
    }

    /**
     * Lista solo roles activos
     */
    public List<Rol> findActive() {
        return Rol.where("status = ?", Rol.STATUS_ACTIVE)
            .orderBy("id DESC");
    }

    /**
     * Lista solo roles inactivos
     */
    public List<Rol> findInactive() {
        return Rol.where("status = ?", Rol.STATUS_INACTIVE)
            .orderBy("id DESC");
    }

    public Rol create(String nombre, String descripcion) {
        Rol rol = new Rol();
        rol.set("nombre", nombre);
        rol.set("descripcion", descripcion);
        rol.set("status", Rol.STATUS_ACTIVE);
        rol.set("activo", 1); // Mantener compatibilidad
        rol.saveIt();
        return rol;
    }

    public boolean update(Integer id, String nombre, String descripcion) {
        Rol rol = Rol.findById(id);
        if (rol == null) return false;
        rol.set("nombre", nombre);
        rol.set("descripcion", descripcion);
        return rol.saveIt();
    }

    /**
     * Cuenta roles activos (excluye eliminados)
     */
    public long count() {
        Long c = Rol.count("status != ?", Rol.STATUS_DELETED);
        return c == null ? 0L : c;
    }

    /**
     * Verifica si un rol es ROOT
     */
    public boolean isRoot(Integer id) {
        Rol rol = Rol.findById(id);
        return rol != null && rol.isRoot();
    }

    /**
     * Obtiene el rol ROOT
     */
    public Rol getRootRole() {
        return Rol.findFirst("nombre = ?", "ROOT");
    }

    // ===== MÉTODOS DE SOFT DELETE Y RESTAURACIÓN =====

    /**
     * Realiza soft delete del rol
     */
    public void softDelete(Rol rol, Integer deletedBy) {
        rol.softDelete(deletedBy);
    }

    /**
     * Eliminación física permanente (solo ROOT)
     */
    public void hardDelete(Rol rol) {
        rol.delete();
    }

    /**
     * Restaura un rol eliminado
     */
    public void restore(Rol rol) {
        rol.restore();
    }

    /**
     * Cuenta roles eliminados
     */
    public long countDeleted() {
        Long c = Rol.count("status = ?", Rol.STATUS_DELETED);
        return c == null ? 0L : c;
    }

    /**
     * Verifica si un rol tiene usuarios asignados activos
     */
    public boolean hasActiveUsers(Integer rolId) {
        Rol rol = Rol.findById(rolId);
        if (rol == null) return false;

        long activeUsers = com.joshuamc1.sicam.models.User.count(
            "rol_id = ? AND status = ?",
            rolId,
            com.joshuamc1.sicam.models.User.STATUS_ACTIVE
        );

        return activeUsers > 0;
    }

    /**
     * Cuenta usuarios asignados a un rol (solo activos)
     */
    public long countActiveUsersByRole(Integer rolId) {
        Long c = com.joshuamc1.sicam.models.User.count(
            "rol_id = ? AND status = ?",
            rolId,
            com.joshuamc1.sicam.models.User.STATUS_ACTIVE
        );
        return c == null ? 0L : c;
    }

    // ===== MÉTODOS LEGACY (mantener compatibilidad) =====

    /**
     * @deprecated Usar softDelete() o hardDelete() según el caso
     */
    @Deprecated
    public boolean delete(Integer id) {
        Rol rol = Rol.findById(id);
        if (rol == null) return false;
        return rol.delete();
    }

    public void save(Rol rol) {
        rol.saveIt();
    }
}

