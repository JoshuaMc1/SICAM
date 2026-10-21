package com.joshuamc1.sicam.repositories;

import com.joshuamc1.sicam.core.enums.StockLevel;
import com.joshuamc1.sicam.models.Report;
import org.javalite.activejdbc.Base;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** Consultas compartidas por CSV y PDF. Los históricos conservan los estados;
 * los indicadores operativos solo cuentan transacciones y artículos activos. */
public class ReportRepository {
    private final java.util.function.Supplier<java.sql.Connection> connection;
    public ReportRepository() { connection = Base::connection; }
    ReportRepository(java.sql.Connection connection) { this.connection = () -> connection; }

    private static final String STATE = "CASE %s WHEN 'active' THEN 'Activo' WHEN 'invalidated' THEN 'Invalidado' "
        + "WHEN 'cancelled' THEN 'Cancelado' WHEN 'deleted' THEN 'Eliminado' ELSE 'Inactivo' END";
    private static final String VALID = "t.status = 'active' AND ti.status = 'active'";

    private Report query(String title, String[] headers, String sql, Object... params) {
        Report report = new Report(title);
        for (String header : headers) report.addHeader(header);
        try (PreparedStatement ps = connection.get().prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    List<String> row = new ArrayList<>();
                    for (int i = 1; i <= headers.length; i++) {
                        String value = rs.getString(i);
                        row.add(value == null ? "" : value);
                    }
                    report.addRow(row);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo generar " + title, e);
        }
        return report;
    }

    public Report users(boolean includeRoot, boolean grouped, boolean includeDeleted) {
        Report report = query(grouped ? "Usuarios agrupados por rol" : "Usuarios del sistema",
            grouped ? new String[]{"ID", "Nombre", "Usuario", "Rol", "Estado", "Fecha de registro"}
                : new String[]{"ID", "Nombre", "Usuario", "Estado", "Fecha de registro"},
            "SELECT u.id, u.name, u.username, "
            + (grouped ? "COALESCE(NULLIF(r.nombre, ''), 'Sin rol'), " : "")
            + String.format(STATE, "u.status") + ", u.created_at "
            + "FROM users u LEFT JOIN roles r ON r.id = u.rol_id WHERE UPPER(COALESCE(u.username, '')) != 'SYSTEM' "
            + "AND UPPER(COALESCE(r.nombre, '')) != 'ROOT' "
            + (includeDeleted ? "" : "AND u.status != 'deleted' ")
            + "ORDER BY " + (grouped ? "r.nombre, " : "") + "u.id DESC");
        if (grouped) report.setGroupByColumn("Rol");
        return report;
    }
    public Report usersWithRoles() { return users(true, false, false); }
    public Report usersExcludingRoot() { return users(false, false, false); }
    public Report usersGroupedByRole() { return users(false, true, false); }

    public Report inventory(String title, String from, String to, boolean includeDeleted, StockLevel level) {
        return inventory(title, from, to, includeDeleted, level, false);
    }

    public Report inventory(String title, String from, String to, boolean includeDeleted, StockLevel level, boolean includeCode) {
        List<Object> params = new ArrayList<>();
        String where = includeDeleted ? "1=1" : "status != 'deleted'";
        if (from != null) {
            where += " AND DATE(created_at) BETWEEN DATE(?) AND DATE(?)";
            params.add(from); params.add(to);
        }
        List<String> headers = new ArrayList<>(List.of("ID", "Producto", "Descripción", "Unidad", "Stock actual", "Stock mínimo", "Estado", "Fecha de registro"));
        if (includeCode) headers.add(1, "Código");
        Report report = query(title + " — existencias actuales", headers.toArray(new String[0]),
            "SELECT id, " + (includeCode ? "code, " : "") + "name, description, unit, current_stock, minimum_stock, " + String.format(STATE, "status")
            + ", created_at FROM products WHERE " + where + " ORDER BY id DESC", params.toArray());
        report.setLandscape(true);
        report.addHeader("Nivel de stock");
        report.getRows().removeIf(row -> {
            StockLevel actual = StockLevel.classify(number(row.get(headers.indexOf("Stock actual"))), number(row.get(headers.indexOf("Stock mínimo"))));
            row.add(actual.getLabel());
            return level != null && actual != level;
        });
        return report;
    }
    private Double number(String value) { return value.isBlank() ? null : Double.valueOf(value); }
    public Report inventoryComplete() { return inventory("Inventario completo", null, null, false, null, true); }
    public Report inventoryTotal() { return inventoryComplete(); }
    public Report inventoryLowStockReport() { return inventory("Inventario bajo el mínimo", null, null, false, StockLevel.BELOW_MINIMUM); }
    public Report inventoryLowStock() { return inventoryLowStockReport(); }
    public Report inventoryOutOfStock() { return inventory("Inventario sin stock", null, null, false, StockLevel.OUT_OF_STOCK); }
    public Report inventoryProductsByDateRange(String from, String to) { return inventory("Productos registrados entre " + from + " y " + to, from, to, false, null); }
    public Report inventoryByDateRange(String from, String to) { return inventoryProductsByDateRange(from, to); }

