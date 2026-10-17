package com.joshuamc1.sicam.services;

import com.joshuamc1.sicam.core.DatabaseManager;
import com.joshuamc1.sicam.core.PermissionHelper;
import com.joshuamc1.sicam.core.Session;
import com.joshuamc1.sicam.models.Rol;
import com.joshuamc1.sicam.repositories.RolRepository;

import java.util.List;

public class RolService {
    private final RolRepository repo = new RolRepository();
    private final AuditService auditService = new AuditService();

    public List<Rol> findAll() {
        return DatabaseManager.callInConnection(repo::findAll);
    }

    public Rol findById(Integer id) {
        return DatabaseManager.callInConnection(() -> repo.findById(id));
    }

    public Rol create(String nombre, String descripcion) {
        if (!PermissionHelper.canAccess("ROLES", "CREATE")) {
            throw new SecurityException("No tiene permiso para crear roles");
        }

        return DatabaseManager.callInConnection(() -> {
            Rol rol = repo.create(nombre, descripcion);
            auditService.logAction(
                Session.getCurrentUserId(),
                "create_rol",
                "roles",
                rol.getInteger("id"),
                String.format("rol_id=%d,nombre=%s", rol.getInteger("id"), nombre)
            );
            return rol;
        });
    }

    public boolean update(Integer id, String nombre, String descripcion) {
        if (!PermissionHelper.canAccess("ROLES", "UPDATE")) {
            throw new SecurityException("No tiene permiso para actualizar roles");
        }

        // No permitir editar rol ROOT
        if (repo.isRoot(id)) {
            throw new IllegalArgumentException("No se permite editar el rol ROOT");
        }

        return DatabaseManager.callInConnection(() -> {
            boolean ok = repo.update(id, nombre, descripcion);
            if (ok) {
                auditService.logAction(
                    Session.getCurrentUserId(),
                    "update_rol",
                    "roles",
                    id,
                    String.format("rol_id=%d,nombre=%s", id, nombre)
                );
            }
            return ok;
        });
    }

    public long count() {
        return DatabaseManager.callInConnection(repo::count);
    }

    // ===== MÉTODOS DE SOFT DELETE Y RESTAURACIÓN =====

    /**
     * Soft delete explícito de un rol
     */
    public void softDelete(Rol rol) {
        Integer currentUserId = Session.getCurrentUserId();
        Integer id = rol.getInteger("id");

        if (!PermissionHelper.canAccess("ROLES", "DELETE")) {
            throw new SecurityException("No tiene permiso para eliminar roles");
        }

        // No permitir eliminar rol ROOT
        if (rol.isRoot()) {
            throw new IllegalArgumentException("No se permite eliminar el rol ROOT");
        }

        // Verificar si tiene usuarios activos
        if (repo.hasActiveUsers(id)) {
            throw new IllegalStateException(
                "No se puede eliminar el rol porque tiene usuarios activos asignados. " +
                "Primero reasigne los usuarios a otro rol."
            );
        }

        DatabaseManager.runInConnection(() -> {
            repo.softDelete(rol, currentUserId);
            auditService.logAction(
                currentUserId,
                "soft_delete",
                "roles",
                id,
                String.format("rol_id=%d,nombre=%s", id, rol.getString("nombre"))
            );
        });
    }

    /**
     * Eliminación física permanente (solo ROOT)
     */
    public void hardDelete(Rol rol) {
        Integer currentUserId = Session.getCurrentUserId();
        Integer id = rol.getInteger("id");

        if (!PermissionHelper.canAccess("SISTEMA", "DESTRUCTIVE_OPERATIONS")) {
            throw new SecurityException("Solo ROOT puede ejecutar eliminaciones permanentes");
        }

        // No permitir eliminar rol ROOT
        if (rol.isRoot()) {
            throw new IllegalArgumentException("No se permite eliminar el rol ROOT");
        }

        DatabaseManager.runInConnection(() -> {
            validateBeforeHardDelete(rol);
            repo.hardDelete(rol);
            auditService.logAction(
                currentUserId,
                "hard_delete",
                "roles",
                id,
                String.format("rol_id=%d [PERMANENT]", id, rol.getString("nombre"))
            );
        });
    }

