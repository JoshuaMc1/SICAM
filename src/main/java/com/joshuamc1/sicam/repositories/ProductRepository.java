package com.joshuamc1.sicam.repositories;

import com.joshuamc1.sicam.core.BarcodeGenerator;
import com.joshuamc1.sicam.models.Product;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class ProductRepository {

    // ===== MÉTODOS DE BÚSQUEDA CON FILTRADO DE ESTADO =====

    /**
     * Busca todos los productos activos (excluye eliminados por defecto)
     */
    public List<Product> findAll() {
        return Product.where("status != ?", Product.STATUS_DELETED)
            .orderBy("id DESC");
    }

    /**
     * Busca todos los productos incluyendo eliminados
     */
    public List<Product> findAllIncludingDeleted() {
        return Product.findAll().orderBy("id DESC");
    }

    /**
     * Busca solo productos eliminados
     */
    public List<Product> findDeleted() {
        return Product.where("status = ?", Product.STATUS_DELETED)
            .orderBy("id DESC");
    }

    /**
     * Busca solo productos activos
     */
    public List<Product> findActive() {
        return Product.where("status = ?", Product.STATUS_ACTIVE)
            .orderBy("id DESC");
    }

    /**
     * Busca solo productos inactivos
     */
    public List<Product> findInactive() {
        return Product.where("status = ?", Product.STATUS_INACTIVE)
            .orderBy("id DESC");
    }

    /**
     * Busca productos por nombre, ID o código con límite de resultados
     * Optimizado para búsqueda en ComboBox con muchos productos
     * Compatible con lectores de códigos de barras/QR
     * SOLO devuelve productos activos
     */
    public List<Product> searchProducts(String query, int limit) {
        if (query == null || query.trim().isEmpty()) {
            // Sin búsqueda: devolver los primeros N productos activos ordenados por nombre
            return Product.where("status = ?", Product.STATUS_ACTIVE)
                .orderBy("name")
                .limit(limit);
        }

        String searchPattern = "%" + query.trim() + "%";

        // Buscar por código (exacto o parcial), nombre o ID - SOLO activos
        return Product.where(
            "(code LIKE ? OR name LIKE ? OR CAST(id AS CHAR) LIKE ?) AND status = ?",
            searchPattern, searchPattern, searchPattern, Product.STATUS_ACTIVE)
            .limit(limit)
            .orderBy("name");
    }

    public Product findById(Integer id) {
        return Product.findById(id);
    }

    /**
     * Busca un producto por su código exacto
     * Útil para lectores de códigos de barras/QR que escanean el código completo
     * SOLO devuelve si está activo
     */
    public Product findByCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }

        List<Product> products = Product.where(
            "code = ? AND status = ?",
            code.trim(),
            Product.STATUS_ACTIVE
        );
        return products.isEmpty() ? null : products.get(0);
    }

    public Product create(String name, String description, String unit, double currentStock, double minimumStock) {
        Product product = new Product();
        product.set("name", name);
        product.set("description", description);
        product.set("unit", unit);
        product.set("current_stock", currentStock);
        product.set("minimum_stock", minimumStock);
        product.set("status", Product.STATUS_ACTIVE); // Nuevo campo

        // Generar código único por defecto si no se define desde la UI/servicio
        String code = generateUniqueCode();
        product.set("code", code);

        product.saveIt();
        return product;
    }

    /**
     * Verifica si ya existe un producto (cualquier estado) con el código dado
     */
    private boolean codeExists(String code) {
        if (code == null) return false;
        Long c = Product.count("code = ?", code);
        return c != null && c > 0;
    }

    /**
     * Genera un código único intentando múltiples veces para evitar colisiones
     */
    private @NotNull String generateUniqueCode() {
        // Intentos razonables para evitar bucle infinito en escenarios extremos
        for (int i = 0; i < 25; i++) {
            String candidate = BarcodeGenerator.generateRandomCode();
            if (!codeExists(candidate)) {
                return candidate;
            }
        }
        // Fallback: anexar sufijo aleatorio corto si hubo muchas colisiones
        for (int i = 0; i < 25; i++) {
            String candidate = BarcodeGenerator.generateRandomCode() + "-" + ThreadLocalRandom.current().nextInt(100, 999);
            if (!codeExists(candidate)) {
                return candidate;
            }
        }
        // Como último recurso, devolver un código aleatorio claro (la BD garantizará UNIQUE y lanzará error si colisiona)
        return BarcodeGenerator.generateRandomCode();
    }

    /**
     * Busca productos con stock bajo (SOLO activos)
     */
    public List<Product> findLowStock() {
        return Product.where(
            "current_stock <= minimum_stock AND status = ?",
            Product.STATUS_ACTIVE
        );
    }

    public void save(@NotNull Product product) {
        product.saveIt();
    }

    // ===== MÉTODOS DE SOFT DELETE Y RESTAURACIÓN =====

    /**
     * Realiza soft delete del producto
     */
    public void softDelete(@NotNull Product product, Integer deletedBy) {
        product.softDelete(deletedBy);
    }

    /**
     * Eliminación física permanente (solo ROOT)
     */
    public void hardDelete(@NotNull Product product) {
        product.delete();
    }

    /**
     * Restaura un producto eliminado
     */
    public void restore(@NotNull Product product) {
        product.restore();
    }

    // ===== MÉTODOS DE CONTEO =====

    /**
     * Cuenta todos los productos activos (excluye eliminados)
     */
    public long countAll() {
        Long c = Product.count("status != ?", Product.STATUS_DELETED);
        return c == null ? 0L : c;
    }

    /**
     * Cuenta productos con stock bajo (SOLO activos)
     */
    public long countLowStock() {
        return (long) Product.where(
            "current_stock < minimum_stock AND status = ?",
            Product.STATUS_ACTIVE
        ).size();
    }

    /**
     * Cuenta productos sin stock (SOLO activos)
     */
    public long countOutOfStock() {
        Long c = Product.count(
            "current_stock = 0 AND status = ?",
            Product.STATUS_ACTIVE
        );
        return c == null ? 0L : c;
    }

    /**
     * Cuenta productos creados este mes (SOLO activos)
     */
    public long countCreatedThisMonth() {
        Long c = Product.count(
            "YEAR(created_at) = YEAR(CURDATE()) AND MONTH(created_at) = MONTH(CURDATE()) AND status = ?",
            Product.STATUS_ACTIVE
        );
        return c == null ? 0L : c;
    }

    /**
     * Cuenta productos creados el mes pasado (SOLO activos)
     */
    public long countCreatedLastMonth() {
        Long c = Product.count(
            "YEAR(created_at) = YEAR(DATE_SUB(CURDATE(), INTERVAL 1 MONTH)) AND MONTH(created_at) = MONTH(DATE_SUB(CURDATE(), INTERVAL 1 MONTH)) AND status = ?",
            Product.STATUS_ACTIVE
        );
        return c == null ? 0L : c;
    }

    /**
     * Cuenta productos eliminados
     */
    public long countDeleted() {
        Long c = Product.count("status = ?", Product.STATUS_DELETED);
        return c == null ? 0L : c;
    }

    // ===== MÉTODOS LEGACY (mantener compatibilidad) =====

    /**
     * @deprecated Usar softDelete() o hardDelete() según el caso
     */
    @Deprecated
    public void delete(@NotNull Product product) {
        product.delete();
    }

    /**
     * Actualiza (guarda) los cambios en un producto
     */
    public void update(@NotNull Product product) {
        product.saveIt();
    }
}
