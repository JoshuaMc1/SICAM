package com.joshuamc1.sicam.repositories;

import com.joshuamc1.sicam.models.TransactionItem;
import org.javalite.activejdbc.LazyList;

import java.util.List;

/**
 * Repositorio para operaciones CRUD sobre transaction_items
 */
public class TransactionItemRepository {

    /**
     * Obtiene todos los items de una transacción específica
     * @param transactionId ID de la transacción
     * @return lista de items
     */
    public List<TransactionItem> findByTransactionId(Integer transactionId) {
        return TransactionItem.where("transaction_id = ?", transactionId).orderBy("id");
    }

    /**
     * Busca un item por su ID
     * @param id ID del item
     * @return TransactionItem encontrado o null
     */
    public TransactionItem findById(Integer id) {
        return TransactionItem.findById(id);
    }

    /**
     * Crea un nuevo item de transacción
     * @param item item a crear
     * @return true si se guardó correctamente
     */
    public boolean create(TransactionItem item) {
        return item.saveIt();
    }

    /**
     * Actualiza un item existente
     * @param item item a actualizar
     * @return true si se actualizó correctamente
     */
    public boolean update(TransactionItem item) {
        return item.saveIt();
    }

    /**
     * Elimina un item por su ID
     * @param id ID del item a eliminar
     * @return true si se eliminó correctamente
     */
    public boolean delete(Integer id) {
        TransactionItem item = findById(id);
        if (item != null) {
            return item.delete();
        }
        return false;
    }

    /**
     * Cuenta la cantidad de items de una transacción
     * @param transactionId ID de la transacción
     * @return cantidad de items
     */
    public long countByTransactionId(Integer transactionId) {
        return TransactionItem.count("transaction_id = ?", transactionId);
    }

    /**
     * Obtiene todos los items de un producto específico
     * Útil para auditoría y reportes
     * @param productId ID del producto
     * @return lista de items
     */
    public List<TransactionItem> findByProductId(Integer productId) {
        return TransactionItem.where("product_id = ?", productId).orderBy("created_at DESC");
    }

    /**
     * Elimina todos los items de una transacción
     * @param transactionId ID de la transacción
     * @return número de items eliminados
     */
    public int deleteByTransactionId(Integer transactionId) {
        return TransactionItem.delete("transaction_id = ?", transactionId);
    }

    /**
     * Obtiene todos los items (útil para reportes generales)
     * @return lista de todos los items
     */
    public LazyList<TransactionItem> findAll() {
        return TransactionItem.findAll();
    }
}

