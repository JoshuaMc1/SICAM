package com.joshuamc1.sicam.repositories;

import org.javalite.activejdbc.Base;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.Supplier;

/** Reserva por cuenta. El reloj y la exclusión mutua pertenecen a la base de datos. */
public final class SessionRepository {
    public static final int LEASE_SECONDS = 60;
    private final Supplier<Connection> connection;

    public SessionRepository() { connection = Base::connection; }
    public SessionRepository(Connection connection) { this.connection = () -> connection; }

    private boolean sqlite() throws SQLException {
        return "SQLite".equalsIgnoreCase(connection.get().getMetaData().getDatabaseProductName());
    }

    private String now() throws SQLException {
        return sqlite() ? "strftime('%Y-%m-%d %H:%M:%f','now')" : "UTC_TIMESTAMP(6)";
    }

    private String expires() throws SQLException {
        return sqlite() ? "strftime('%Y-%m-%d %H:%M:%f','now','+" + LEASE_SECONDS + " seconds')"
            : "DATE_ADD(UTC_TIMESTAMP(6), INTERVAL " + LEASE_SECONDS + " SECOND)";
    }

    private static final String ACTIVE = "status='active' AND EXISTS (SELECT 1 FROM roles r "
        + "WHERE r.id=users.rol_id AND r.status='active' AND r.activo=1)";

    public boolean acquire(int userId, String token) {
        try {
            // Un único UPDATE condicional: dos procesos nunca pueden reservar la misma cuenta.
            return update("UPDATE users SET session_token=?, session_expires_at=" + expires()
                + " WHERE id=? AND " + ACTIVE
                + " AND (session_token IS NULL OR session_expires_at IS NULL OR session_expires_at<=" + now() + ")",
                token, userId) == 1;
        } catch (SQLException e) { throw failure(e); }
    }

    public boolean renew(int userId, String token) {
        try {
            return update("UPDATE users SET session_expires_at=" + expires()
                + " WHERE session_token=? AND id=? AND " + ACTIVE + " AND session_expires_at>" + now(),
                token, userId) == 1;
        } catch (SQLException e) { throw failure(e); }
    }

    public boolean isValid(int userId, String token) {
        if (token == null) return false;
        try (PreparedStatement ps = connection.get().prepareStatement(
            "SELECT 1 FROM users WHERE session_token=? AND id=? AND " + ACTIVE + " AND session_expires_at>" + now())) {
            ps.setString(1, token);
            ps.setInt(2, userId);
            ps.setQueryTimeout(5);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) { throw failure(e); }
    }

    public void release(int userId, String token) {
        try {
            // Un cierre tardío jamás libera la reserva de otra instancia.
            update("UPDATE users SET session_token=NULL, session_expires_at=NULL WHERE session_token=? AND id=?",
                token, userId);
        } catch (SQLException e) { throw failure(e); }
    }

    private int update(String sql, String token, int userId) throws SQLException {
        try (PreparedStatement ps = connection.get().prepareStatement(sql)) {
            ps.setString(1, token);
            ps.setInt(2, userId);
            ps.setQueryTimeout(5);
            return ps.executeUpdate();
        }
    }

    private IllegalStateException failure(SQLException cause) {
        return new IllegalStateException("No se pudo verificar la reserva de sesión. "
            + "Compruebe la conexión y que el esquema incluya los campos de sesión.", cause);
    }
}
