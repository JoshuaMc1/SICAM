package com.joshuamc1.sicam.services;

import com.joshuamc1.sicam.core.DatabaseManager;
import com.joshuamc1.sicam.core.Session;
import com.joshuamc1.sicam.core.Authorization;
import com.joshuamc1.sicam.models.Rol;
import com.joshuamc1.sicam.models.User;
import com.joshuamc1.sicam.repositories.UserRepository;

import java.util.List;

public class UserService {
    private final UserRepository repo = new UserRepository();
    private final AuditService auditService = new AuditService();

    public User login(String username, String password) {
        return DatabaseManager.callInConnection(() -> {
            User user = repo.findByUsernameIncludingDeleted(username);

            // Usuario no encontrado
            if (user == null) {
                auditService.logAction(
                    null,
                    AuditService.ACTION_LOGIN_FAILED,
                    "users",
                    null,
                    String.format("username=%s, reason=user_not_found", username)
                );
                return null;
            }

            // FASE 8: Validar estado del usuario
            String status = user.getString("status");
            if (status == null) {
                status = "active"; // Por compatibilidad con datos antiguos
            }

            if (!"active".equals(status)) {
                String reason;
                if ("deleted".equals(status)) {
                    reason = "Usuario eliminado del sistema";
                } else if ("inactive".equals(status)) {
                    reason = "Usuario inactivo";
                } else {
                    reason = "Usuario no disponible (estado: " + status + ")";
                }

                // Registrar intento de login bloqueado
                auditService.logAction(
                    user.getInteger("id"),
                    AuditService.ACTION_LOGIN_BLOCKED,
                    "users",
                    user.getInteger("id"),
                    String.format("username=%s, status=%s, reason=%s", username, status, reason)
                );

                return null;
            }

            Rol loginRole = user.getRol();
            if (loginRole == null || !loginRole.isActiveStatus() || !loginRole.isActivo()) return null;
            if (user.getString("password") == null || user.getString("password").isBlank()) return null;
            // Validar contraseña
            if (!user.validatePassword(password)) {
                auditService.logAction(
                    user.getInteger("id"),
                    AuditService.ACTION_LOGIN_FAILED,
                    "users",
                    user.getInteger("id"),
                    String.format("username=%s, reason=invalid_password", username)
                );
                return null;
            }

            // Login exitoso
            auditService.logAction(
                user.getInteger("id"),
                AuditService.ACTION_LOGIN,
                "users",
                user.getInteger("id"),
                String.format("user_id=%d, username=%s, successful_login", user.getInteger("id"), username)
            );

            return user;
        });
    }