    /** Una transacción por fila, con desglose de productos sin sumar unidades distintas. */
    public Report transactionDetails(String title, String type, String from, String to,
                                     Integer userId, Integer personId, String status) {
        return transactionDetails(title, type, from, to, userId, personId, status, false);
    }

    private Report transactionDetails(String title, String type, String from, String to,
                                      Integer userId, Integer personId, String status, boolean registrationDate) {
        List<Object> params = new ArrayList<>();
        String where = "1=1";
        if (type != null) { where += " AND t.type = ?"; params.add(type); }
        if (from != null) { where += registrationDate ? " AND DATE(t.created_at) BETWEEN DATE(?) AND DATE(?)" : " AND t.date BETWEEN ? AND ?"; params.add(from); params.add(to); }
        if (userId != null) { where += " AND t.user_id = ?"; params.add(userId); }
        if (personId != null) { where += " AND t.person_id = ?"; params.add(personId); }
        if (status != null) {
            if (!List.of("active", "invalidated", "cancelled").contains(status)) throw new IllegalArgumentException("Estado no válido");
            where += " AND t.status = ?"; params.add(status);
        }
        Report detail = query(title + " — " + (status == null ? "Todos los estados" : status.equals("active") ? "Activas" : status.equals("invalidated") ? "Invalidadas" : "Canceladas"),
            new String[]{"ID transacción", "Tipo", "Estado transacción", "Fecha de operación", "Fecha de registro",
                "Producto", "Unidad", "Cantidad", "Estado producto en transacción", "Usuario", "Persona",
                "Código préstamo", "Fecha de invalidación", "Invalidado por", "Motivo de invalidación", "Notas"},
            "SELECT t.id, CASE t.type WHEN 'in' THEN 'Ingreso' WHEN 'out' THEN 'Egreso' ELSE 'Préstamo' END, "
            + String.format(STATE, "t.status") + ", t.date, t.created_at, p.name, p.unit, ti.quantity, "
            + String.format(STATE, "ti.status") + ", u.username, "
            + "TRIM(CONCAT(COALESCE(pe.first_name,''), ' ', COALESCE(pe.last_name,''))), t.loan_code, "
            + "t.invalidated_at, iu.username, t.invalidation_reason, t.notes FROM transactions t "
            + "LEFT JOIN transaction_items ti ON ti.transaction_id = t.id "
            + "LEFT JOIN products p ON p.id = ti.product_id LEFT JOIN users u ON u.id = t.user_id "
            + "LEFT JOIN users iu ON iu.id = t.invalidated_by LEFT JOIN people pe ON pe.id = t.person_id "
            + "WHERE " + where + " ORDER BY t.id DESC, ti.id", params.toArray());
        Report grouped = new Report(detail.getTitle());
        for (String header : new String[]{"ID", "Productos", "Tipo", "Estado", "Fecha de operación",
                "Usuario", "Persona", "Fecha de registro", "Invalidación", "Notas"}) grouped.addHeader(header);
        java.util.Map<String, List<String>> transactions = new java.util.LinkedHashMap<>();
        for (List<String> row : detail.getRows()) {
            List<String> summary = transactions.get(row.get(0));
            if (summary == null) {
                String audit = row.get(12).isBlank() ? "" : "Fecha: " + row.get(12);
                if (!row.get(13).isBlank()) audit += "\nPor: " + row.get(13);
                if (!row.get(14).isBlank()) audit += "\nMotivo: " + row.get(14);
                summary = new ArrayList<>(List.of(row.get(0) + (row.get(11).isBlank() ? "" : "\n" + row.get(11)),
                    "", row.get(1), row.get(2), row.get(3), row.get(9), row.get(10), row.get(4), audit, row.get(15)));
                transactions.put(row.get(0), summary);
            }
            if (!row.get(5).isBlank()) {
                String product = row.get(5) + ": " + row.get(7) + " " + row.get(6);
                if (!"Activo".equals(row.get(8))) product += " (" + row.get(8) + ")";
                summary.set(1, summary.get(1) + (summary.get(1).isBlank() ? "" : "\n") + product);
            }
        }
        transactions.values().forEach(grouped::addRow);
        grouped.setLandscape(true);
        return grouped;
    }
    public Report transactionsAll() { return transactionDetails("Historial de transacciones", null, null, null, null, null, null); }
    public Report inventoryMovements() { return inventoryMovementsByDateRange(LocalDate.now().minusDays(29).toString(), LocalDate.now().toString()); }
    public Report inventoryMovementsByDateRange(String from, String to) { return transactionDetails("Movimientos " + from + " a " + to, null, from, to, null, null, null); }
    public Report transactions(String from, String to) { return inventoryMovementsByDateRange(from, to); }
    public Report transactionsByTypeReport(String type) { return transactionDetails("Transacciones por tipo", type, null, null, null, null, null); }
    public Report transactionsByTypeAndDateRange(String type, String from, String to) { return transactionDetails("Transacciones " + from + " a " + to, type, from, to, null, null, null); }
    public Report transactionsByType(String type, String from, String to) { return transactionsByTypeAndDateRange(type, from, to); }
    public Report transactionsByUserReport(int id) { return transactionDetails("Transacciones por usuario", null, null, null, id, null, null); }
    public Report transactionsByPersonReport(int id) { return transactionDetails("Transacciones por persona", null, null, null, null, id, null); }
    public Report transactionsByPerson(int id, String from, String to) { return transactionDetails("Transacciones por persona", null, from, to, null, id, null); }

