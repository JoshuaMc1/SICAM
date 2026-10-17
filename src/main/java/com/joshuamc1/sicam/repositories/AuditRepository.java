package com.joshuamc1.sicam.repositories;

import com.joshuamc1.sicam.models.Audit;
import com.joshuamc1.sicam.models.dto.AuditDTO;
import org.jetbrains.annotations.NotNull;
import org.javalite.activejdbc.Base;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class AuditRepository {
    public void save(@NotNull Audit audit) {
        audit.saveIt();
    }

    public List<Audit> findAll() {
        return Audit.findAll();
    }

    public List<AuditDTO> findAllWithUser() {
        return findPageWithUser(0, 1000, null);
    }

    public List<AuditDTO> findPageWithUser(int offset, int limit, String search) {
        String base = "FROM audit a LEFT JOIN users u ON u.id = a.user_id";
        String where = "";
        if (search != null && !search.trim().isEmpty()) {
            where = " WHERE (lower(u.name) LIKE ? OR lower(a.action) LIKE ? OR lower(a.table_name) LIKE ? OR lower(a.details) LIKE ?)";
        }
        String sql = "SELECT a.id, a.user_id AS usuario_id, u.name as usuario_name, a.action AS accion, a.table_name AS tabla_afectada, a.record_id AS registro_afectado_id, a.details AS detalles, a.created_at AS fecha " +
                base + where + " ORDER BY a.created_at DESC LIMIT ? OFFSET ?";

        List<AuditDTO> result = new ArrayList<>();
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = Base.connection().prepareStatement(sql);
            int idx = 1;
            if (where.length() > 0) {
                String pattern = "%" + search.toLowerCase() + "%";
                ps.setString(idx++, pattern);
                ps.setString(idx++, pattern);
                ps.setString(idx++, pattern);
                ps.setString(idx++, pattern);
            }
            ps.setInt(idx++, limit);
            ps.setInt(idx, offset);

            rs = ps.executeQuery();
            while (rs.next()) {
                Integer id = rs.getInt("id");
                Integer usuarioId = rs.getObject("usuario_id") == null ? null : rs.getInt("usuario_id");
                String usuarioName = rs.getString("usuario_name");
                String accion = rs.getString("accion");
                String tabla = rs.getString("tabla_afectada");
                Integer registroId = rs.getObject("registro_afectado_id") == null ? null : rs.getInt("registro_afectado_id");
                String detalles = rs.getString("detalles");
                String fecha = rs.getString("fecha");
                result.add(new AuditDTO(id, usuarioId, usuarioName, accion, tabla, registroId, detalles, fecha));
            }
        } catch (Exception e) {
            throw new RuntimeException("Error executing audit query", e);
        } finally {
            try { if (rs != null) rs.close(); } catch (Exception ignored) {}
            try { if (ps != null) ps.close(); } catch (Exception ignored) {}
        }
        return result;
    }

    public int countWithFilter(String search) {
        String base = "FROM audit a LEFT JOIN users u ON u.id = a.user_id";
        String where = "";
        if (search != null && !search.trim().isEmpty()) {
            where = " WHERE (lower(u.name) LIKE ? OR lower(a.action) LIKE ? OR lower(a.table_name) LIKE ? OR lower(a.details) LIKE ?)";
        }
        String sql = "SELECT COUNT(1) as total " + base + where;

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = Base.connection().prepareStatement(sql);
            int idx = 1;
            if (where.length() > 0) {
                String pattern = "%" + search.toLowerCase() + "%";
                ps.setString(idx++, pattern);
                ps.setString(idx++, pattern);
                ps.setString(idx++, pattern);
                ps.setString(idx++, pattern);
            }
            rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt("total");
            }
            return 0;
        } catch (Exception e) {
            throw new RuntimeException("Error counting audit records", e);
        } finally {
            try { if (rs != null) rs.close(); } catch (Exception ignored) {}
            try { if (ps != null) ps.close(); } catch (Exception ignored) {}
        }
    }

    public List<AuditDTO> findAllWithUserByDateRange(LocalDate from, LocalDate to) {
        String sql = "SELECT a.id, a.user_id AS usuario_id, u.name as usuario_name, " +
                "a.action AS accion, a.table_name AS tabla_afectada, a.record_id AS registro_afectado_id, " +
                "a.details AS detalles, a.created_at AS fecha " +
                "FROM audit a LEFT JOIN users u ON u.id = a.user_id " +
                "WHERE DATE(a.created_at) BETWEEN ? AND ? " +
                "ORDER BY a.created_at DESC";

        List<AuditDTO> result = new ArrayList<>();
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = Base.connection().prepareStatement(sql);
            ps.setString(1, from.toString());
            ps.setString(2, to.toString());

            rs = ps.executeQuery();
            while (rs.next()) {
                Integer id = rs.getInt("id");
                Integer usuarioId = rs.getObject("usuario_id") == null ? null : rs.getInt("usuario_id");
                String usuarioName = rs.getString("usuario_name");
                String accion = rs.getString("accion");
                String tabla = rs.getString("tabla_afectada");
                Integer registroId = rs.getObject("registro_afectado_id") == null ? null : rs.getInt("registro_afectado_id");
                String detalles = rs.getString("detalles");
                String fecha = rs.getString("fecha");
                result.add(new AuditDTO(id, usuarioId, usuarioName, accion, tabla, registroId, detalles, fecha));
            }
        } catch (Exception e) {
            throw new RuntimeException("Error executing audit query by date range", e);
        } finally {
            try { if (rs != null) rs.close(); } catch (Exception ignored) {}
            try { if (ps != null) ps.close(); } catch (Exception ignored) {}
        }
        return result;
    }
}