    public User createUser(String username, String password, String role) {
        return createUserFull(username,username,password,role);
    }
    public User createUserFull(String name, String username, String password, String role) {
        Authorization.require("USUARIOS","CREATE");
        return DatabaseManager.callInConnection(() -> {
            Rol target = Rol.findFirst("UPPER(nombre)=?",role.toUpperCase(java.util.Locale.ROOT));
            if (target == null) throw new IllegalArgumentException("Rol no encontrado");
            return createUserWithRoleId(name,username,password,target.getInteger("id"));
        });
    }
    public User createUserWithRoleId(String name,String username,String password,Integer roleId) {
        Authorization.require("USUARIOS","CREATE");
        return DatabaseManager.callInConnection(() -> {
            validateAssignedRole(roleId);
            if ("SYSTEM".equalsIgnoreCase(username)) throw new SecurityException("Usuario reservado");
            User user = new User(); user.set("name",name); user.set("username",username);
            user.setPassword(password); user.set("rol_id",roleId); user.set("status","active"); user.set("must_change_password",1);
            user.saveIt();
            auditService.logAction(Session.getCurrentUserId(),"create_user","users",user.getInteger("id"),"rol_id="+roleId);
            return user;
        });
    }
    public boolean updateUser(Integer id,String name,String username,String role,String newPassword) {
        Authorization.require("USUARIOS","UPDATE");
        return DatabaseManager.callInConnection(() -> {
            Rol target = Rol.findFirst("UPPER(nombre)=?",role.toUpperCase(java.util.Locale.ROOT));
            if (target == null) throw new IllegalArgumentException("Rol no encontrado");
            return updateUserWithRoleId(id,name,username,target.getInteger("id"),newPassword);
        });
    }
    public boolean updateUserWithRoleId(Integer id,String name,String username,Integer roleId,String newPassword) {
        Authorization.require("USUARIOS","UPDATE");
        return DatabaseManager.callInConnection(() -> {
            User user = repo.findById(id);
            if (user == null) return false;
            if (user.isRootRole() || "SYSTEM".equalsIgnoreCase(user.getString("username"))) throw new SecurityException("Usuario protegido");
            if (!java.util.Objects.equals(user.getInteger("rol_id"),roleId)) {
                Authorization.require("USUARIOS","CHANGE_ROLE");
                if (id.equals(Session.getCurrentUserId())) throw new SecurityException("No puede cambiar su propio rol");
                validateAssignedRole(roleId);
            }
            if (!user.getString("username").equals(username) && !Authorization.isRoot()) throw new SecurityException("Solo ROOT puede cambiar el nombre de usuario");
            if ("SYSTEM".equalsIgnoreCase(username)) throw new SecurityException("Usuario reservado");
            user.set("name",name); user.set("username",username); user.set("rol_id",roleId);
            if(newPassword!=null && !newPassword.isBlank()) { user.setPassword(newPassword); user.set("must_change_password",1); }
            user.saveIt();
            auditService.logAction(Session.getCurrentUserId(),"update_user","users",id,"rol_id="+roleId);
            return true;
        });
    }
    private void validateAssignedRole(Integer roleId) {
        Rol role = Rol.findById(roleId);
        if (role == null || role.isRoot() || !role.isActiveStatus() || !role.isActivo()) throw new SecurityException("Rol no asignable");
        if (!Authorization.isRoot()) {
            for (com.joshuamc1.sicam.models.Permiso permission : role.getPermisos()) {
                String module=permission.getString("modulo"), action=permission.getString("accion");
                if (com.joshuamc1.sicam.core.PermissionPolicy.isSupported(module,action) && !Authorization.canAccess(module,action))
                    throw new SecurityException("No puede asignar un rol con permisos superiores a los suyos");
            }
        }
    }

    public boolean resetPassword(Integer id, String newPassword, boolean mustChange) {
        Authorization.require("USUARIOS","UPDATE");
        return DatabaseManager.callInConnection(() -> {
            User target=repo.findById(id);
            if (target != null && "SYSTEM".equalsIgnoreCase(target.getString("username"))) throw new SecurityException("El usuario SYSTEM está protegido");
            // Seguridad: No permitir resetear contraseña de usuarios root
            if (repo.isRootUser(id)) {
                throw new IllegalArgumentException("No se permite resetear la contraseña de usuarios root por seguridad");
            }

            boolean ok = repo.resetPassword(id, newPassword, mustChange);
            if (ok) {
                auditService.logAction(
                    Session.getCurrentUserId(),
                    "reset_password",
                    "users",
                    id,
                    String.format("user_id=%d,must_change=%b", id, mustChange)
                );
            }
            return ok;
        });
    }

    @Deprecated
    public boolean deleteUser(Integer id) {
        Authorization.require("USUARIOS","DELETE");
        User user=findById(id);
        if(user==null) return false;
        softDelete(user);
        return true;
    }

    public User findById(Integer id) {
        Authorization.requireActiveSession();
        if (!java.util.Objects.equals(id,Session.getCurrentUserId())) Authorization.require("USUARIOS","READ");
        return DatabaseManager.callInConnection(() -> repo.findById(id));
    }

    public long countUsers() {
        Authorization.require("USUARIOS","READ");
        return DatabaseManager.callInConnection(repo::countUsers);
    }

