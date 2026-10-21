package com.joshuamc1.sicam.services;

import com.joshuamc1.sicam.core.DatabaseManager;
import com.joshuamc1.sicam.core.Session;
import com.joshuamc1.sicam.core.PermissionHelper;
import com.joshuamc1.sicam.models.Person;
import com.joshuamc1.sicam.repositories.PersonRepository;

import java.util.List;

public class PersonService {
    private final PersonRepository repo = new PersonRepository();
    private final AuditService auditService = new AuditService();

    public Person create(String dni, String firstName, String lastName, String phone, String internalCode) {
        // Validación de permisos en la capa de servicio
        if (!PermissionHelper.canAccess("PERSONAS", "CREATE")) {
            throw new IllegalStateException("Acceso denegado: no tiene permiso para crear personas");
        }
        return DatabaseManager.callInConnection(() -> {
            Person person = repo.create(dni, firstName, lastName, phone, internalCode);

            auditService.logAction(
                Session.getCurrentUserId(),
                "create_person",
                "people",
                person.getInteger("id"),
                String.format("person_id=%d,dni=%s,name=%s %s",
                    person.getInteger("id"), dni, firstName, lastName)
            );

            return person;
        });
    }

    public boolean update(Integer id, String dni, String firstName, String lastName, String phone, String internalCode) {
        // Validación de permisos
        if (!PermissionHelper.canAccess("PERSONAS", "UPDATE")) {
            throw new IllegalStateException("Acceso denegado: no tiene permiso para actualizar personas");
        }
        return DatabaseManager.callInConnection(() -> {
            boolean ok = repo.update(id, dni, firstName, lastName, phone, internalCode);
            if (ok) {
                auditService.logAction(
                    Session.getCurrentUserId(),
                    "update_person",
                    "people",
                    id,
                    String.format("person_id=%d,dni=%s", id, dni)
                );
            }
            return ok;
        });
    }

    @Deprecated
    public boolean delete(Integer id) {
        com.joshuamc1.sicam.core.Authorization.require("PERSONAS","DELETE");
        Person person=findById(id);
        if(person==null) return false;
        softDelete(person);
        return true;
    }

    public Person findById(Integer id) {
        com.joshuamc1.sicam.core.Authorization.require("PERSONAS","READ");
        return DatabaseManager.callInConnection(() -> repo.findById(id));
    }

    public Person findByDni(String dni) {
        com.joshuamc1.sicam.core.Authorization.require("PERSONAS","READ");
        return DatabaseManager.callInConnection(() -> repo.findByDni(dni));
    }

    public long count() {
        com.joshuamc1.sicam.core.Authorization.require("PERSONAS","READ");
        return DatabaseManager.callInConnection(repo::count);
    }

    public List<Person> listPaged(int offset, int limit, String search) {
        com.joshuamc1.sicam.core.Authorization.require("PERSONAS","READ");
        return DatabaseManager.callInConnection(() -> repo.listPaged(offset, limit, search));
    }

    public List<Person> findAll() {
        com.joshuamc1.sicam.core.Authorization.require("PERSONAS","READ");
        return DatabaseManager.callInConnection(repo::findAll);
    }

    /**
     * Busca personas por DNI o nombre con límite de resultados (optimizado para ComboBox)
     * @param query Texto a buscar (DNI, nombre o apellido)
     * @param limit Número máximo de resultados
     * @return Lista limitada de personas
     */
    public List<Person> searchPeople(String query, int limit) {
        com.joshuamc1.sicam.core.Authorization.require("PERSONAS","READ");
        return DatabaseManager.callInConnection(() -> repo.searchPeople(query, limit));
    }

    /**
     * Verifica si existe una persona con el DNI dado
     * @param dni el DNI a verificar
     * @return true si existe, false si no existe
     */
    public boolean existsByDni(String dni) {
        com.joshuamc1.sicam.core.Authorization.requireAny("PERSONAS","READ","CREATE","UPDATE");
        return DatabaseManager.callInConnection(() -> {
            Person person = repo.findByDni(dni);
            return person != null;
        });
    }

    /**
     * Obtiene el ID de una persona por su DNI
     * @param dni el DNI de la persona
     * @return el ID de la persona o null si no existe
     */
    public Integer getIdByDni(String dni) {
        com.joshuamc1.sicam.core.Authorization.requireAny("PERSONAS","READ","CREATE","UPDATE");
        return DatabaseManager.callInConnection(() -> {
            Person person = repo.findByDni(dni);
            return person != null ? person.getInteger("id") : null;
        });
    }

