package com.joshuamc1.sicam.repositories;

import com.joshuamc1.sicam.core.Logs;
import com.joshuamc1.sicam.models.Report;
import org.javalite.activejdbc.Base;
import org.jetbrains.annotations.NotNull;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class ReportRepository {
    // Reporte de usuarios con roles
    public Report usersWithRoles() {
        // FASE 9: Excluir usuarios eliminados
        String sql = "SELECT u.id, u.name, u.username, r.nombre as role, u.created_at " +
                     "FROM users u " +
                     "LEFT JOIN roles r ON u.rol_id = r.id " +
                     "WHERE u.status = 'active' " +
                     "ORDER BY u.name";
        Report res = new Report("Usuarios y roles");
        res.addHeader("ID"); res.addHeader("Nombre"); res.addHeader("Username"); res.addHeader("Rol"); res.addHeader("Creado");

        try (PreparedStatement ps = Base.connection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                List<String> row = new ArrayList<>();
                row.add(String.valueOf(rs.getInt("id")));
                row.add(rs.getString("name"));
                row.add(rs.getString("username"));
                row.add(rs.getString("role"));
                row.add(rs.getString("created_at"));
                res.addRow(row);
            }
        } catch (Exception e) {
            Logs.error(this.getClass(), "Error ejecutando usersWithRoles: {}", e.getMessage());
            throw new RuntimeException(e);
        }
        return res;
    }


    // Reporte de inventario total
    public Report inventoryTotal() {
        // FASE 9: Excluir productos eliminados
        String sql = "SELECT id, name, unit, current_stock, minimum_stock, created_at " +
                     "FROM products " +
                     "WHERE status = 'active' " +
                     "ORDER BY name";
        Report res = new Report("Inventario total");
        res.addHeader("ID"); res.addHeader("Nombre"); res.addHeader("Unidad"); res.addHeader("Stock actual"); res.addHeader("Stock mínimo"); res.addHeader("Creado");

        try (PreparedStatement ps = Base.connection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                List<String> row = new ArrayList<>();
                row.add(String.valueOf(rs.getInt("id")));
                row.add(rs.getString("name"));
                row.add(rs.getString("unit"));
                row.add(String.valueOf(rs.getObject("current_stock")));
                row.add(String.valueOf(rs.getObject("minimum_stock")));
                row.add(rs.getString("created_at"));
                res.addRow(row);
            }
        } catch (Exception e) {
            Logs.error(this.getClass(), "Error ejecutando inventoryTotal: {}", e.getMessage());
            throw new RuntimeException(e);
        }
        return res;
    }

    // Reporte de inventario con stock bajo
    public Report inventoryLowStock() {
        // FASE 9: Excluir productos eliminados
        String sql = "SELECT id, name, unit, current_stock, minimum_stock " +
                     "FROM products " +
                     "WHERE current_stock <= minimum_stock AND status = 'active' " +
                     "ORDER BY current_stock ASC";
        Report res = new Report("Inventario - Stock bajo");
        res.addHeader("ID"); res.addHeader("Nombre"); res.addHeader("Unidad"); res.addHeader("Stock actual"); res.addHeader("Stock mínimo");

        try (PreparedStatement ps = Base.connection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                List<String> row = new ArrayList<>();
                row.add(String.valueOf(rs.getInt("id")));
                row.add(rs.getString("name"));
                row.add(rs.getString("unit"));
                row.add(String.valueOf(rs.getObject("current_stock")));
                row.add(String.valueOf(rs.getObject("minimum_stock")));
                res.addRow(row);
            }
        } catch (Exception e) {
            Logs.error(this.getClass(), "Error ejecutando inventoryLowStock: {}", e.getMessage());
            throw new RuntimeException(e);
        }
        return res;
    }

    // Reporte de inventario por rango de fechas (created_at)
    public Report inventoryByDateRange(String from, String to) {
        String sql = "SELECT id, name, unit, current_stock, minimum_stock, created_at FROM products WHERE date(created_at) BETWEEN date(?) AND date(?) ORDER BY created_at";
        Report res = new Report("Inventario por rango de fechas");
        res.addHeader("ID"); res.addHeader("Nombre"); res.addHeader("Unidad"); res.addHeader("Stock actual"); res.addHeader("Stock mínimo"); res.addHeader("Creado");

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = Base.connection().prepareStatement(sql);
            ps.setString(1, from);
            ps.setString(2, to);
            rs = ps.executeQuery();
            while (rs.next()) {
                List<String> row = new ArrayList<>();
                row.add(String.valueOf(rs.getInt("id")));
                row.add(rs.getString("name"));
                row.add(rs.getString("unit"));
                row.add(String.valueOf(rs.getObject("current_stock")));
                row.add(String.valueOf(rs.getObject("minimum_stock")));
                row.add(rs.getString("created_at"));
                res.addRow(row);
            }
        } catch (Exception e) {
            Logs.error(this.getClass(), "Error ejecutando inventoryByDateRange: {}", e.getMessage());
            throw new RuntimeException(e);
        } finally {
            try { if (rs != null) rs.close(); } catch (Exception ignored) {}
            try { if (ps != null) ps.close(); } catch (Exception ignored) {}
        }
        return res;
    }

    // Reporte transacciones
    public Report transactions(String from, String to) {
        String sql = "SELECT t.id, " +
                "       GROUP_CONCAT(DISTINCT p.name SEPARATOR ', ') as productos, " +
                "       t.type, " +
                "       (SELECT SUM(ti.quantity) FROM transaction_items ti WHERE ti.transaction_id = t.id) as cantidad_total, " +
                "       t.total_items, " +
                "       t.date, u.name as usuario, t.notes, t.created_at " +
                "FROM transactions t " +
                "LEFT JOIN transaction_items ti ON ti.transaction_id = t.id " +
                "LEFT JOIN products p ON p.id = ti.product_id " +
                "LEFT JOIN users u ON u.id = t.user_id " +
                "WHERE date(t.created_at) BETWEEN date(?) AND date(?) " +
                "GROUP BY t.id, t.type, t.date, u.name, t.notes, t.created_at " +
                "ORDER BY t.created_at DESC";
        Report res = new Report("Transacciones");
        res.addHeader("ID");
        res.addHeader("Productos");
        res.addHeader("Tipo");
        res.addHeader("Cantidad Total");
        res.addHeader("Items");
        res.addHeader("Fecha");
        res.addHeader("Usuario");
        res.addHeader("Notas");
        res.addHeader("Creado");

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = Base.connection().prepareStatement(sql);
            ps.setString(1, from);
            ps.setString(2, to);
            rs = ps.executeQuery();
            while (rs.next()) {
                List<String> row = new ArrayList<>();
                row.add(String.valueOf(rs.getInt("id")));

                String productos = rs.getString("productos");
                int totalItems = rs.getInt("total_items");
                // Siempre mostrar nombres de productos
                row.add(productos != null ? productos : "N/A");

                row.add(rs.getString("type"));
                row.add(String.format("%.2f", rs.getDouble("cantidad_total")));
                row.add(String.valueOf(totalItems));
                row.add(rs.getString("date"));
                row.add(rs.getString("usuario"));
                row.add(rs.getString("notes"));
                row.add(rs.getString("created_at"));
                res.addRow(row);
            }
        } catch (Exception e) {
            Logs.error(this.getClass(), "Error ejecutando transactions: {}", e.getMessage());
            throw new RuntimeException(e);
        } finally {
            try { if (rs != null) rs.close(); } catch (Exception ignored) {}
            try { if (ps != null) ps.close(); } catch (Exception ignored) {}
        }
        return res;
    }

    // Reporte transacciones por tipo
    public Report transactionsByType(String type, String from, String to) {
        String sql = "SELECT t.id, " +
                "       GROUP_CONCAT(DISTINCT p.name SEPARATOR ', ') as productos, " +
                "       t.type, " +
                "       (SELECT SUM(ti.quantity) FROM transaction_items ti WHERE ti.transaction_id = t.id) as cantidad_total, " +
                "       t.total_items, " +
                "       t.date, u.name as usuario, t.notes, t.created_at " +
                "FROM transactions t " +
                "LEFT JOIN transaction_items ti ON ti.transaction_id = t.id " +
                "LEFT JOIN products p ON p.id = ti.product_id " +
                "LEFT JOIN users u ON u.id = t.user_id " +
                "WHERE t.type = ? AND date(t.created_at) BETWEEN date(?) AND date(?) " +
                "GROUP BY t.id, t.type, t.date, u.name, t.notes, t.created_at " +
                "ORDER BY t.created_at DESC";
        Report res = new Report("Transacciones por tipo: " + type);
        res.addHeader("ID");
        res.addHeader("Productos");
        res.addHeader("Tipo");
        res.addHeader("Cantidad Total");
        res.addHeader("Items");
        res.addHeader("Fecha");
        res.addHeader("Usuario");
        res.addHeader("Notas");
        res.addHeader("Creado");

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = Base.connection().prepareStatement(sql);
            ps.setString(1, type);
            ps.setString(2, from);
            ps.setString(3, to);
            rs = ps.executeQuery();
            while (rs.next()) {
                List<String> row = new ArrayList<>();
                row.add(String.valueOf(rs.getInt("id")));

                String productos = rs.getString("productos");
                int totalItems = rs.getInt("total_items");
                // Siempre mostrar nombres de productos
                row.add(productos != null ? productos : "N/A");

                row.add(rs.getString("type"));
                row.add(String.format("%.2f", rs.getDouble("cantidad_total")));
                row.add(String.valueOf(totalItems));
                row.add(rs.getString("date"));
                row.add(rs.getString("usuario"));
                row.add(rs.getString("notes"));
                row.add(rs.getString("created_at"));
                res.addRow(row);
            }
        } catch (Exception e) {
            Logs.error(this.getClass(), "Error ejecutando transactionsByType: {}", e.getMessage());
            throw new RuntimeException(e);
        } finally {
            try { if (rs != null) rs.close(); } catch (Exception ignored) {}
            try { if (ps != null) ps.close(); } catch (Exception ignored) {}
        }
        return res;
    }

    // Reporte transacciones por persona
    public Report transactionsByPerson(int personId, String from, String to) {
        String sql = "SELECT t.id, " +
                "       GROUP_CONCAT(DISTINCT p.name SEPARATOR ', ') as productos, " +
                "       t.type, " +
                "       (SELECT SUM(ti.quantity) FROM transaction_items ti WHERE ti.transaction_id = t.id) as cantidad_total, " +
                "       t.total_items, " +
                "       t.date, u.name as usuario, t.person_id, t.notes, t.created_at " +
                "FROM transactions t " +
                "LEFT JOIN transaction_items ti ON ti.transaction_id = t.id " +
                "LEFT JOIN products p ON p.id = ti.product_id " +
                "LEFT JOIN users u ON u.id = t.user_id " +
                "WHERE t.person_id = ? AND date(t.created_at) BETWEEN date(?) AND date(?) " +
                "GROUP BY t.id, t.type, t.date, u.name, t.person_id, t.notes, t.created_at " +
                "ORDER BY t.created_at DESC";
        Report res = new Report("Transacciones por persona");
        res.addHeader("ID");
        res.addHeader("Productos");
        res.addHeader("Tipo");
        res.addHeader("Cantidad Total");
        res.addHeader("Items");
        res.addHeader("Fecha");
        res.addHeader("Usuario");
        res.addHeader("Person ID");
        res.addHeader("Notas");
        res.addHeader("Creado");

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = Base.connection().prepareStatement(sql);
            ps.setInt(1, personId);
            ps.setString(2, from);
            ps.setString(3, to);
            rs = ps.executeQuery();
            while (rs.next()) {
                List<String> row = new ArrayList<>();
                row.add(String.valueOf(rs.getInt("id")));

                String productos = rs.getString("productos");
                int totalItems = rs.getInt("total_items");
                // Siempre mostrar nombres de productos
                row.add(productos != null ? productos : "N/A");

                row.add(rs.getString("type"));
                row.add(String.format("%.2f", rs.getDouble("cantidad_total")));
                row.add(String.valueOf(totalItems));
                row.add(rs.getString("date"));
                row.add(rs.getString("usuario"));
                row.add(String.valueOf(rs.getObject("person_id")));
                row.add(rs.getString("notes"));
                row.add(rs.getString("created_at"));
                res.addRow(row);
            }
        } catch (Exception e) {
            Logs.error(this.getClass(), "Error ejecutando transactionsByPerson: {}", e.getMessage());
            throw new RuntimeException(e);
        } finally {
            try { if (rs != null) rs.close(); } catch (Exception ignored) {}
            try { if (ps != null) ps.close(); } catch (Exception ignored) {}
        }
        return res;
    }

    // Reporte de personas registradas
    public Report peopleRegistered(String from, String to) {
        String sql = "SELECT id, dni, first_name, last_name, phone, internal_code, created_at FROM people WHERE date(created_at) BETWEEN date(?) AND date(?) ORDER BY created_at DESC";
        Report res = new Report("Personas registradas");
        res.addHeader("ID"); res.addHeader("DNI"); res.addHeader("Nombre"); res.addHeader("Apellido"); res.addHeader("Teléfono"); res.addHeader("Código interno"); res.addHeader("Creado");

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = Base.connection().prepareStatement(sql);
            ps.setString(1, from);
            ps.setString(2, to);
            rs = ps.executeQuery();
            while (rs.next()) {
                List<String> row = new ArrayList<>();
                row.add(String.valueOf(rs.getInt("id")));
                row.add(rs.getString("dni"));
                row.add(rs.getString("first_name"));
                row.add(rs.getString("last_name"));
                row.add(rs.getString("phone"));
                row.add(rs.getString("internal_code"));
                row.add(rs.getString("created_at"));
                res.addRow(row);
            }
        } catch (Exception e) {
            Logs.error(this.getClass(), "Error ejecutando peopleRegistered: {}", e.getMessage());
            throw new RuntimeException(e);
        } finally {
            try { if (rs != null) rs.close(); } catch (Exception ignored) {}
            try { if (ps != null) ps.close(); } catch (Exception ignored) {}
        }
        return res;
    }

    // --- NUEVOS: reportes de usuarios ---
    // Reporte de usuarios excluyendo root
    public Report usersExcludingRoot() {
        String sql = "SELECT u.id, u.name, u.username, r.nombre as role, u.created_at " +
                     "FROM users u " +
                     "LEFT JOIN roles r ON u.rol_id = r.id " +
                     "WHERE u.rol_id != 1 " +
                     "ORDER BY u.name";
        Report res = new Report("Usuarios del Sistema (sin Root)");
        res.addHeader("ID"); res.addHeader("Nombre"); res.addHeader("Username"); res.addHeader("Rol"); res.addHeader("Creado");

        try (PreparedStatement ps = Base.connection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                List<String> row = new ArrayList<>();
                row.add(String.valueOf(rs.getInt("id")));
                row.add(rs.getString("name"));
                row.add(rs.getString("username"));
                row.add(rs.getString("role"));
                row.add(rs.getString("created_at"));
                res.addRow(row);
            }
        } catch (Exception e) {
            Logs.error(this.getClass(), "Error ejecutando usersExcludingRoot: {}", e.getMessage());
            throw new RuntimeException(e);
        }
        return res;
    }

    // Reporte de usuarios agrupados por rol
    public Report usersGroupedByRole() {
        String sql = "SELECT u.id, u.name, u.username, r.nombre as role, u.created_at " +
                     "FROM users u " +
                     "LEFT JOIN roles r ON u.rol_id = r.id " +
                     "WHERE u.rol_id != 1 " +
                     "ORDER BY r.nombre, u.name";
        Report res = new Report("Usuarios Agrupados por Rol");
        res.addHeader("ID"); res.addHeader("Nombre"); res.addHeader("Username"); res.addHeader("Rol"); res.addHeader("Creado");

        try (PreparedStatement ps = Base.connection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                List<String> row = new ArrayList<>();
                row.add(String.valueOf(rs.getInt("id")));
                row.add(rs.getString("name"));
                row.add(rs.getString("username"));
                row.add(rs.getString("role"));
                row.add(rs.getString("created_at"));
                res.addRow(row);
            }
        } catch (Exception e) {
            Logs.error(this.getClass(), "Error ejecutando usersGroupedByRole: {}", e.getMessage());
            throw new RuntimeException(e);
        }
        return res;
    }

    // --- NUEVOS: reportes de inventario ---
    // Reporte de inventario completo
    public Report inventoryComplete() {
        String sql = "SELECT id, name, description, unit, current_stock, minimum_stock, created_at " +
                     "FROM products ORDER BY name";
        Report res = new Report("Inventario Completo");
        res.addHeader("ID");
        res.addHeader("Nombre");
        res.addHeader("Descripción");
        res.addHeader("Unidad");
        res.addHeader("Stock Actual");
        res.addHeader("Stock Mínimo");
        res.addHeader("Fecha Registro");

        try (PreparedStatement ps = Base.connection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                List<String> row = new ArrayList<>();
                row.add(String.valueOf(rs.getInt("id")));
                row.add(rs.getString("name"));
                row.add(rs.getString("description"));
                row.add(rs.getString("unit"));
                row.add(String.valueOf(rs.getObject("current_stock")));
                row.add(String.valueOf(rs.getObject("minimum_stock")));
                row.add(rs.getString("created_at"));
                res.addRow(row);
            }
        } catch (Exception e) {
            Logs.error(this.getClass(), "Error ejecutando inventoryComplete: {}", e.getMessage());
            throw new RuntimeException(e);
        }
        return res;
    }

    // Reporte de productos con stock bajo
    public Report inventoryLowStockReport() {
        String sql = "SELECT id, name, description, unit, current_stock, minimum_stock " +
                     "FROM products WHERE current_stock <= minimum_stock ORDER BY current_stock ASC, name";
        Report res = new Report("Productos con Stock Bajo");
        res.addHeader("ID");
        res.addHeader("Nombre");
        res.addHeader("Descripción");
        res.addHeader("Unidad");
        res.addHeader("Stock Actual");
        res.addHeader("Stock Mínimo");

        try (PreparedStatement ps = Base.connection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                List<String> row = new ArrayList<>();
                row.add(String.valueOf(rs.getInt("id")));
                row.add(rs.getString("name"));
                row.add(rs.getString("description"));
                row.add(rs.getString("unit"));
                row.add(String.valueOf(rs.getObject("current_stock")));
                row.add(String.valueOf(rs.getObject("minimum_stock")));
                res.addRow(row);
            }
        } catch (Exception e) {
            Logs.error(this.getClass(), "Error ejecutando inventoryLowStockReport: {}", e.getMessage());
            throw new RuntimeException(e);
        }
        return res;
    }

    // Reporte de productos sin stock
    public Report inventoryOutOfStock() {
        String sql = "SELECT id, name, description, unit, minimum_stock, created_at " +
                     "FROM products WHERE current_stock = 0 ORDER BY name";
        Report res = new Report("Productos Sin Stock");
        res.addHeader("ID");
        res.addHeader("Nombre");
        res.addHeader("Descripción");
        res.addHeader("Unidad");
        res.addHeader("Stock Mínimo");
        res.addHeader("Fecha Registro");

        try (PreparedStatement ps = Base.connection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                List<String> row = new ArrayList<>();
                row.add(String.valueOf(rs.getInt("id")));
                row.add(rs.getString("name"));
                row.add(rs.getString("description"));
                row.add(rs.getString("unit"));
                row.add(String.valueOf(rs.getObject("minimum_stock")));
                row.add(rs.getString("created_at"));
                res.addRow(row);
            }
        } catch (Exception e) {
            Logs.error(this.getClass(), "Error ejecutando inventoryOutOfStock: {}", e.getMessage());
            throw new RuntimeException(e);
        }
        return res;
    }

    // Reporte de movimientos de inventario (últimos 30 días)
    public Report inventoryMovements() {
        String sql = "SELECT t.id, " +
                     "       GROUP_CONCAT(DISTINCT p.name SEPARATOR ', ') as productos, " +
                     "       t.type, " +
                     "       (SELECT SUM(ti.quantity) FROM transaction_items ti WHERE ti.transaction_id = t.id) as cantidad_total, " +
                     "       t.total_items, " +
                     "       t.date, " +
                     "       u.username as usuario, " +
                     "       COALESCE(pe.first_name, '') as persona_nombre, " +
                     "       COALESCE(pe.last_name, '') as persona_apellido, " +
                     "       t.notes " +
                     "FROM transactions t " +
                     "LEFT JOIN transaction_items ti ON ti.transaction_id = t.id " +
                     "LEFT JOIN products p ON p.id = ti.product_id " +
                     "LEFT JOIN users u ON u.id = t.user_id " +
                     "LEFT JOIN people pe ON pe.id = t.person_id " +
                     "WHERE t.date >= DATE_SUB(CURDATE(), INTERVAL 30 DAY) " +
                     "GROUP BY t.id, t.type, t.date, u.username, persona_nombre, persona_apellido, t.notes " +
                     "ORDER BY t.date DESC, t.created_at DESC";
        Report res = new Report("Movimientos de Inventario (Últimos 30 días)");
        res.addHeader("ID");
        res.addHeader("Productos");
        res.addHeader("Tipo");
        res.addHeader("Cantidad Total");
        res.addHeader("Items");
        res.addHeader("Fecha");
        res.addHeader("Usuario");
        res.addHeader("Persona");
        res.addHeader("Notas");

        try (PreparedStatement ps = Base.connection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                List<String> row = new ArrayList<>();
                row.add(String.valueOf(rs.getInt("id")));

                String productos = rs.getString("productos");
                int totalItems = rs.getInt("total_items");
                // Siempre mostrar nombres de productos
                row.add(productos != null ? productos : "N/A");

                // Formatear tipo de transacción
                String type = rs.getString("type");
                String typeLabel = switch (type) {
                    case "in" -> "Ingreso";
                    case "out" -> "Egreso";
                    case "loan" -> "Préstamo";
                    default -> type;
                };
                row.add(typeLabel);

                row.add(String.format("%.2f", rs.getDouble("cantidad_total")));
                row.add(String.valueOf(totalItems));
                row.add(rs.getString("date"));
                row.add(rs.getString("usuario"));

                // Concatenar nombre completo de persona si existe
                String personaNombre = rs.getString("persona_nombre");
                String personaApellido = rs.getString("persona_apellido");
                String personaCompleto = "";
                if (personaNombre != null && !personaNombre.isEmpty()) {
                    personaCompleto = personaNombre + " " + personaApellido;
                }
                row.add(personaCompleto.trim());

                row.add(rs.getString("notes"));
                res.addRow(row);
            }
        } catch (Exception e) {
            Logs.error(this.getClass(), "Error ejecutando inventoryMovements: {}", e.getMessage());
            throw new RuntimeException(e);
        }
        return res;
    }

    // --- REPORTES CON RANGO DE FECHAS PERSONALIZADO ---

    // Reporte de movimientos de inventario por rango de fechas
    public Report inventoryMovementsByDateRange(String from, String to) {
        String sql = "SELECT t.id, " +
                     "       GROUP_CONCAT(DISTINCT p.name SEPARATOR ', ') as productos, " +
                     "       t.type, " +
                     "       (SELECT SUM(ti.quantity) FROM transaction_items ti WHERE ti.transaction_id = t.id) as cantidad_total, " +
                     "       t.total_items, " +
                     "       t.date, " +
                     "       u.username as usuario, " +
                     "       COALESCE(pe.first_name, '') as persona_nombre, " +
                     "       COALESCE(pe.last_name, '') as persona_apellido " +
                     "FROM transactions t " +
                     "LEFT JOIN transaction_items ti ON ti.transaction_id = t.id " +
                     "LEFT JOIN products p ON p.id = ti.product_id " +
                     "LEFT JOIN users u ON u.id = t.user_id " +
                     "LEFT JOIN people pe ON pe.id = t.person_id " +
                     "WHERE t.date BETWEEN ? AND ? " +
                     "GROUP BY t.id, t.type, t.date, u.username, persona_nombre, persona_apellido " +
                     "ORDER BY t.date DESC, t.created_at DESC";
        Report res = new Report("Movimientos de Inventario por Rango de Fechas");
        res.addHeader("ID");
        res.addHeader("Productos");
        res.addHeader("Tipo");
        res.addHeader("Cantidad Total");
        res.addHeader("Items");
        res.addHeader("Fecha");
        res.addHeader("Usuario");
        res.addHeader("Persona");

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = Base.connection().prepareStatement(sql);
            ps.setString(1, from);
            ps.setString(2, to);
            rs = ps.executeQuery();
            while (rs.next()) {
                List<String> row = new ArrayList<>();
                row.add(String.valueOf(rs.getInt("id")));

                String productos = rs.getString("productos");
                int totalItems = rs.getInt("total_items");
                // Siempre mostrar nombres de productos
                row.add(productos != null ? productos : "N/A");

                // Formatear tipo de transacción
                String type = rs.getString("type");
                String typeLabel = switch (type) {
                    case "in" -> "Ingreso";
                    case "out" -> "Egreso";
                    case "loan" -> "Préstamo";
                    default -> type;
                };
                row.add(typeLabel);

                row.add(String.format("%.2f", rs.getDouble("cantidad_total")));
                row.add(String.valueOf(totalItems));
                row.add(rs.getString("date"));
                row.add(rs.getString("usuario"));

                // Concatenar nombre completo de persona si existe
                String personaNombre = rs.getString("persona_nombre");
                String personaApellido = rs.getString("persona_apellido");
                String personaCompleto = "";
                if (personaNombre != null && !personaNombre.isEmpty()) {
                    personaCompleto = personaNombre + " " + personaApellido;
                }
                row.add(personaCompleto.trim());

                res.addRow(row);
            }
        } catch (Exception e) {
            Logs.error(this.getClass(), "Error ejecutando inventoryMovementsByDateRange: {}", e.getMessage());
            throw new RuntimeException(e);
        } finally {
            try { if (rs != null) rs.close(); } catch (Exception ignored) {}
            try { if (ps != null) ps.close(); } catch (Exception ignored) {}
        }
        return res;
    }

    // Reporte de productos registrados por rango de fechas
    public Report inventoryProductsByDateRange(String from, String to) {
        String sql = "SELECT id, name, description, unit, current_stock, minimum_stock, created_at " +
                     "FROM products WHERE date(created_at) BETWEEN date(?) AND date(?) ORDER BY created_at DESC";
        Report res = new Report("Productos Registrados por Rango de Fechas");
        res.addHeader("ID");
        res.addHeader("Nombre");
        res.addHeader("Descripción");
        res.addHeader("Unidad");
        res.addHeader("Stock Actual");
        res.addHeader("Stock Mínimo");
        res.addHeader("Fecha Registro");

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = Base.connection().prepareStatement(sql);
            ps.setString(1, from);
            ps.setString(2, to);
            rs = ps.executeQuery();
            while (rs.next()) {
                List<String> row = new ArrayList<>();
                row.add(String.valueOf(rs.getInt("id")));
                row.add(rs.getString("name"));
                row.add(rs.getString("description"));
                row.add(rs.getString("unit"));
                row.add(String.valueOf(rs.getObject("current_stock")));
                row.add(String.valueOf(rs.getObject("minimum_stock")));
                row.add(rs.getString("created_at"));
                res.addRow(row);
            }
        } catch (Exception e) {
            Logs.error(this.getClass(), "Error ejecutando inventoryProductsByDateRange: {}", e.getMessage());
            throw new RuntimeException(e);
        } finally {
            try { if (rs != null) rs.close(); } catch (Exception ignored) {}
            try { if (ps != null) ps.close(); } catch (Exception ignored) {}
        }
        return res;
    }

    // --- REPORTES DE TRANSACCIONES ---

    // Reporte de historial de transacciones completo
    public Report transactionsAll() {
        String sql = "SELECT t.id, " +
                     "       GROUP_CONCAT(DISTINCT p.name SEPARATOR ', ') as productos, " +
                     "       t.type, " +
                     "       (SELECT SUM(ti.quantity) FROM transaction_items ti WHERE ti.transaction_id = t.id) as cantidad_total, " +
                     "       t.total_items, " +
                     "       t.date, " +
                     "       u.username as usuario, " +
                     "       COALESCE(pe.first_name, '') as persona_nombre, " +
                     "       COALESCE(pe.last_name, '') as persona_apellido, " +
                     "       t.created_at " +
                     "FROM transactions t " +
                     "LEFT JOIN transaction_items ti ON ti.transaction_id = t.id " +
                     "LEFT JOIN products p ON p.id = ti.product_id " +
                     "LEFT JOIN users u ON u.id = t.user_id " +
                     "LEFT JOIN people pe ON pe.id = t.person_id " +
                     "GROUP BY t.id, t.type, t.date, u.username, persona_nombre, persona_apellido, t.created_at " +
                     "ORDER BY t.created_at DESC";
        Report res = new Report("Historial Completo de Transacciones");
        res.addHeader("ID");
        res.addHeader("Productos");
        res.addHeader("Tipo");
        res.addHeader("Cantidad Total");
        res.addHeader("Items");
        res.addHeader("Fecha");
        res.addHeader("Usuario");
        res.addHeader("Persona");
        res.addHeader("Registro");

        try (PreparedStatement ps = Base.connection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                List<String> row = new ArrayList<>();
                row.add(String.valueOf(rs.getInt("id")));

                // Mostrar productos
                String productos = rs.getString("productos");
                int totalItems = rs.getInt("total_items");
                // Siempre mostrar nombres de productos
                row.add(productos != null ? productos : "N/A");

                // Formatear tipo de transacción
                String type = rs.getString("type");
                String typeLabel = switch (type) {
                    case "in" -> "Ingreso";
                    case "out" -> "Egreso";
                    case "loan" -> "Préstamo";
                    default -> type;
                };
                row.add(typeLabel);

                row.add(String.format("%.2f", rs.getDouble("cantidad_total")));
                row.add(String.valueOf(totalItems));
                row.add(rs.getString("date"));
                row.add(rs.getString("usuario"));

                // Concatenar nombre completo de persona si existe
                String personaNombre = rs.getString("persona_nombre");
                String personaApellido = rs.getString("persona_apellido");
                String personaCompleto = "";
                if (personaNombre != null && !personaNombre.isEmpty()) {
                    personaCompleto = personaNombre + " " + personaApellido;
                }
                row.add(personaCompleto.trim());

                row.add(rs.getString("created_at"));
                res.addRow(row);
            }
        } catch (Exception e) {
            Logs.error(this.getClass(), "Error ejecutando transactionsAll: {}", e.getMessage());
            throw new RuntimeException(e);
        }
        return res;
    }

    // Reporte de transacciones por tipo
    public Report transactionsByTypeReport(@NotNull String type) {
        String sql = "SELECT t.id, " +
                     "       GROUP_CONCAT(DISTINCT p.name SEPARATOR ', ') as productos, " +
                     "       t.type, " +
                     "       (SELECT SUM(ti.quantity) FROM transaction_items ti WHERE ti.transaction_id = t.id) as cantidad_total, " +
                     "       t.total_items, " +
                     "       t.date, " +
                     "       u.username as usuario, " +
                     "       COALESCE(pe.first_name, '') as persona_nombre, " +
                     "       COALESCE(pe.last_name, '') as persona_apellido, " +
                     "       t.created_at " +
                     "FROM transactions t " +
                     "LEFT JOIN transaction_items ti ON ti.transaction_id = t.id " +
                     "LEFT JOIN products p ON p.id = ti.product_id " +
                     "LEFT JOIN users u ON u.id = t.user_id " +
                     "LEFT JOIN people pe ON pe.id = t.person_id " +
                     "WHERE t.type = ? " +
                     "GROUP BY t.id, t.type, t.date, u.username, persona_nombre, persona_apellido, t.created_at " +
                     "ORDER BY t.created_at DESC";

        String typeName = switch (type) {
            case "in" -> "Ingresos";
            case "out" -> "Egresos";
            case "loan" -> "Préstamos";
            default -> "Transacciones";
        };

        Report res = new Report("Transacciones: " + typeName);
        res.addHeader("ID");
        res.addHeader("Productos");
        res.addHeader("Tipo");
        res.addHeader("Cantidad Total");
        res.addHeader("Items");
        res.addHeader("Fecha");
        res.addHeader("Usuario");
        res.addHeader("Persona");
        res.addHeader("Registro");

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = Base.connection().prepareStatement(sql);
            ps.setString(1, type);
            rs = ps.executeQuery();
            while (rs.next()) {
                List<String> row = new ArrayList<>();
                row.add(String.valueOf(rs.getInt("id")));

                // Mostrar productos
                String productos = rs.getString("productos");
                int totalItems = rs.getInt("total_items");
                // Siempre mostrar nombres de productos
                row.add(productos != null ? productos : "N/A");

                String typeValue = rs.getString("type");
                String typeLabel = switch (typeValue) {
                    case "in" -> "Ingreso";
                    case "out" -> "Egreso";
                    case "loan" -> "Préstamo";
                    default -> typeValue;
                };
                row.add(typeLabel);

                row.add(String.format("%.2f", rs.getDouble("cantidad_total")));
                row.add(String.valueOf(totalItems));
                row.add(rs.getString("date"));
                row.add(rs.getString("usuario"));

                String personaNombre = rs.getString("persona_nombre");
                String personaApellido = rs.getString("persona_apellido");
                String personaCompleto = "";
                if (personaNombre != null && !personaNombre.isEmpty()) {
                    personaCompleto = personaNombre + " " + personaApellido;
                }
                row.add(personaCompleto.trim());

                row.add(rs.getString("created_at"));
                res.addRow(row);
            }
        } catch (Exception e) {
            Logs.error(this.getClass(), "Error ejecutando transactionsByTypeReport: {}", e.getMessage());
            throw new RuntimeException(e);
        } finally {
            try { if (rs != null) rs.close(); } catch (Exception ignored) {}
            try { if (ps != null) ps.close(); } catch (Exception ignored) {}
        }
        return res;
    }

    // Reporte de transacciones por tipo y rango de fechas
    public Report transactionsByTypeAndDateRange(@NotNull String type, String from, String to) {
        String sql = "SELECT t.id, " +
                     "       GROUP_CONCAT(DISTINCT p.name SEPARATOR ', ') as productos, " +
                     "       t.type, " +
                     "       (SELECT SUM(ti.quantity) FROM transaction_items ti WHERE ti.transaction_id = t.id) as cantidad_total, " +
                     "       t.total_items, " +
                     "       t.date, " +
                     "       u.username as usuario, " +
                     "       COALESCE(pe.first_name, '') as persona_nombre, " +
                     "       COALESCE(pe.last_name, '') as persona_apellido, " +
                     "       t.created_at " +
                     "FROM transactions t " +
                     "LEFT JOIN transaction_items ti ON ti.transaction_id = t.id " +
                     "LEFT JOIN products p ON p.id = ti.product_id " +
                     "LEFT JOIN users u ON u.id = t.user_id " +
                     "LEFT JOIN people pe ON pe.id = t.person_id " +
                     "WHERE t.type = ? AND t.date BETWEEN ? AND ? " +
                     "GROUP BY t.id, t.type, t.date, u.username, persona_nombre, persona_apellido, t.created_at " +
                     "ORDER BY t.date DESC, t.created_at DESC";

        String typeName = switch (type) {
            case "in" -> "Ingresos";
            case "out" -> "Egresos";
            case "loan" -> "Préstamos";
            default -> "Transacciones";
        };

        Report res = new Report(typeName + " por Rango de Fechas");
        res.addHeader("ID");
        res.addHeader("Productos");
        res.addHeader("Tipo");
        res.addHeader("Cantidad Total");
        res.addHeader("Items");
        res.addHeader("Fecha");
        res.addHeader("Usuario");
        res.addHeader("Persona");
        res.addHeader("Registro");

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = Base.connection().prepareStatement(sql);
            ps.setString(1, type);
            ps.setString(2, from);
            ps.setString(3, to);
            rs = ps.executeQuery();
            while (rs.next()) {
                List<String> row = new ArrayList<>();
                row.add(String.valueOf(rs.getInt("id")));

                String productos = rs.getString("productos");
                int totalItems = rs.getInt("total_items");
                // Siempre mostrar nombres de productos
                row.add(productos != null ? productos : "N/A");

                String typeValue = rs.getString("type");
                String typeLabel = switch (typeValue) {
                    case "in" -> "Ingreso";
                    case "out" -> "Egreso";
                    case "loan" -> "Préstamo";
                    default -> typeValue;
                };
                row.add(typeLabel);

                row.add(String.format("%.2f", rs.getDouble("cantidad_total")));
                row.add(String.valueOf(totalItems));
                row.add(rs.getString("date"));
                row.add(rs.getString("usuario"));

                String personaNombre = rs.getString("persona_nombre");
                String personaApellido = rs.getString("persona_apellido");
                String personaCompleto = "";
                if (personaNombre != null && !personaNombre.isEmpty()) {
                    personaCompleto = personaNombre + " " + personaApellido;
                }
                row.add(personaCompleto.trim());

                row.add(rs.getString("created_at"));
                res.addRow(row);
            }
        } catch (Exception e) {
            Logs.error(this.getClass(), "Error ejecutando transactionsByTypeAndDateRange: {}", e.getMessage());
            throw new RuntimeException(e);
        } finally {
            try { if (rs != null) rs.close(); } catch (Exception ignored) {}
            try { if (ps != null) ps.close(); } catch (Exception ignored) {}
        }
        return res;
    }

    // Reporte de transacciones por usuario
    public Report transactionsByUserReport(int userId) {
        String sql = "SELECT t.id, " +
                     "       GROUP_CONCAT(DISTINCT p.name SEPARATOR ', ') as productos, " +
                     "       t.type, " +
                     "       (SELECT SUM(ti.quantity) FROM transaction_items ti WHERE ti.transaction_id = t.id) as cantidad_total, " +
                     "       t.total_items, " +
                     "       t.date, " +
                     "       u.username as usuario, " +
                     "       COALESCE(pe.first_name, '') as persona_nombre, " +
                     "       COALESCE(pe.last_name, '') as persona_apellido, " +
                     "       t.created_at " +
                     "FROM transactions t " +
                     "LEFT JOIN transaction_items ti ON ti.transaction_id = t.id " +
                     "LEFT JOIN products p ON p.id = ti.product_id " +
                     "LEFT JOIN users u ON u.id = t.user_id " +
                     "LEFT JOIN people pe ON pe.id = t.person_id " +
                     "WHERE t.user_id = ? " +
                     "GROUP BY t.id, t.type, t.date, u.username, persona_nombre, persona_apellido, t.created_at " +
                     "ORDER BY t.created_at DESC";

        Report res = new Report("Transacciones por Usuario");
        res.addHeader("ID");
        res.addHeader("Productos");
        res.addHeader("Tipo");
        res.addHeader("Cantidad Total");
        res.addHeader("Items");
        res.addHeader("Fecha");
        res.addHeader("Usuario");
        res.addHeader("Persona");
        res.addHeader("Registro");

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = Base.connection().prepareStatement(sql);
            ps.setInt(1, userId);
            rs = ps.executeQuery();
            while (rs.next()) {
                List<String> row = new ArrayList<>();
                row.add(String.valueOf(rs.getInt("id")));

                String productos = rs.getString("productos");
                int totalItems = rs.getInt("total_items");
                // Siempre mostrar nombres de productos
                row.add(productos != null ? productos : "N/A");

                String type = rs.getString("type");
                String typeLabel = switch (type) {
                    case "in" -> "Ingreso";
                    case "out" -> "Egreso";
                    case "loan" -> "Préstamo";
                    default -> type;
                };
                row.add(typeLabel);

                row.add(String.format("%.2f", rs.getDouble("cantidad_total")));
                row.add(String.valueOf(totalItems));
                row.add(rs.getString("date"));
                row.add(rs.getString("usuario"));

                String personaNombre = rs.getString("persona_nombre");
                String personaApellido = rs.getString("persona_apellido");
                String personaCompleto = "";
                if (personaNombre != null && !personaNombre.isEmpty()) {
                    personaCompleto = personaNombre + " " + personaApellido;
                }
                row.add(personaCompleto.trim());

                row.add(rs.getString("created_at"));
                res.addRow(row);
            }
        } catch (Exception e) {
            Logs.error(this.getClass(), "Error ejecutando transactionsByUserReport: {}", e.getMessage());
            throw new RuntimeException(e);
        } finally {
            try { if (rs != null) rs.close(); } catch (Exception ignored) {}
            try { if (ps != null) ps.close(); } catch (Exception ignored) {}
        }
        return res;
    }

    // Reporte de transacciones por persona
    public Report transactionsByPersonReport(int personId) {
        String sql = "SELECT t.id, " +
                     "       GROUP_CONCAT(DISTINCT p.name SEPARATOR ', ') as productos, " +
                     "       t.type, " +
                     "       (SELECT SUM(ti.quantity) FROM transaction_items ti WHERE ti.transaction_id = t.id) as cantidad_total, " +
                     "       t.total_items, " +
                     "       t.date, " +
                     "       u.username as usuario, " +
                     "       COALESCE(pe.first_name, '') as persona_nombre, " +
                     "       COALESCE(pe.last_name, '') as persona_apellido, " +
                     "       t.created_at " +
                     "FROM transactions t " +
                     "LEFT JOIN transaction_items ti ON ti.transaction_id = t.id " +
                     "LEFT JOIN products p ON p.id = ti.product_id " +
                     "LEFT JOIN users u ON u.id = t.user_id " +
                     "LEFT JOIN people pe ON pe.id = t.person_id " +
                     "WHERE t.person_id = ? " +
                     "GROUP BY t.id, t.type, t.date, u.username, persona_nombre, persona_apellido, t.created_at " +
                     "ORDER BY t.created_at DESC";

        Report res = new Report("Transacciones por Persona");
        res.addHeader("ID");
        res.addHeader("Productos");
        res.addHeader("Tipo");
        res.addHeader("Cantidad Total");
        res.addHeader("Items");
        res.addHeader("Fecha");
        res.addHeader("Usuario");
        res.addHeader("Persona");
        res.addHeader("Registro");

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = Base.connection().prepareStatement(sql);
            ps.setInt(1, personId);
            rs = ps.executeQuery();
            while (rs.next()) {
                List<String> row = new ArrayList<>();
                row.add(String.valueOf(rs.getInt("id")));

                String productos = rs.getString("productos");
                int totalItems = rs.getInt("total_items");
                // Siempre mostrar nombres de productos
                row.add(productos != null ? productos : "N/A");

                String type = rs.getString("type");
                String typeLabel = switch (type) {
                    case "in" -> "Ingreso";
                    case "out" -> "Egreso";
                    case "loan" -> "Préstamo";
                    default -> type;
                };
                row.add(typeLabel);

                row.add(String.format("%.2f", rs.getDouble("cantidad_total")));
                row.add(String.valueOf(totalItems));
                row.add(rs.getString("date"));
                row.add(rs.getString("usuario"));

                String personaNombre = rs.getString("persona_nombre");
                String personaApellido = rs.getString("persona_apellido");
                String personaCompleto = "";
                if (personaNombre != null && !personaNombre.isEmpty()) {
                    personaCompleto = personaNombre + " " + personaApellido;
                }
                row.add(personaCompleto.trim());

                row.add(rs.getString("created_at"));
                res.addRow(row);
            }
        } catch (Exception e) {
            Logs.error(this.getClass(), "Error ejecutando transactionsByPersonReport: {}", e.getMessage());
            throw new RuntimeException(e);
        } finally {
            try { if (rs != null) rs.close(); } catch (Exception ignored) {}
            try { if (ps != null) ps.close(); } catch (Exception ignored) {}
        }
        return res;
    }

    // --- REPORTES ANALÍTICOS Y DE GESTIÓN ---

    // Reporte de productos más movidos
    public Report productsMostMoved(String from, String to) {
        String sql = "SELECT p.id, p.name, p.unit, " +
                     "       COUNT(DISTINCT t.id) as total_movimientos, " +
                     "       SUM(CASE WHEN t.type='out' THEN ti.quantity ELSE 0 END) as total_egresos, " +
                     "       SUM(CASE WHEN t.type='in' THEN ti.quantity ELSE 0 END) as total_ingresos, " +
                     "       SUM(CASE WHEN t.type='loan' THEN ti.quantity ELSE 0 END) as total_prestamos " +
                     "FROM products p " +
                     "LEFT JOIN transaction_items ti ON p.id = ti.product_id " +
                     "LEFT JOIN transactions t ON ti.transaction_id = t.id AND t.date BETWEEN ? AND ? " +
                     "GROUP BY p.id " +
                     "ORDER BY total_movimientos DESC " +
                     "LIMIT 20";

        Report res = new Report("Productos Más Movidos (Top 20)");
        res.addHeader("ID");
        res.addHeader("Producto");
        res.addHeader("Unidad");
        res.addHeader("Total Movimientos");
        res.addHeader("Total Egresos");
        res.addHeader("Total Ingresos");
        res.addHeader("Total Préstamos");

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = Base.connection().prepareStatement(sql);
            ps.setString(1, from);
            ps.setString(2, to);
            rs = ps.executeQuery();
            while (rs.next()) {
                List<String> row = new ArrayList<>();
                row.add(String.valueOf(rs.getInt("id")));
                row.add(rs.getString("name"));
                row.add(rs.getString("unit"));
                row.add(String.valueOf(rs.getInt("total_movimientos")));
                row.add(String.valueOf(rs.getObject("total_egresos")));
                row.add(String.valueOf(rs.getObject("total_ingresos")));
                row.add(String.valueOf(rs.getObject("total_prestamos")));
                res.addRow(row);
            }
        } catch (Exception e) {
            Logs.error(this.getClass(), "Error ejecutando productsMostMoved: {}", e.getMessage());
            throw new RuntimeException(e);
        } finally {
            try { if (rs != null) rs.close(); } catch (Exception ignored) {}
            try { if (ps != null) ps.close(); } catch (Exception ignored) {}
        }
        return res;
    }

    // Reporte de personas con préstamos activos
    public Report peopleWithActiveLoans() {
        String sql = "SELECT pe.id, pe.dni, pe.first_name, pe.last_name, " +
                     "       GROUP_CONCAT(DISTINCT p.name SEPARATOR ', ') as productos, " +
                     "       (SELECT SUM(ti.quantity) FROM transaction_items ti WHERE ti.transaction_id = t.id) as cantidad_total, " +
                     "       t.total_items, " +
                     "       t.date, t.created_at, " +
                     "       DATEDIFF(CURDATE(), t.date) as dias_prestamo " +
                     "FROM transactions t " +
                     "JOIN people pe ON t.person_id = pe.id " +
                     "JOIN transaction_items ti ON ti.transaction_id = t.id " +
                     "JOIN products p ON ti.product_id = p.id " +
                     "WHERE t.type = 'loan' " +
                     "GROUP BY t.id, pe.id, pe.dni, pe.first_name, pe.last_name, t.date, t.created_at " +
                     "ORDER BY t.created_at DESC";

        Report res = new Report("Personas con Préstamos Activos");
        res.addHeader("ID Persona");
        res.addHeader("DNI");
        res.addHeader("Nombre");
        res.addHeader("Apellido");
        res.addHeader("Productos");
        res.addHeader("Cantidad Total");
        res.addHeader("Items");
        res.addHeader("Fecha Préstamo");
        res.addHeader("Días Transcurridos");

        try (PreparedStatement ps = Base.connection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                List<String> row = new ArrayList<>();
                row.add(String.valueOf(rs.getInt("id")));
                row.add(rs.getString("dni"));
                row.add(rs.getString("first_name"));
                row.add(rs.getString("last_name"));

                String productos = rs.getString("productos");
                int totalItems = rs.getInt("total_items");
                // Siempre mostrar nombres de productos
                row.add(productos != null ? productos : "N/A");

                row.add(String.format("%.2f", rs.getDouble("cantidad_total")));
                row.add(String.valueOf(totalItems));
                row.add(rs.getString("date"));
                row.add(String.valueOf(rs.getInt("dias_prestamo")));
                res.addRow(row);
            }
        } catch (Exception e) {
            Logs.error(this.getClass(), "Error ejecutando peopleWithActiveLoans: {}", e.getMessage());
            throw new RuntimeException(e);
        }
        return res;
    }

    // Reporte de consumo mensual por producto
    public Report monthlyConsumption(int months) {
        String sql = "SELECT p.name as producto, p.unit, " +
                     "       DATE_FORMAT(t.date, '%Y-%m') as mes, " +
                     "       SUM(ti.quantity) as total_consumido " +
                     "FROM products p " +
                     "JOIN transaction_items ti ON p.id = ti.product_id " +
                     "JOIN transactions t ON ti.transaction_id = t.id " +
                     "WHERE t.type = 'out' AND t.date >= DATE_SUB(CURDATE(), INTERVAL ? MONTH) " +
                     "GROUP BY p.id, mes " +
                     "ORDER BY mes DESC, total_consumido DESC";

        Report res = new Report("Consumo Mensual de Productos (Últimos " + months + " meses)");
        res.addHeader("Producto");
        res.addHeader("Unidad");
        res.addHeader("Mes");
        res.addHeader("Total Consumido");

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = Base.connection().prepareStatement(sql);
            ps.setInt(1, months);
            rs = ps.executeQuery();
            while (rs.next()) {
                List<String> row = new ArrayList<>();
                row.add(rs.getString("producto"));
                row.add(rs.getString("unit"));
                row.add(rs.getString("mes"));
                row.add(String.valueOf(rs.getObject("total_consumido")));
                res.addRow(row);
            }
        } catch (Exception e) {
            Logs.error(this.getClass(), "Error ejecutando monthlyConsumption: {}", e.getMessage());
            throw new RuntimeException(e);
        } finally {
            try { if (rs != null) rs.close(); } catch (Exception ignored) {}
            try { if (ps != null) ps.close(); } catch (Exception ignored) {}
        }
        return res;
    }

    // Reporte de productos sin movimiento
    public Report productsWithoutMovement(int days) {
        String sql = "SELECT p.id, p.name, p.description, p.unit, p.current_stock, " +
                     "       p.created_at, " +
                     "       COALESCE(MAX(t.date), 'Sin movimientos') as ultimo_movimiento, " +
                     "       COALESCE(DATEDIFF(CURDATE(), MAX(t.date)), DATEDIFF(CURDATE(), p.created_at)) as dias_sin_movimiento " +
                     "FROM products p " +
                     "LEFT JOIN transaction_items ti ON p.id = ti.product_id " +
                     "LEFT JOIN transactions t ON ti.transaction_id = t.id " +
                     "GROUP BY p.id " +
                     "HAVING dias_sin_movimiento >= ? OR ultimo_movimiento = 'Sin movimientos' " +
                     "ORDER BY dias_sin_movimiento DESC";

        Report res = new Report("Productos Sin Movimiento (Últimos " + days + " días)");
        res.addHeader("ID");
        res.addHeader("Producto");
        res.addHeader("Descripción");
        res.addHeader("Unidad");
        res.addHeader("Stock Actual");
        res.addHeader("Último Movimiento");
        res.addHeader("Días Sin Movimiento");

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = Base.connection().prepareStatement(sql);
            ps.setInt(1, days);
            rs = ps.executeQuery();
            while (rs.next()) {
                List<String> row = new ArrayList<>();
                row.add(String.valueOf(rs.getInt("id")));
                row.add(rs.getString("name"));
                row.add(rs.getString("description"));
                row.add(rs.getString("unit"));
                row.add(String.valueOf(rs.getObject("current_stock")));
                row.add(rs.getString("ultimo_movimiento"));
                row.add(String.valueOf(rs.getInt("dias_sin_movimiento")));
                res.addRow(row);
            }
        } catch (Exception e) {
            Logs.error(this.getClass(), "Error ejecutando productsWithoutMovement: {}", e.getMessage());
            throw new RuntimeException(e);
        } finally {
            try { if (rs != null) rs.close(); } catch (Exception ignored) {}
            try { if (ps != null) ps.close(); } catch (Exception ignored) {}
        }
        return res;
    }

    // Reporte de actividad por usuario
    public Report userActivity(int userId, String from, String to) {
        String sql = "SELECT u.username as usuario, u.username, " +
                     "       DATE(t.created_at) as fecha, " +
                     "       COUNT(t.id) as total_transacciones, " +
                     "       SUM(CASE WHEN t.type='in' THEN 1 ELSE 0 END) as ingresos, " +
                     "       SUM(CASE WHEN t.type='out' THEN 1 ELSE 0 END) as egresos, " +
                     "       SUM(CASE WHEN t.type='loan' THEN 1 ELSE 0 END) as prestamos " +
                     "FROM transactions t " +
                     "JOIN users u ON t.user_id = u.id " +
                     "WHERE t.user_id = ? AND t.date BETWEEN ? AND ? " +
                     "GROUP BY DATE(t.created_at) " +
                     "ORDER BY fecha DESC";

        Report res = new Report("Actividad por Usuario");
        res.addHeader("Usuario");
        res.addHeader("Username");
        res.addHeader("Fecha");
        res.addHeader("Total Transacciones");
        res.addHeader("Ingresos");
        res.addHeader("Egresos");
        res.addHeader("Préstamos");

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = Base.connection().prepareStatement(sql);
            ps.setInt(1, userId);
            ps.setString(2, from);
            ps.setString(3, to);
            rs = ps.executeQuery();
            while (rs.next()) {
                List<String> row = new ArrayList<>();
                row.add(rs.getString("usuario"));
                row.add(rs.getString("username"));
                row.add(rs.getString("fecha"));
                row.add(String.valueOf(rs.getInt("total_transacciones")));
                row.add(String.valueOf(rs.getInt("ingresos")));
                row.add(String.valueOf(rs.getInt("egresos")));
                row.add(String.valueOf(rs.getInt("prestamos")));
                res.addRow(row);
            }
        } catch (Exception e) {
            Logs.error(this.getClass(), "Error ejecutando userActivity: {}", e.getMessage());
            throw new RuntimeException(e);
        } finally {
            try { if (rs != null) rs.close(); } catch (Exception ignored) {}
            try { if (ps != null) ps.close(); } catch (Exception ignored) {}
        }
        return res;
    }

    // Dashboard mensual con KPIs
    public Report monthlyDashboard(String yearMonth) {
        String sql = "SELECT " +
                     "  (SELECT COUNT(*) FROM products) as total_productos, " +
                     "  (SELECT COUNT(*) FROM products WHERE current_stock <= minimum_stock) as productos_stock_bajo, " +
                     "  (SELECT COUNT(*) FROM products WHERE current_stock = 0) as productos_sin_stock, " +
                     "  (SELECT SUM(current_stock) FROM products) as stock_total_sistema, " +
                     "  (SELECT COUNT(*) FROM transactions WHERE DATE_FORMAT(created_at, '%Y-%m') = ?) as transacciones_mes, " +
                     "  (SELECT COUNT(*) FROM transactions WHERE type='in' AND DATE_FORMAT(created_at, '%Y-%m') = ?) as ingresos_mes, " +
                     "  (SELECT COUNT(*) FROM transactions WHERE type='out' AND DATE_FORMAT(created_at, '%Y-%m') = ?) as egresos_mes, " +
                     "  (SELECT COUNT(*) FROM transactions WHERE type='loan' AND DATE_FORMAT(created_at, '%Y-%m') = ?) as prestamos_mes, " +
                     "  (SELECT COUNT(DISTINCT user_id) FROM transactions WHERE DATE_FORMAT(created_at, '%Y-%m') = ?) as usuarios_activos, " +
                     "  (SELECT COUNT(DISTINCT person_id) FROM transactions WHERE person_id IS NOT NULL AND DATE_FORMAT(created_at, '%Y-%m') = ?) as personas_activas, " +
                     "  (SELECT COUNT(*) FROM people) as total_personas_registradas, " +
                     "  (SELECT COUNT(*) FROM users WHERE rol_id != 1) as total_usuarios_sistema";

        Report res = new Report("Dashboard Mensual - " + yearMonth);
        res.addHeader("Métrica");
        res.addHeader("Valor");

        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = Base.connection().prepareStatement(sql);
            ps.setString(1, yearMonth);
            ps.setString(2, yearMonth);
            ps.setString(3, yearMonth);
            ps.setString(4, yearMonth);
            ps.setString(5, yearMonth);
            ps.setString(6, yearMonth);
            rs = ps.executeQuery();

            if (rs.next()) {
                res.addRow(List.of("Total de Productos en Sistema", String.valueOf(rs.getInt("total_productos"))));
                res.addRow(List.of("Productos con Stock Bajo", String.valueOf(rs.getInt("productos_stock_bajo"))));
                res.addRow(List.of("Productos Sin Stock", String.valueOf(rs.getInt("productos_sin_stock"))));
                res.addRow(List.of("Stock Total en Sistema", String.valueOf(rs.getObject("stock_total_sistema"))));
                res.addRow(List.of("--- TRANSACCIONES DEL MES ---", "---"));
                res.addRow(List.of("Total Transacciones", String.valueOf(rs.getInt("transacciones_mes"))));
                res.addRow(List.of("Ingresos", String.valueOf(rs.getInt("ingresos_mes"))));
                res.addRow(List.of("Egresos", String.valueOf(rs.getInt("egresos_mes"))));
                res.addRow(List.of("Préstamos", String.valueOf(rs.getInt("prestamos_mes"))));
                res.addRow(List.of("--- ACTIVIDAD DEL MES ---", "---"));
                res.addRow(List.of("Usuarios Activos", String.valueOf(rs.getInt("usuarios_activos"))));
                res.addRow(List.of("Personas con Movimientos", String.valueOf(rs.getInt("personas_activas"))));
                res.addRow(List.of("--- RESUMEN GENERAL ---", "---"));
                res.addRow(List.of("Total Personas Registradas", String.valueOf(rs.getInt("total_personas_registradas"))));
                res.addRow(List.of("Total Usuarios del Sistema", String.valueOf(rs.getInt("total_usuarios_sistema"))));
            }
        } catch (Exception e) {
            Logs.error(this.getClass(), "Error ejecutando monthlyDashboard: {}", e.getMessage());
            throw new RuntimeException(e);
        } finally {
            try { if (rs != null) rs.close(); } catch (Exception ignored) {}
            try { if (ps != null) ps.close(); } catch (Exception ignored) {}
        }
        return res;
    }
}