    public List<User> listPaged(int offset, int limit, String search) {
        Authorization.require("USUARIOS","READ");
        return DatabaseManager.callInConnection(() -> repo.listPaged(offset, limit, search));
    }

    // ===== MÉTODOS DE SOFT DELETE Y RESTAURACIÓN =====

    /**
     * Soft delete explícito de un usuario
     */
    public void softDelete(User user) {
        if ("SYSTEM".equalsIgnoreCase(user.getString("username"))) throw new SecurityException("El usuario SYSTEM está protegido");
        if (java.util.Objects.equals(user.getInteger("id"), Session.getCurrentUserId())) throw new SecurityException("No puede eliminar o desactivar su propia cuenta");
        Integer currentUserId = Session.getCurrentUserId();
        Integer id = user.getInteger("id");

        if (user.isRootRole()) {
            throw new IllegalArgumentException("No se permite eliminar usuarios root");
        }

        if (!com.joshuamc1.sicam.core.PermissionHelper.canAccess("USUARIOS", "DELETE")) {
            throw new SecurityException("No tiene permiso para eliminar usuarios");
        }

        DatabaseManager.runInConnection(() -> {
            repo.softDelete(user, currentUserId);
            auditService.logAction(
                currentUserId,
                "soft_delete",
                "users",
                id,
                String.format("user_id=%d,username=%s", id, user.getString("username"))
            );
        });
    }

    /**
     * Eliminación física permanente (solo ROOT)
     */
    public void hardDelete(User user) {
        if ("SYSTEM".equalsIgnoreCase(user.getString("username"))) throw new SecurityException("El usuario SYSTEM está protegido");
        if (java.util.Objects.equals(user.getInteger("id"), Session.getCurrentUserId())) throw new SecurityException("No puede eliminar o desactivar su propia cuenta");
        Integer currentUserId = Session.getCurrentUserId();
        Integer id = user.getInteger("id");

        if (user.isRootRole()) {
            throw new IllegalArgumentException("No se permite eliminar usuarios root");
        }

        if (!com.joshuamc1.sicam.core.PermissionHelper.canAccess("SISTEMA", "DESTRUCTIVE_OPERATIONS")) {
            throw new SecurityException("Solo ROOT puede ejecutar eliminaciones permanentes");
        }

        DatabaseManager.runInConnection(() -> {
            validateBeforeHardDelete(user);
            repo.hardDelete(user);
            auditService.logAction(
                currentUserId,
                "hard_delete",
                "users",
                id,
                String.format("user_id=%d [PERMANENT]", id, user.getString("username"))
            );
        });
    }

    /**
     * Restaura un usuario eliminado lógicamente
     */
    public void restore(User user) {
        if ("SYSTEM".equalsIgnoreCase(user.getString("username"))) throw new SecurityException("El usuario SYSTEM está protegido");
        Integer currentUserId = Session.getCurrentUserId();
        Integer id = user.getInteger("id");

        if (!com.joshuamc1.sicam.core.PermissionHelper.canAccess("USUARIOS", "RESTORE")) {
            throw new SecurityException("No tiene permiso para restaurar usuarios");
        }

        if (!user.isDeleted()) {
            throw new IllegalStateException("El usuario no está eliminado");
        }

        DatabaseManager.runInConnection(() -> {
            repo.restore(user);
            auditService.logAction(
                currentUserId,
                "restore",
                "users",
                id,
                String.format("user_id=%d,username=%s", id, user.getString("username"))
            );
        });
    }