    // ===== MÉTODOS DE SOFT DELETE Y RESTAURACIÓN =====

    /**
     * Soft delete explícito de una persona
     */
    public void softDelete(Person person) {
        Integer currentUserId = Session.getCurrentUserId();
        Integer id = person.getInteger("id");

        if (!PermissionHelper.canAccess("PERSONAS", "DELETE")) {
            throw new SecurityException("No tiene permiso para eliminar personas");
        }

        DatabaseManager.runInConnection(() -> {
            repo.softDelete(person, currentUserId);
            auditService.logAction(
                currentUserId,
                "soft_delete",
                "people",
                id,
                String.format("person_id=%d,dni=%s", id, person.getString("dni"))
            );
        });
    }

    /**
     * Eliminación física permanente (solo ROOT)
     */
    public void hardDelete(Person person) {
        Integer currentUserId = Session.getCurrentUserId();
        Integer id = person.getInteger("id");

        if (!PermissionHelper.canAccess("SISTEMA", "DESTRUCTIVE_OPERATIONS")) {
            throw new SecurityException("Solo ROOT puede ejecutar eliminaciones permanentes");
        }

        DatabaseManager.runInConnection(() -> {
            validateBeforeHardDelete(person);
            repo.hardDelete(person);
            auditService.logAction(
                currentUserId,
                "hard_delete",
                "people",
                id,
                String.format("person_id=%d [PERMANENT]", id, person.getString("dni"))
            );
        });
    }

    /**
     * Restaura una persona eliminada lógicamente
     */
    public void restore(Person person) {
        Integer currentUserId = Session.getCurrentUserId();
        Integer id = person.getInteger("id");

        if (!PermissionHelper.canAccess("PERSONAS", "RESTORE")) {
            throw new SecurityException("No tiene permiso para restaurar personas");
        }

        if (!person.isDeleted()) {
            throw new IllegalStateException("La persona no está eliminada");
        }

        DatabaseManager.runInConnection(() -> {
            repo.restore(person);
            auditService.logAction(
                currentUserId,
                "restore",
                "people",
                id,
                String.format("person_id=%d,dni=%s", id, person.getString("dni"))
            );
        });
    }

    /**
     * Marca persona como inactiva
     */
    public void markAsInactive(Person person) {
        Integer currentUserId = Session.getCurrentUserId();
        Integer id = person.getInteger("id");

        if (!PermissionHelper.canAccess("PERSONAS", "UPDATE")) {
            throw new SecurityException("No tiene permiso para modificar personas");
        }

        DatabaseManager.runInConnection(() -> {
            person.markAsInactive();
            auditService.logAction(currentUserId, "mark_inactive", "people", id, "status=inactive");
        });
    }

    /**
     * Marca persona como activa
     */
    public void markAsActive(Person person) {
        if (person.isDeleted()) throw new SecurityException("Los eliminados deben recuperarse mediante RESTORE");
        Integer currentUserId = Session.getCurrentUserId();
        Integer id = person.getInteger("id");

        if (!PermissionHelper.canAccess("PERSONAS", "UPDATE")) {
            throw new SecurityException("No tiene permiso para modificar personas");
        }

        DatabaseManager.runInConnection(() -> {
            person.markAsActive();
            auditService.logAction(currentUserId, "mark_active", "people", id, "status=active");
        });
    }

    /**
     * Lista personas incluyendo eliminadas (requiere VIEW_DELETED)
     */
    public List<Person> findAllIncludingDeleted() {
        if (!PermissionHelper.canAccess("SISTEMA", "VIEW_DELETED")) {
            throw new SecurityException("No tiene permiso para ver personas eliminadas");
        }
        return DatabaseManager.callInConnection(repo::findAllIncludingDeleted);
    }

    /**
     * Lista solo eliminadas
     */
    public List<Person> findDeleted() {
        if (!PermissionHelper.canAccess("SISTEMA", "VIEW_DELETED")) {
            throw new SecurityException("No tiene permiso para ver personas eliminadas");
        }
        return DatabaseManager.callInConnection(repo::findDeleted);
    }

    public long countDeleted() {
        if (!PermissionHelper.canAccess("SISTEMA", "VIEW_DELETED")) {
            return 0L;
        }
        return DatabaseManager.callInConnection(repo::countDeleted);
    }

    private void validateBeforeHardDelete(Person person) {
        if (person.isActive()) {
            throw new IllegalStateException(
                "No se puede eliminar permanentemente una persona activa. " +
                "Primero debe marcarse como eliminada."
            );
        }
    }
}