    /**
     * Restaura un rol eliminado lógicamente
     */
    public void restore(Rol rol) {
        Integer currentUserId = Session.getCurrentUserId();
        Integer id = rol.getInteger("id");

        if (!PermissionHelper.canAccess("ROLES", "RESTORE")) {
            throw new SecurityException("No tiene permiso para restaurar roles");
        }

        if (!rol.isDeleted()) {
            throw new IllegalStateException("El rol no está eliminado");
        }

        DatabaseManager.runInConnection(() -> {
            repo.restore(rol);
            auditService.logAction(
                currentUserId,
                "restore",
                "roles",
                id,
                String.format("rol_id=%d,nombre=%s", id, rol.getString("nombre"))
            );
        });
    }

    /**
     * Marca rol como inactivo
     */
    public void markAsInactive(Rol rol) {
        Integer currentUserId = Session.getCurrentUserId();
        Integer id = rol.getInteger("id");

        if (!PermissionHelper.canAccess("ROLES", "UPDATE")) {
            throw new SecurityException("No tiene permiso para modificar roles");
        }

        if (rol.isRoot()) {
            throw new IllegalArgumentException("No se permite desactivar el rol ROOT");
        }

        DatabaseManager.runInConnection(() -> {
            rol.markAsInactive();
            auditService.logAction(currentUserId, "mark_inactive", "roles", id, "status=inactive");
        });
    }

    /**
     * Marca rol como activo
     */
    public void markAsActive(Rol rol) {
        Integer currentUserId = Session.getCurrentUserId();
        Integer id = rol.getInteger("id");

        if (!PermissionHelper.canAccess("ROLES", "UPDATE")) {
            throw new SecurityException("No tiene permiso para modificar roles");
        }

        DatabaseManager.runInConnection(() -> {
            rol.markAsActive();
            auditService.logAction(currentUserId, "mark_active", "roles", id, "status=active");
        });
    }

    /**
     * Lista roles incluyendo eliminados (requiere VIEW_DELETED)
     */
    public List<Rol> findAllIncludingDeleted() {
        if (!PermissionHelper.canAccess("SISTEMA", "VIEW_DELETED")) {
            throw new SecurityException("No tiene permiso para ver roles eliminados");
        }
        return DatabaseManager.callInConnection(repo::findAllIncludingDeleted);
    }

    /**
     * Lista solo eliminados
     */
    public List<Rol> findDeleted() {
        if (!PermissionHelper.canAccess("SISTEMA", "VIEW_DELETED")) {
            throw new SecurityException("No tiene permiso para ver roles eliminados");
        }
        return DatabaseManager.callInConnection(repo::findDeleted);
    }

    public long countDeleted() {
        if (!PermissionHelper.canAccess("SISTEMA", "VIEW_DELETED")) {
            return 0L;
        }
        return DatabaseManager.callInConnection(repo::countDeleted);
    }

    /**
     * Verifica si un rol tiene usuarios activos
     */
    public boolean hasActiveUsers(Integer rolId) {
        return DatabaseManager.callInConnection(() -> repo.hasActiveUsers(rolId));
    }

    /**
     * Cuenta usuarios activos asignados a un rol
     */
    public long countActiveUsersByRole(Integer rolId) {
        return DatabaseManager.callInConnection(() -> repo.countActiveUsersByRole(rolId));
    }

    private void validateBeforeHardDelete(Rol rol) {
        if (rol.isActiveStatus()) {
            throw new IllegalStateException(
                "No se puede eliminar permanentemente un rol activo. " +
                "Primero debe marcarse como eliminado."
            );
        }

        // Verificar nuevamente si tiene usuarios
        if (repo.hasActiveUsers(rol.getInteger("id"))) {
            throw new IllegalStateException(
                "No se puede eliminar permanentemente el rol porque tiene usuarios asignados."
            );
        }
    }
}