    public Report peopleRegistered(String from, String to) {
        return query("Personas registradas " + from + " a " + to,
            new String[]{"ID", "DNI", "Nombre", "Apellido", "Teléfono", "Código interno", "Estado", "Fecha de registro"},
            "SELECT id, dni, first_name, last_name, phone, internal_code, " + String.format(STATE, "status")
            + ", created_at FROM people WHERE status != 'deleted' AND DATE(created_at) BETWEEN DATE(?) AND DATE(?) ORDER BY id DESC", from, to);
    }

    public Report productsMostMoved(String from, String to) {
        return query("Productos con mayor movimiento — operaciones vigentes — " + from + " a " + to,
            new String[]{"ID", "Producto", "Unidad", "Movimientos", "Cantidad egresada", "Cantidad ingresada", "Cantidad prestada"},
            "SELECT p.id, p.name, p.unit, COUNT(DISTINCT t.id), "
            + "SUM(CASE WHEN t.type='out' THEN ti.quantity ELSE 0 END), "
            + "SUM(CASE WHEN t.type='in' THEN ti.quantity ELSE 0 END), "
            + "SUM(CASE WHEN t.type='loan' THEN ti.quantity ELSE 0 END) "
            + "FROM products p JOIN transaction_items ti ON ti.product_id = p.id JOIN transactions t ON t.id = ti.transaction_id "
            + "WHERE " + VALID + " AND p.status != 'deleted' AND t.date BETWEEN ? AND ? "
            + "GROUP BY p.id, p.name, p.unit ORDER BY COUNT(DISTINCT t.id) DESC, p.id DESC", from, to);
    }