    /**
     * Marca usuario como inactivo
     */
    public void markAsInactive(User user) {
        if ("SYSTEM".equalsIgnoreCase(user.getString("username"))) throw new SecurityException("El usuario SYSTEM está protegido");
        if (java.util.Objects.equals(user.getInteger("id"), Session.getCurrentUserId())) throw new SecurityException("No puede eliminar o desactivar su propia cuenta");
        Integer currentUserId = Session.getCurrentUserId();
        Integer id = user.getInteger("id");

        if (user.isRootRole()) {
            throw new IllegalArgumentException("No se permite desactivar usuarios root");
        }

        if (!com.joshuamc1.sicam.core.PermissionHelper.canAccess("USUARIOS", "UPDATE")) {
            throw new SecurityException("No tiene permiso para modificar usuarios");
        }

        DatabaseManager.runInConnection(() -> {
            user.markAsInactive();
            auditService.logAction(currentUserId, "mark_inactive", "users", id, "status=inactive");
        });
    }

    /**
     * Marca usuario como activo
     */
    public void markAsActive(User user) {
        if (user.isDeleted()) throw new SecurityException("Los eliminados deben recuperarse mediante RESTORE");
        if ("SYSTEM".equalsIgnoreCase(user.getString("username"))) throw new SecurityException("El usuario SYSTEM está protegido");
        Integer currentUserId = Session.getCurrentUserId();
        Integer id = user.getInteger("id");

        if (!com.joshuamc1.sicam.core.PermissionHelper.canAccess("USUARIOS", "UPDATE")) {
            throw new SecurityException("No tiene permiso para modificar usuarios");
        }

        DatabaseManager.runInConnection(() -> {
            user.markAsActive();
            auditService.logAction(currentUserId, "mark_active", "users", id, "status=active");
        });
    }

    private List<User> visibleUsers(List<User> users) {
        boolean root = Authorization.isRoot();
        return users.stream().filter(user -> root || !user.isRootRole())
            .collect(java.util.stream.Collectors.toList());
    }

    /** Lista usuarios activos e inactivos, excluyendo eliminados. */
    public List<User> findAll() {
        Authorization.require("USUARIOS", "READ");
        return DatabaseManager.callInConnection(() -> visibleUsers(repo.findAll()));
    }

    /** Lista usuarios incluyendo eliminados (requiere VIEW_DELETED). */
    public List<User> findAllIncludingDeleted() {
        if (!com.joshuamc1.sicam.core.PermissionHelper.canAccess("SISTEMA", "VIEW_DELETED")) {
            throw new SecurityException("No tiene permiso para ver usuarios eliminados");
        }
        return DatabaseManager.callInConnection(() -> visibleUsers(repo.findAllIncludingDeleted()));
    }

    /**
     * Lista solo eliminados
     */
    public List<User> findDeleted() {
        if (!com.joshuamc1.sicam.core.PermissionHelper.canAccess("SISTEMA", "VIEW_DELETED")) {
            throw new SecurityException("No tiene permiso para ver usuarios eliminados");
        }
        return DatabaseManager.callInConnection(() -> visibleUsers(repo.findDeleted()));
    }

    public long countDeleted() {
        if (!com.joshuamc1.sicam.core.PermissionHelper.canAccess("SISTEMA", "VIEW_DELETED")) {
            return 0L;
        }
        return DatabaseManager.callInConnection(repo::countDeleted);
    }

    private void validateBeforeHardDelete(User user) {
        if (user.isActiveStatus()) {
            throw new IllegalStateException(
                "No se puede eliminar permanentemente un usuario activo. " +
                "Primero debe marcarse como eliminado."
            );
        }
    }

    /**
     * Verifica si existe un usuario con el username dado
     */
    public boolean existsByUsername(String username) {
        Authorization.requireAny("USUARIOS","READ","CREATE","UPDATE");
        return DatabaseManager.callInConnection(() -> {
            User user = repo.findByUsernameIncludingDeleted(username);
            return user != null;
        });
    }

    /**
     * Obtiene el ID de un usuario por su username
     */
    public Integer getIdByUsername(String username) {
        Authorization.requireAny("USUARIOS","READ","CREATE","UPDATE");
        return DatabaseManager.callInConnection(() -> {
            User user = repo.findByUsernameIncludingDeleted(username);
            return user != null ? user.getInteger("id") : null;
        });
    }
}