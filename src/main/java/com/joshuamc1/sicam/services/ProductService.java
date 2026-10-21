package com.joshuamc1.sicam.services;

import com.joshuamc1.sicam.core.BarcodeGenerator;
import com.joshuamc1.sicam.core.DatabaseManager;
import com.joshuamc1.sicam.core.Logs;
import com.joshuamc1.sicam.core.Session;
import com.joshuamc1.sicam.models.Product;
import com.joshuamc1.sicam.repositories.ProductRepository;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ProductService {
    private final ProductRepository repo = new ProductRepository();
    private final AuditService auditService = new AuditService();

    public List<Product> findAll() {
        if (!com.joshuamc1.sicam.core.Authorization.canAccess("DASHBOARD","READ")) com.joshuamc1.sicam.core.Authorization.require("INVENTARIO","READ");
        return DatabaseManager.callInConnection(repo::findAll);
    }

    /**
     * Busca productos por nombre o ID con límite de resultados (optimizado para ComboBox)
     * @param query Texto a buscar (nombre o ID)
     * @param limit Número máximo de resultados
     * @return Lista limitada de productos
     */
    public List<Product> searchProducts(String query, int limit) {
        com.joshuamc1.sicam.core.Authorization.require("INVENTARIO","READ");
        return DatabaseManager.callInConnection(() -> repo.searchProducts(query, limit));
    }

    public Product findById(Integer id) {
        com.joshuamc1.sicam.core.Authorization.require("INVENTARIO","READ");
        return DatabaseManager.callInConnection(() -> repo.findById(id));
    }

    /**
     * Busca un producto por su código exacto
     * Útil para lectores de códigos de barras/QR
     * @param code Código del producto
     * @return Producto encontrado o null
     */
    public Product findByCode(String code) {
        com.joshuamc1.sicam.core.Authorization.require("INVENTARIO","READ");
        return DatabaseManager.callInConnection(() -> repo.findByCode(code));
    }

    public Product createProduct(String name,String desc,String unit,double minStock) {
        return createProduct(name,desc,unit,0.0,minStock,null);
    }
    public Product createProduct(String name,String desc,String unit,double currentStock,double minStock) {
        return createProduct(name,desc,unit,currentStock,minStock,null);
    }
    public Product createProduct(String name,String desc,String unit,double currentStock,double minStock,String code) {
        com.joshuamc1.sicam.core.Authorization.require("INVENTARIO","CREATE");
        validateStock(currentStock);
        validateStock(minStock);
        if (code != null && !code.isBlank() && !BarcodeGenerator.isValidCode(code.trim())) throw new IllegalArgumentException("Código no válido");
        return productWrite(() -> {
            requireUniqueCode(code, null);
            Product product = repo.create(name, desc, unit, currentStock, minStock);
            if (code != null && !code.isBlank()) { product.set("code", code.trim()); repo.save(product); }
            auditService.logAction(Session.getCurrentUserId(), "create", "products", product.getInteger("id"), "name=" + name + ",unit=" + unit);
            return product;
        });
    }

    public List<Product> findLowStockProducts() {
        if (!com.joshuamc1.sicam.core.Authorization.canAccess("DASHBOARD","READ")) com.joshuamc1.sicam.core.Authorization.require("INVENTARIO","READ");
        return DatabaseManager.callInConnection(repo::findLowStock);
    }

    public long countAll() {
        if (!com.joshuamc1.sicam.core.Authorization.canAccess("DASHBOARD","READ")) com.joshuamc1.sicam.core.Authorization.require("INVENTARIO","READ");
        return DatabaseManager.callInConnection(repo::countAll);
    }

    public long countLowStock() {
        if (!com.joshuamc1.sicam.core.Authorization.canAccess("DASHBOARD","READ")) com.joshuamc1.sicam.core.Authorization.require("INVENTARIO","READ");
        return DatabaseManager.callInConnection(repo::countLowStock);
    }

    public long countOutOfStock() {
        if (!com.joshuamc1.sicam.core.Authorization.canAccess("DASHBOARD","READ")) com.joshuamc1.sicam.core.Authorization.require("INVENTARIO","READ");
        return DatabaseManager.callInConnection(repo::countOutOfStock);
    }

    public long countCreatedThisMonth() {
        if (!com.joshuamc1.sicam.core.Authorization.canAccess("DASHBOARD","READ")) com.joshuamc1.sicam.core.Authorization.require("INVENTARIO","READ");
        return DatabaseManager.callInConnection(repo::countCreatedThisMonth);
    }

    public long countCreatedLastMonth() {
        if (!com.joshuamc1.sicam.core.Authorization.canAccess("DASHBOARD","READ")) com.joshuamc1.sicam.core.Authorization.require("INVENTARIO","READ");
        return DatabaseManager.callInConnection(repo::countCreatedLastMonth);
    }

    public void save(@NotNull Product product) {
        save(product, null);
    }

    public void save(@NotNull Product product, Double expectedStock) {
        com.joshuamc1.sicam.core.Authorization.require("INVENTARIO", "UPDATE");
        String code = product.getString("code");
        if (code != null && !code.isBlank() && !BarcodeGenerator.isValidCode(code.trim())) throw new IllegalArgumentException("Código no válido");
        product.set("code", code == null || code.isBlank() ? null : code.trim());
        validateStock(product.getDouble("current_stock"));
        validateStock(product.getDouble("minimum_stock"));
        productWrite(() -> {
            requireUniqueCode(product.getString("code"), product.getInteger("id"));
            String suffix;
            try { suffix = org.javalite.activejdbc.Base.connection().getMetaData().getDatabaseProductName().equalsIgnoreCase("MySQL") ? " FOR UPDATE" : ""; }
            catch (java.sql.SQLException ex) { throw new IllegalStateException("No se pudo verificar el producto", ex); }
            List<Product> matches = Product.findBySQL("SELECT * FROM products WHERE id = ?" + suffix, product.getInteger("id"));
            if (matches.isEmpty()) throw new IllegalStateException("Producto no encontrado");
            Product current = matches.get(0);
            if (!java.util.Objects.equals(current.getString("status"), product.getString("status"))) throw new IllegalStateException("El estado del producto cambió; vuelva a cargarlo");
            Double old = current.getDouble("current_stock");
            if (expectedStock != null && Double.compare(old == null ? 0 : old, expectedStock) != 0)
                throw new IllegalStateException("El stock cambió mientras editaba. Vuelva a cargar el producto");
            repo.save(product);
            String details = "id=" + product.getInteger("id") + ",name=" + product.getString("name")
                + ",stock_anterior=" + old + ",stock_nuevo=" + product.getDouble("current_stock")
                + ",minimum=" + product.getDouble("minimum_stock");
            auditService.logAction(Session.getCurrentUserId(), "update", "products", product.getInteger("id"), details);
            return null;
        });
    }

    private void requireUniqueCode(String code, Integer ownId) {
        if (code == null || code.isBlank()) return;
        Long matches = Product.count("LOWER(code) = LOWER(?) AND id <> ?", code.trim(), ownId == null ? 0 : ownId);
        if (matches != null && matches > 0) throw duplicateCode(null);
    }

    private IllegalArgumentException duplicateCode(Throwable cause) {
        return new IllegalArgumentException("Ya existe un producto con ese código. Utilice uno diferente. Si está inactivo o eliminado, revise ese registro.", cause);
    }

    private <T> T productWrite(java.util.function.Supplier<T> operation) {
        try {
            return DatabaseManager.callInTransaction(operation);
        } catch (RuntimeException ex) {
            // La restricción UNIQUE sigue protegiendo también ante altas concurrentes.
            for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
                if (cause instanceof java.sql.SQLException) {
                    String message = java.util.Objects.toString(cause.getMessage(), "").toLowerCase(java.util.Locale.ROOT);
                    if (message.contains("products.code") && (message.contains("duplicate") || message.contains("unique"))) {
                        throw duplicateCode(ex);
                    }
                }
            }
            throw ex;
        }
    }

    private void validateStock(Double value) {
        if (value == null || !Double.isFinite(value) || value < 0 || value > 99999999.99
            || java.math.BigDecimal.valueOf(value).stripTrailingZeros().scale() > 2)
            throw new IllegalArgumentException("Stock inválido: indique un número no negativo con hasta dos decimales");
    }

    // ===== MÉTODOS DE SOFT DELETE Y RESTAURACIÓN =====

    /**
     * Elimina un producto (soft delete o hard delete según permisos)
     * @param product Producto a eliminar
     */
    public void delete(@NotNull Product product) {
        Integer currentUserId = Session.getCurrentUserId();
        Integer id = product.getInteger("id");
        String name = product.getString("name");

        // Verificar permisos
        if (!com.joshuamc1.sicam.core.PermissionHelper.canAccess("INVENTARIO", "DELETE")) {
            throw new SecurityException("No tiene permiso para eliminar productos");
        }

        // Decidir si hacer soft delete o hard delete
        boolean canHardDelete = com.joshuamc1.sicam.core.PermissionHelper.canAccess("SISTEMA", "DESTRUCTIVE_OPERATIONS");

        DatabaseManager.runInConnection(() -> {
            if (canHardDelete) {
                // Validar antes de hard delete
                validateBeforeHardDelete(product);
                repo.hardDelete(product);
                auditService.logAction(
                    currentUserId,
                    "hard_delete",
                    "products",
                    id,
                    String.format("id=%s,name=%s", id, name)
                );
            } else {
                // Soft delete
                repo.softDelete(product, currentUserId);
                auditService.logAction(
                    currentUserId,
                    "soft_delete",
                    "products",
                    id,
                    String.format("id=%s,name=%s,status=%s", id, name, "deleted")
                );
            }
        });
    }

    /**
     * Soft delete explícito de un producto
     * @param product Producto a eliminar lógicamente
     */
    public void softDelete(@NotNull Product product) {
        Integer currentUserId = Session.getCurrentUserId();
        Integer id = product.getInteger("id");
        String name = product.getString("name");

        if (!com.joshuamc1.sicam.core.PermissionHelper.canAccess("INVENTARIO", "DELETE")) {
            throw new SecurityException("No tiene permiso para eliminar productos");
        }

        DatabaseManager.runInConnection(() -> {
            repo.softDelete(product, currentUserId);
            auditService.logAction(
                currentUserId,
                "soft_delete",
                "products",
                id,
                String.format("id=%s,name=%s", id, name)
            );
        });
    }

    /**
     * Eliminación física permanente (solo ROOT)
     * @param product Producto a eliminar permanentemente
     */
    public void hardDelete(@NotNull Product product) {
        Integer currentUserId = Session.getCurrentUserId();
        Integer id = product.getInteger("id");
        String name = product.getString("name");

        if (!com.joshuamc1.sicam.core.PermissionHelper.canAccess("SISTEMA", "DESTRUCTIVE_OPERATIONS")) {
            throw new SecurityException("Solo ROOT puede ejecutar eliminaciones permanentes");
        }

        DatabaseManager.runInConnection(() -> {
            // Validaciones antes de eliminar permanentemente
            validateBeforeHardDelete(product);

            repo.hardDelete(product);
            auditService.logAction(
                currentUserId,
                "hard_delete",
                "products",
                id,
                String.format("id=%s,name=%s [PERMANENT]", id, name)
            );
        });
    }

    /**
     * Restaura un producto eliminado lógicamente
     * @param product Producto a restaurar
     */
    public void restore(@NotNull Product product) {
        Integer currentUserId = Session.getCurrentUserId();
        Integer id = product.getInteger("id");
        String name = product.getString("name");

        if (!com.joshuamc1.sicam.core.PermissionHelper.canAccess("INVENTARIO", "RESTORE")) {
            throw new SecurityException("No tiene permiso para restaurar productos");
        }

        if (!product.isDeleted()) {
            throw new IllegalStateException("El producto no está eliminado");
        }

        DatabaseManager.runInConnection(() -> {
            repo.restore(product);
            auditService.logAction(
                currentUserId,
                "restore",
                "products",
                id,
                String.format("id=%s,name=%s", id, name)
            );
        });
    }

    /**
     * Cambia el estado de un producto a inactivo
     * @param product Producto a desactivar
     */
    public void markAsInactive(@NotNull Product product) {
        Integer currentUserId = Session.getCurrentUserId();
        Integer id = product.getInteger("id");

        if (!com.joshuamc1.sicam.core.PermissionHelper.canAccess("INVENTARIO", "UPDATE")) {
            throw new SecurityException("No tiene permiso para modificar productos");
        }

        DatabaseManager.runInConnection(() -> {
            product.markAsInactive();
            auditService.logAction(
                currentUserId,
                "mark_inactive",
                "products",
                id,
                "status=inactive"
            );
        });
    }

    /**
     * Cambia el estado de un producto a activo
     * @param product Producto a activar
     */
    public void markAsActive(@NotNull Product product) {
        if (product.isDeleted()) throw new SecurityException("Los eliminados deben recuperarse mediante RESTORE");
        Integer currentUserId = Session.getCurrentUserId();
        Integer id = product.getInteger("id");

        if (!com.joshuamc1.sicam.core.PermissionHelper.canAccess("INVENTARIO", "UPDATE")) {
            throw new SecurityException("No tiene permiso para modificar productos");
        }

        DatabaseManager.runInConnection(() -> {
            product.markAsActive();
            auditService.logAction(
                currentUserId,
                "mark_active",
                "products",
                id,
                "status=active"
            );
        });
    }

    // ===== MÉTODOS DE BÚSQUEDA AVANZADA =====

    /**
     * Lista todos los productos incluyendo eliminados (requiere permiso VIEW_DELETED)
     */
    public List<Product> findAllIncludingDeleted() {
        if (!com.joshuamc1.sicam.core.PermissionHelper.canAccess("SISTEMA", "VIEW_DELETED")) {
            throw new SecurityException("No tiene permiso para ver productos eliminados");
        }
        return DatabaseManager.callInConnection(repo::findAllIncludingDeleted);
    }

    /**
     * Lista solo productos eliminados (requiere permiso VIEW_DELETED)
     */
    public List<Product> findDeleted() {
        if (!com.joshuamc1.sicam.core.PermissionHelper.canAccess("SISTEMA", "VIEW_DELETED")) {
            throw new SecurityException("No tiene permiso para ver productos eliminados");
        }
        return DatabaseManager.callInConnection(repo::findDeleted);
    }

    /**
     * Cuenta productos eliminados
     */
    public long countDeleted() {
        if (!com.joshuamc1.sicam.core.PermissionHelper.canAccess("SISTEMA", "VIEW_DELETED")) {
            return 0L;
        }
        return DatabaseManager.callInConnection(repo::countDeleted);
    }

    // ===== VALIDACIONES DE NEGOCIO =====

    /**
     * Valida si un producto puede ser eliminado permanentemente
     * @param product Producto a validar
     */
    private void validateBeforeHardDelete(@NotNull Product product) {
        // No permitir eliminar productos activos
        if (product.isActive()) {
            throw new IllegalStateException(
                "No se puede eliminar permanentemente un producto activo. " +
                "Primero debe marcarse como eliminado."
            );
        }

        // Verificar si tiene transacciones asociadas
        Long transactionCount = (Long) org.javalite.activejdbc.Base.firstCell(
            "SELECT COUNT(*) FROM transaction_items WHERE product_id = ?",
            product.getId()
        );

        if (transactionCount != null && transactionCount > 0) {
            throw new IllegalStateException(
                "No se puede eliminar permanentemente este producto porque tiene " +
                transactionCount + " transacción(es) asociada(s). " +
                "Los productos con historial de transacciones no pueden ser eliminados del sistema."
            );
        }
    }
}