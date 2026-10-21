package com.joshuamc1.sicam.repositories;

import com.joshuamc1.sicam.core.PermissionPolicy;
import org.javalite.activejdbc.Base;
import java.sql.*;
import java.util.function.Supplier;

/** Consulta la autorización vigente, sin depender del rol guardado en la sesión. */
public class AuthorizationRepository {
    private final Supplier<Connection> connection;
    public AuthorizationRepository() { connection = Base::connection; }
    public AuthorizationRepository(Connection connection) { this.connection = () -> connection; }

    public String activeRoleName(Integer userId) {
        if (userId == null) return null;
        try (PreparedStatement ps = connection.get().prepareStatement(
            "SELECT r.nombre FROM users u JOIN roles r ON r.id=u.rol_id "
            + "WHERE u.id=? AND u.status='active' AND r.status='active' AND r.activo=1")) {
            ps.setInt(1,userId);
            try(ResultSet rs=ps.executeQuery()) { return rs.next() ? rs.getString(1) : null; }
        } catch(SQLException e) { throw new IllegalStateException("No se pudo verificar la sesión",e); }
    }
    public boolean hasPermission(Integer userId, String module, String action) {
        if (!PermissionPolicy.isSupported(module,action)) return false;
        String role=activeRoleName(userId);
        if (role==null) return false;
        if ("ROOT".equalsIgnoreCase(role)) return true;
        if (PermissionPolicy.isRootOnly(module,action)) return false;
        try (PreparedStatement ps=connection.get().prepareStatement(
            "SELECT COUNT(*) FROM users u JOIN rol_permisos rp ON rp.rol_id=u.rol_id "
            + "JOIN permisos p ON p.id=rp.permiso_id JOIN roles r ON r.id=u.rol_id "
            + "WHERE u.id=? AND u.status='active' AND r.status='active' AND r.activo=1 AND p.modulo=? AND p.accion=?")) {
            ps.setInt(1,userId); ps.setString(2,module); ps.setString(3,action);
            try(ResultSet rs=ps.executeQuery()) { return rs.next() && rs.getLong(1)>0; }
        } catch(SQLException e) { throw new IllegalStateException("No se pudo verificar el permiso",e); }
    }
}