    public Report peopleWithActiveLoans() {
        Report report = query("Personas con Préstamos Activos",
            new String[]{"ID préstamo", "Código", "DNI", "Persona", "Producto", "Unidad", "Cantidad", "Fecha de operación", "Devolución prevista", "Estado guardado", "Teléfono"},
            "SELECT t.id, t.loan_code, pe.dni, TRIM(CONCAT(COALESCE(pe.first_name,''),' ',COALESCE(pe.last_name,''))), "
            + "p.name, p.unit, ti.quantity, t.date, t.expected_return_date, t.loan_status, pe.phone "
            + "FROM transactions t LEFT JOIN people pe ON pe.id = t.person_id "
            + "JOIN transaction_items ti ON ti.transaction_id = t.id JOIN products p ON p.id = ti.product_id "
            + "WHERE " + VALID + " AND t.type='loan' AND t.loan_status IN ('pending','overdue') "
            + "ORDER BY t.expected_return_date, t.id DESC, ti.id");
        Report grouped = new Report(report.getTitle());
        for (String header : new String[]{"ID préstamo", "Código", "DNI", "Persona", "Teléfono", "Productos",
                "Fecha de operación", "Devolución prevista", "Estado préstamo", "Días de atraso"}) grouped.addHeader(header);
        java.util.Map<String, List<String>> loans = new java.util.LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        for (List<String> row : report.getRows()) {
            List<String> summary = loans.computeIfAbsent(row.get(0), key -> {
                LocalDate due = row.get(8).isBlank() ? null : LocalDate.parse(row.get(8));
                boolean overdue = com.joshuamc1.sicam.utils.LoanDueDate.isOverdue(row.get(9), due, today);
                String state = overdue ? "Vencido" : due != null && due.equals(today) ? "Vence hoy" : "Pendiente";
                String delay = due == null ? "" : String.valueOf(Math.max(0, ChronoUnit.DAYS.between(due, today)));
                return new ArrayList<>(List.of(row.get(0), row.get(1), row.get(2), row.get(3), row.get(10),
                    "", row.get(7), row.get(8), state, delay));
            });
            summary.set(5, summary.get(5) + (summary.get(5).isEmpty() ? "" : "\n")
                + row.get(4) + ": " + row.get(6) + " " + row.get(5));
        }
        // Prioriza vencidos, luego los que vencen hoy, los próximos y los que no tienen fecha.
        java.util.Comparator<List<String>> priority = java.util.Comparator.comparingInt(row ->
            row.get(8).equals("Vencido") ? 0 : row.get(8).equals("Vence hoy") ? 1 : 2);
        priority = priority.thenComparing(row -> row.get(7).isBlank() ? "9999-12-31" : row.get(7))
            .thenComparing(java.util.Comparator.comparingLong((List<String> row) -> Long.parseLong(row.get(0))).reversed());
        loans.values().stream().sorted(priority).forEach(grouped::addRow);
        return grouped;
    }

    public Report monthlyConsumption(int months) {
        if (months < 1) throw new IllegalArgumentException("Meses debe ser positivo");
        LocalDate start = LocalDate.now().withDayOfMonth(1).minusMonths(months - 1L);
        Report report = query("Consumo mensual de productos — egresos vigentes — " + start + " a " + LocalDate.now(),
            new String[]{"ID producto", "Producto", "Unidad", "Mes", "Cantidad consumida", "Egresos"},
            "SELECT p.id, p.name, p.unit, SUBSTR(CAST(t.date AS CHAR),1,7) AS mes, SUM(ti.quantity), COUNT(DISTINCT t.id) "
            + "FROM products p JOIN transaction_items ti ON ti.product_id=p.id JOIN transactions t ON t.id=ti.transaction_id "
            + "WHERE " + VALID + " AND t.type='out' AND t.date BETWEEN ? AND ? "
            + "GROUP BY p.id, p.name, p.unit, SUBSTR(CAST(t.date AS CHAR),1,7) ORDER BY mes DESC, p.id DESC",
            start.toString(), LocalDate.now().toString());
        report.setLandscape(true);
        return report;
    }

    public Report productsWithoutMovement(int days) {
        if (days < 0) throw new IllegalArgumentException("Días no puede ser negativo");
        Report report = query("Productos sin movimiento válido durante al menos " + days + " días",
            new String[]{"ID", "Producto", "Descripción", "Unidad", "Stock actual", "Fecha de registro", "Último movimiento válido"},
            "SELECT p.id, p.name, p.description, p.unit, p.current_stock, p.created_at, MAX(t.date) "
            + "FROM products p LEFT JOIN transaction_items ti ON ti.product_id=p.id AND ti.status='active' "
            + "LEFT JOIN transactions t ON t.id=ti.transaction_id AND t.status='active' "
            + "WHERE p.status != 'deleted' GROUP BY p.id, p.name, p.description, p.unit, p.current_stock, p.created_at ORDER BY p.id DESC");
        report.addHeader("Días sin movimiento");
        LocalDate today = LocalDate.now();
        report.getRows().removeIf(row -> {
            String last = row.get(6).isBlank() ? row.get(5) : row.get(6);
            if (last.isBlank()) return true;
            long elapsed = ChronoUnit.DAYS.between(LocalDate.parse(last.substring(0, 10)), today);
            row.add(String.valueOf(Math.max(0, elapsed)));
            if (row.get(6).isBlank()) row.set(6, "Sin movimientos");
            return elapsed < days;
        });
        report.getRows().sort((a,b) -> Long.compare(Long.parseLong(b.get(7)), Long.parseLong(a.get(7))));
        return report;
    }

