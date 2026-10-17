package com.joshuamc1.sicam.services;

import com.joshuamc1.sicam.core.DatabaseManager;
import com.joshuamc1.sicam.core.Session;
import com.joshuamc1.sicam.models.User;
import com.joshuamc1.sicam.repositories.UserRepository;

import java.util.List;

public class UserService {
    private final UserRepository repo = new UserRepository();
    private final AuditService auditService = new AuditService();

    public User login(String username, String password) {
        return DatabaseManager.callInConnection(() -> {
            User user = repo.findByUsername(username);

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

                // Retornar null con un atributo especial para distinguir
                User blockedUser = new User();
                blockedUser.set("id", -1);
                blockedUser.set("username", username);
                blockedUser.set("blocked_reason", reason);
                blockedUser.set("status", status);
                return blockedUser;
            }

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
        return DatabaseManager.callInConnection(() -> {
            // password en texto plano: el modelo User.setPassword hará el hash
            User user = repo.create(username, password, role);

            auditService.logAction(
                    Session.getCurrentUserId(),
                    "create_user",
                    "users",
                    user.getInteger("id"),
                    String.format("user_id=%d,username=%s,role=%s", user.getInteger("id"), username, role)
            );

            return user;
        });
    }

    public User createUserFull(String name, String username, String password, String role) {
        return DatabaseManager.callInConnection(() -> {
            // Seguridad: No permitir crear usuarios con rol root
            if ("root".equals(role)) {
                throw new IllegalArgumentException("No se permite crear usuarios con rol root por seguridad");
            }

            // password en texto plano: el modelo User.setPassword hará el hash
            User user = repo.createFull(name, username, password, role);

            auditService.logAction(
                    Session.getCurrentUserId(),
                    "create_user",
                    "users",
                    user.getInteger("id"),
                    String.format("user_id=%d,name=%s,username=%s,role=%s", user.getInteger("id"), name, username, role)
            );

            return user;
        });
    }

    public boolean updateUser(Integer id, String name, String username, String role, String newPassword) {
        return DatabaseManager.callInConnection(() -> {
            // Seguridad: No permitir editar usuarios root
            if (repo.isRootUser(id)) {
                throw new IllegalArgumentException("No se permite editar usuarios con rol root por seguridad");
            }

            // Seguridad: No permitir cambiar a rol root
            if ("root".equals(role)) {
                throw new IllegalArgumentException("No se permite asignar el rol root por seguridad");
            }

            boolean ok = repo.update(id, name, username, role, newPassword);
            if (ok) {
                auditService.logAction(
                    Session.getCurrentUserId(),
                    "update_user",
                    "users",
                    id,
                    String.format("user_id=%d,username=%s,role=%s", id, username, role)
                );
            }
            return ok;
        });
    }

    public boolean resetPassword(Integer id, String newPassword, boolean mustChange) {
        return DatabaseManager.callInConnection(() -> {
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

    public boolean deleteUser(Integer id) {
        return DatabaseManager.callInConnection(() -> {
            // Seguridad: No permitir eliminar usuarios root
            if (repo.isRootUser(id)) {
                throw new IllegalArgumentException("No se permite eliminar usuarios root por seguridad");
            }

            boolean ok = repo.delete(id);

            if (ok) {
                auditService.logAction(
                        Session.getCurrentUserId(),
                        "delete_user",
                        "users",
                        id,
                        "user_id=" + id
                );
            }
            return ok;
        });
    }

    public User findById(Integer id) {
        return DatabaseManager.callInConnection(() -> repo.findById(id));
    }

    public long countUsers() {
        return DatabaseManager.callInConnection(repo::countUsers);
    }

    public List<User> listPaged(int offset, int limit, String search) {
        return DatabaseManager.callInConnection(() -> repo.listPaged(offset, limit, search));
    }

    // ===== MÉTODOS DE SOFT DELETE Y RESTAURACIÓN =====

    /**
     * Soft delete explícito de un usuario
     */
    public void softDelete(User user) {
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

    /**
     * Lista usuarios incluyendo eliminados (requiere VIEW_DELETED)
     */
    public List<User> findAllIncludingDeleted() {
        if (!com.joshuamc1.sicam.core.PermissionHelper.canAccess("SISTEMA", "VIEW_DELETED")) {
            throw new SecurityException("No tiene permiso para ver usuarios eliminados");
        }
        return DatabaseManager.callInConnection(repo::findAllIncludingDeleted);
    }

    /**
     * Lista solo eliminados
     */
    public List<User> findDeleted() {
        if (!com.joshuamc1.sicam.core.PermissionHelper.canAccess("SISTEMA", "VIEW_DELETED")) {
            throw new SecurityException("No tiene permiso para ver usuarios eliminados");
        }
        return DatabaseManager.callInConnection(repo::findDeleted);
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
        return DatabaseManager.callInConnection(() -> {
            User user = repo.findByUsername(username);
            return user != null;
        });
    }

    /**
     * Obtiene el ID de un usuario por su username
     */
    public Integer getIdByUsername(String username) {
        return DatabaseManager.callInConnection(() -> {
            User user = repo.findByUsername(username);
            return user != null ? user.getInteger("id") : null;
        });
    }
}