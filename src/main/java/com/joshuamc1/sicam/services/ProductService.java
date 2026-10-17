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
        return DatabaseManager.callInConnection(repo::findAll);
    }

    /**
     * Busca productos por nombre o ID con límite de resultados (optimizado para ComboBox)
     * @param query Texto a buscar (nombre o ID)
     * @param limit Número máximo de resultados
     * @return Lista limitada de productos
     */
    public List<Product> searchProducts(String query, int limit) {
        return DatabaseManager.callInConnection(() -> repo.searchProducts(query, limit));
    }

    public Product findById(Integer id) {
        return DatabaseManager.callInConnection(() -> repo.findById(id));
    }

    /**
     * Busca un producto por su código exacto
     * Útil para lectores de códigos de barras/QR
     * @param code Código del producto
     * @return Producto encontrado o null
     */
    public Product findByCode(String code) {
        return DatabaseManager.callInConnection(() -> repo.findByCode(code));
    }

    public Product createProduct(String name, String desc, String unit, double minStock) {
        return DatabaseManager.callInConnection(() ->
                repo.create(name, desc, unit, 0.0, minStock)
        );
    }

    public Product createProduct(String name, String desc, String unit, double currentStock, double minStock) {
        Product p = DatabaseManager.callInConnection(() -> repo.create(name, desc, unit, currentStock, minStock));
        try {
            Integer userId = Session.getCurrentUserId();
            String details = String.format("name=%s,unit=%s,current=%.2f,min=%.2f", name, unit, currentStock, minStock);

            auditService.logAction(userId, "create", "products", p.getInteger("id"), details);
        } catch (Exception e) {
            Logs.error(getClass(), e.getMessage(), e);
        }

        return p;
    }

    public List<Product> findLowStockProducts() {
        return DatabaseManager.callInConnection(repo::findLowStock);
    }

    public long countAll() {
        return DatabaseManager.callInConnection(repo::countAll);
    }

    public long countLowStock() {
        return DatabaseManager.callInConnection(repo::countLowStock);
    }

    public long countOutOfStock() {
        return DatabaseManager.callInConnection(repo::countOutOfStock);
    }

    public long countCreatedThisMonth() {
        return DatabaseManager.callInConnection(repo::countCreatedThisMonth);
    }

    public long countCreatedLastMonth() {
        return DatabaseManager.callInConnection(repo::countCreatedLastMonth);
    }

    public void save(@NotNull Product product) {
        // Normalizar código antes de guardar
        String code = product.getString("code");
        if (code != null) {
            code = code.trim();
            if (code.isEmpty()) {
                // Evitar guardar cadena vacía, dejar que el repositorio genere o mantener null
                product.set("code", null);
            } else if (!BarcodeGenerator.isValidCode(code)) {
                // Si no es válido, no lo forzamos; lo registramos y lo dejamos como estaba (o null)
                Logs.warn(getClass(), "Código de producto inválido: '{}' – se omitirá", code);
                product.set("code", null);
            } else {
                product.set("code", code);
            }
        }

        DatabaseManager.runInConnection(() -> {
            repo.save(product);
        });

        try {
            Integer userId = Session.getCurrentUserId();
            String details = String.format("id=%s,name=%s,unit=%s,current=%.2f,min=%.2f",
                    product.getInteger("id"), product.getString("name"), product.getString("unit"),
                    product.getDouble("current_stock") == null ? 0.0 : product.getDouble("current_stock"),
                    product.getDouble("minimum_stock") == null ? 0.0 : product.getDouble("minimum_stock"));
            auditService.logAction(userId, "update", "products", product.getInteger("id"), details);
        } catch (Exception ignored) {}
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