    /** Detalle de transacciones registradas por el usuario; no es la bitácora general de auditoría. */
    public Report userActivity(int userId, String from, String to) {
        return userActivity(userId, from, to, null);
    }

    public Report userActivity(int userId, String from, String to, String status) {
        if ((from == null) != (to == null)) throw new IllegalArgumentException("Indique ambas fechas o todas las actividades");
        Report report = transactionDetails("Actividad detallada del usuario #" + userId
            + (from == null ? " — todas las fechas" : " — registro " + from + " a " + to),
            null, from, to, userId, null, status, true);
        report.setLandscape(true);
        return report;
    }

    public Report monthlyDashboard(String yearMonth) {
        YearMonth month = YearMonth.parse(yearMonth);
        Report report = query("Resumen mensual " + month + " — actividad por fecha de operación",
            new String[]{"Tipo", "Estado", "Transacciones"},
            "SELECT type, status, COUNT(*) FROM transactions WHERE date BETWEEN ? AND ? GROUP BY type, status ORDER BY type, status",
            month.atDay(1).toString(), month.atEndOfMonth().toString());
        Report result = new Report(report.getTitle());
        result.addHeader("Sección"); result.addHeader("Métrica"); result.addHeader("Valor");
        long total = 0;
        for (List<String> row : report.getRows()) {
            String type = row.get(0).equals("in") ? "Ingresos" : row.get(0).equals("out") ? "Egresos" : "Préstamos";
            String state = row.get(1).equals("active") ? "Activos" : row.get(1).equals("invalidated") ? "Invalidados" : "Cancelados";
            result.addRow(List.of("Actividad de " + month, type + " — " + state, row.get(2)));
            total += Long.parseLong(row.get(2));
        }
        result.addRow(List.of("Actividad de " + month, "Total de transacciones registradas", String.valueOf(total)));
        Report products = inventoryComplete();
        result.addRow(List.of("Existencias actuales al " + LocalDate.now(), "Productos no eliminados", String.valueOf(products.getRows().size())));
        for (StockLevel level : StockLevel.values()) {
            long count = products.getRows().stream().filter(row -> row.get(products.getHeaders().indexOf("Nivel de stock")).equals(level.getLabel())).count();
            result.addRow(List.of("Existencias actuales", level.getLabel(), String.valueOf(count)));
        }
        Report stocks = query("Existencias por unidad", new String[]{"Unidad", "Stock"},
            "SELECT unit, COALESCE(SUM(current_stock),0) FROM products WHERE status != 'deleted' GROUP BY unit ORDER BY unit");
        for (List<String> row : stocks.getRows()) result.addRow(List.of("Existencias actuales", "Stock total (" + row.get(0) + ")", row.get(1)));
        Report activity = query("Actividad", new String[]{"Usuarios", "Personas"},
            "SELECT COUNT(DISTINCT user_id), COUNT(DISTINCT person_id) FROM transactions WHERE status='active' AND date BETWEEN ? AND ?",
            month.atDay(1).toString(), month.atEndOfMonth().toString());
        result.addRow(List.of("Actividad vigente de " + month, "Usuarios con operaciones", activity.getRows().get(0).get(0)));
        result.addRow(List.of("Actividad vigente de " + month, "Personas con operaciones", activity.getRows().get(0).get(1)));
        Report totals = query("Totales actuales", new String[]{"Personas", "Usuarios"},
            "SELECT (SELECT COUNT(*) FROM people WHERE status != 'deleted'), (SELECT COUNT(*) FROM users WHERE status != 'deleted' AND rol_id != 1)");
        result.addRow(List.of("Registros actuales", "Personas no eliminadas", totals.getRows().get(0).get(0)));
        result.addRow(List.of("Registros actuales", "Usuarios no eliminados (sin ROOT)", totals.getRows().get(0).get(1)));
        return result;
    }
}
