package com.joshuamc1.sicam.services;

import com.joshuamc1.sicam.core.DatabaseManager;
import com.joshuamc1.sicam.core.PermissionHelper;
import com.joshuamc1.sicam.core.Session;
import com.joshuamc1.sicam.core.Authorization;
import com.joshuamc1.sicam.core.PermissionPolicy;
import com.joshuamc1.sicam.models.Permiso;
import org.javalite.activejdbc.Base;
import com.joshuamc1.sicam.models.Rol;
import com.joshuamc1.sicam.repositories.RolRepository;

import java.util.List;

public class RolService {
    private final RolRepository repo = new RolRepository();
    private final AuditService auditService = new AuditService();

    public List<Rol> findAll() {
        Authorization.require("ROLES", "READ");
        return DatabaseManager.callInConnection(repo::findAll);
    }

    public Rol findById(Integer id) {
        return DatabaseManager.callInConnection(() -> repo.findById(id));
    }

    public Rol create(String nombre,String descripcion) {
        return saveRole(null,nombre,descripcion,true);
    }
    public boolean update(Integer id,String nombre,String descripcion) {
        Authorization.require("ROLES","UPDATE");
        return DatabaseManager.callInConnection(() -> {
            Rol role=repo.findById(id);
            return role != null && saveRole(id,nombre,descripcion,role.isActivo()) != null;
        });
    }

    public Rol saveRole(Integer id, String name, String description, boolean active) {
        Authorization.require("ROLES", id == null ? "CREATE" : "UPDATE");
        return DatabaseManager.callInConnection(() -> {
            Rol role = id == null ? new Rol() : repo.findById(id);
            if (role != null && role.isDeleted()) throw new SecurityException("Debe usar RESTORE para recuperar el rol");
            if (role == null || role.isRoot() || "ROOT".equalsIgnoreCase(name)) throw new SecurityException("El rol ROOT está protegido");
            if (id != null && java.util.Set.of("ADMIN","AUDITOR","SUPERVISOR","OPERADOR","CONSULTOR").contains(role.getString("nombre"))
                    && !role.getString("nombre").equals(name)) throw new SecurityException("No se puede renombrar un rol del sistema");
            role.set("nombre",name); role.set("descripcion",description);
            role.set("activo",active ? 1 : 0); role.set("status",active ? Rol.STATUS_ACTIVE : Rol.STATUS_INACTIVE);
            role.saveIt();
            auditService.logAction(Session.getCurrentUserId(),id == null ? "create_rol" : "update_rol","roles",role.getInteger("id"),"nombre="+name+",active="+active);
            return role;
        });
    }

    public void updatePermissions(Integer roleId, List<Integer> permissionIds) {
        Authorization.require("ROLES", "ASSIGN_PERMISSIONS");
        DatabaseManager.runInConnection(() -> {
            Rol role = repo.findById(roleId);
            if (role == null || role.isRoot()) throw new SecurityException("Los permisos de ROOT son inmutables");
            String actorRole = Authorization.currentRole();
            java.util.Set<Integer> unique = new java.util.LinkedHashSet<>(permissionIds);
            for (Integer id : unique) {
                Permiso permission = Permiso.findById(id);
                if (permission == null || !PermissionPolicy.canDelegate(actorRole, role.getString("nombre"),
                        permission.getString("modulo"), permission.getString("accion"),
                        Authorization.canAccess(permission.getString("modulo"),permission.getString("accion")))) {
                    throw new SecurityException("No cuenta con permisos suficientes para delegar ese permiso");
                }
            }
            String before = role.getPermisos().stream().map(p -> String.valueOf(p.getId())).collect(java.util.stream.Collectors.joining(","));
            DatabaseManager.callInTransaction(() -> {
                role.sincronizarPermisos(new java.util.ArrayList<>(unique));
                auditService.logAction(Session.getCurrentUserId(),"assign_permissions","roles",roleId,"before="+before+",after="+unique);
                return null;
            });
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
        if (java.util.Set.of("ROOT","ADMIN","AUDITOR","SUPERVISOR","OPERADOR","CONSULTOR").contains(rol.getString("nombre"))) throw new SecurityException("No se puede eliminar un rol del sistema");
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
        if (java.util.Set.of("ROOT","ADMIN","AUDITOR","SUPERVISOR","OPERADOR","CONSULTOR").contains(rol.getString("nombre"))) throw new SecurityException("No se puede eliminar un rol del sistema");
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
        if (rol.isDeleted()) throw new SecurityException("Los eliminados deben recuperarse mediante RESTORE");
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
        Authorization.require("ROLES","READ");
        if (!PermissionHelper.canAccess("SISTEMA", "VIEW_DELETED")) {
            throw new SecurityException("No tiene permiso para ver roles eliminados");
        }
        return DatabaseManager.callInConnection(repo::findAllIncludingDeleted);
    }

    /**
     * Lista solo eliminados
     */
    public List<Rol> findDeleted() {
        Authorization.require("ROLES","READ");
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

