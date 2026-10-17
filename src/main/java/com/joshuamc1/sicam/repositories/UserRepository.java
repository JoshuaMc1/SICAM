package com.joshuamc1.sicam.repositories;

import com.joshuamc1.sicam.models.User;

import java.util.List;

public class UserRepository {

    // ===== MÉTODOS DE BÚSQUEDA CON FILTRADO DE ESTADO =====

    public User findByUsername(String username) {
        // Solo usuarios activos pueden hacer login
        return User.findFirst("username = ? AND status = ?", username, User.STATUS_ACTIVE);
    }

    /**
     * Busca usuario por username incluyendo inactivos/eliminados (para auditoría)
     */
    public User findByUsernameIncludingDeleted(String username) {
        return User.findFirst("username = ?", username);
    }

    public User findById(Integer id) {
        return User.findById(id);
    }

    /**
     * Lista todos los usuarios activos (excluye eliminados)
     */
    public List<User> findAll() {
        return User.where("status != ?", User.STATUS_DELETED)
            .orderBy("created_at DESC");
    }

    /**
     * Lista todos los usuarios incluyendo eliminados
     */
    public List<User> findAllIncludingDeleted() {
        return User.findAll().orderBy("created_at DESC");
    }

    /**
     * Lista solo usuarios eliminados
     */
    public List<User> findDeleted() {
        return User.where("status = ?", User.STATUS_DELETED)
            .orderBy("deleted_at DESC");
    }

    /**
     * Lista solo usuarios activos
     */
    public List<User> findActive() {
        return User.where("status = ?", User.STATUS_ACTIVE)
            .orderBy("created_at DESC");
    }

    /**
     * Lista solo usuarios inactivos
     */
    public List<User> findInactive() {
        return User.where("status = ?", User.STATUS_INACTIVE)
            .orderBy("created_at DESC");
    }

    public User create(String username, String password, String role) {
        User user = new User();
        user.set("username", username);
        user.setPassword(password);
        user.set("role", role);
        user.set("status", User.STATUS_ACTIVE); // Nuevo campo
        // Forzar cambio de contraseña al primer acceso
        user.set("must_change_password", 1);
        user.saveIt();

        return user;
    }

    public User createFull(String name, String username, String password, String role) {
        User user = new User();
        user.set("name", name);
        user.set("username", username);
        user.setPassword(password);
        user.set("role", role);
        user.set("status", User.STATUS_ACTIVE); // Nuevo campo
        // Forzar cambio de contraseña al primer acceso
        user.set("must_change_password", 1);
        user.saveIt();
        return user;
    }

    public boolean update(Integer id, String name, String username, String role, String newPassword) {
        User u = User.findById(id);
        if (u == null) return false;
        u.set("name", name);
        u.set("username", username);
        u.set("role", role);
        if (newPassword != null && !newPassword.isBlank()) {
            u.setPassword(newPassword);
            // Si se cambia la contraseña desde el propio usuario, quitar el flag
            u.set("must_change_password", 0);
        }
        return u.saveIt();
    }

    public boolean resetPassword(Integer id, String newPassword, boolean mustChange) {
        User u = User.findById(id);
        if (u == null) return false;
        u.setPassword(newPassword);
        u.set("must_change_password", mustChange ? 1 : 0);
        return u.saveIt();
    }

    /**
     * Cuenta usuarios activos (excluye eliminados)
     */
    public long countUsers() {
        Long c = User.count("status != ?", User.STATUS_DELETED);
        return c == null ? 0 : c;
    }

    /**
     * Cuenta administradores activos
     */
    public long countAdmins() {
        // Contar usuarios con rol ADMIN (id=2) y activos
        Long c = User.count("rol_id = ? AND status = ?", 2, User.STATUS_ACTIVE);
        return c == null ? 0 : c;
    }

    /**
     * Lista paginada de usuarios (excluye ROOT y eliminados)
     */
    public List<User> listPaged(int offset, int limit, String search) {
        // Excluir usuarios con rol ROOT (id=1) y eliminados
        if (search != null && !search.isBlank()) {
            return User.where(
                "(name LIKE ? OR username LIKE ?) AND rol_id != ? AND status != ?",
                "%"+search+"%", "%"+search+"%", 1, User.STATUS_DELETED)
                .orderBy("created_at DESC")
                .offset(offset)
                .limit(limit);
        } else {
            return User.where("rol_id != ? AND status != ?", 1, User.STATUS_DELETED)
                .orderBy("created_at DESC")
                .offset(offset)
                .limit(limit);
        }
    }

    /**
     * Verifica si un usuario es root
     */
    public boolean isRootUser(Integer id) {
        User u = User.findById(id);
        return u != null && u.isRootRole();
    }

    /**
     * Cuenta cuántos usuarios root existen (activos)
     */
    public long countRootUsers() {
        // Contar usuarios con rol ROOT (id=1) y activos
        Long c = User.count("rol_id = ? AND status = ?", 1, User.STATUS_ACTIVE);
        return c == null ? 0 : c;
    }

    public boolean updateOwnProfile(Integer id, String name, String newPassword) {
        User u = User.findById(id);
        if (u == null) return false;
        if (name != null && !name.isBlank()) {
            u.set("name", name);
        }
        if (newPassword != null && !newPassword.isBlank()) {
            u.setPassword(newPassword);
            u.set("must_change_password", 0);
        }
        return u.saveIt();
    }

    // ===== MÉTODOS DE SOFT DELETE Y RESTAURACIÓN =====

    /**
     * Realiza soft delete del usuario
     */
    public void softDelete(User user, Integer deletedBy) {
        user.softDelete(deletedBy);
    }

    /**
     * Eliminación física permanente (solo ROOT)
     */
    public void hardDelete(User user) {
        user.delete();
    }

    /**
     * Restaura un usuario eliminado
     */
    public void restore(User user) {
        user.restore();
    }

    /**
     * Cuenta usuarios eliminados
     */
    public long countDeleted() {
        Long c = User.count("status = ?", User.STATUS_DELETED);
        return c == null ? 0L : c;
    }

    // ===== MÉTODOS LEGACY (mantener compatibilidad) =====

    /**
     * @deprecated Usar softDelete() o hardDelete() según el caso
     */
    @Deprecated
    public boolean delete(Integer id) {
        User u = User.findById(id);
        if (u == null) return false;
        return u.delete();
    }
}
