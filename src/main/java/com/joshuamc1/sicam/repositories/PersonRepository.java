package com.joshuamc1.sicam.repositories;

import com.joshuamc1.sicam.models.Person;
import org.javalite.activejdbc.LazyList;

import java.util.List;

public class PersonRepository {

    // ===== MÉTODOS DE BÚSQUEDA CON FILTRADO DE ESTADO =====

    public Person findById(Integer id) {
        return Person.findById(id);
    }

    public Person findByDni(String dni) {
        // Solo personas activas
        return Person.findFirst("dni = ? AND status = ?", dni, Person.STATUS_ACTIVE);
    }

    /**
     * Busca persona por DNI incluyendo inactivas/eliminadas (para validaciones)
     */
    public Person findByDniIncludingDeleted(String dni) {
        return Person.findFirst("dni = ?", dni);
    }

    /**
     * Lista todas las personas activas (excluye eliminadas)
     */
    public List<Person> findAll() {
        return Person.where("status != ?", Person.STATUS_DELETED)
            .orderBy("id DESC");
    }

    /**
     * Lista todas las personas incluyendo eliminadas
     */
    public List<Person> findAllIncludingDeleted() {
        return Person.findAll().orderBy("id DESC");
    }

    /**
     * Lista solo personas eliminadas
     */
    public List<Person> findDeleted() {
        return Person.where("status = ?", Person.STATUS_DELETED)
            .orderBy("id DESC");
    }

    /**
     * Lista solo personas activas
     */
    public List<Person> findActive() {
        return Person.where("status = ?", Person.STATUS_ACTIVE)
            .orderBy("id DESC");
    }

    /**
     * Lista solo personas inactivas
     */
    public List<Person> findInactive() {
        return Person.where("status = ?", Person.STATUS_INACTIVE)
            .orderBy("id DESC");
    }

    public Person create(String dni, String firstName, String lastName, String phone, String internalCode) {
        Person person = new Person();
        person.set("dni", dni);
        person.set("first_name", firstName);
        person.set("last_name", lastName);
        person.set("phone", phone);
        person.set("internal_code", internalCode);
        person.set("status", Person.STATUS_ACTIVE); // Nuevo campo
        person.saveIt();
        return person;
    }

    public boolean update(Integer id, String dni, String firstName, String lastName, String phone, String internalCode) {
        Person p = Person.findById(id);
        if (p == null) return false;
        p.set("dni", dni);
        p.set("first_name", firstName);
        p.set("last_name", lastName);
        p.set("phone", phone);
        p.set("internal_code", internalCode);
        return p.saveIt();
    }

    /**
     * Cuenta personas activas (excluye eliminadas)
     */
    public long count() {
        Long c = Person.count("status != ?", Person.STATUS_DELETED);
        return c == null ? 0L : c;
    }

    /**
     * Lista paginada de personas (excluye eliminadas por defecto)
     */
    public List<Person> listPaged(int offset, int limit, String search) {
        if (search != null && !search.isBlank()) {
            LazyList<Person> list = Person.where(
                "(dni LIKE ? OR first_name LIKE ? OR last_name LIKE ? OR internal_code LIKE ?) AND status != ?",
                "%" + search + "%", "%" + search + "%", "%" + search + "%", "%" + search + "%",
                Person.STATUS_DELETED
            ).orderBy("id DESC").offset(offset).limit(limit);
            return list;
        } else {
            return Person.where("status != ?", Person.STATUS_DELETED)
                .orderBy("id DESC")
                .offset(offset)
                .limit(limit);
        }
    }

    /**
     * Busca personas por DNI, nombre o apellido con límite de resultados
     * Optimizado para búsqueda en ComboBox con muchas personas
     * SOLO devuelve personas activas
     */
    public List<Person> searchPeople(String query, int limit) {
        if (query == null || query.trim().isEmpty()) {
            // Sin búsqueda: devolver las primeras N personas activas ordenadas por nombre
            return Person.where("status = ?", Person.STATUS_ACTIVE)
                .orderBy("first_name, last_name")
                .limit(limit);
        }

        String searchPattern = "%" + query.trim() + "%";

        // Buscar por DNI, nombre, apellido o código interno - SOLO activas
        return Person.where(
            "(dni LIKE ? OR first_name LIKE ? OR last_name LIKE ? OR internal_code LIKE ?) AND status = ?",
            searchPattern, searchPattern, searchPattern, searchPattern, Person.STATUS_ACTIVE
        )
        .orderBy("first_name, last_name")
        .limit(limit);
    }

    // ===== MÉTODOS DE SOFT DELETE Y RESTAURACIÓN =====

    /**
     * Realiza soft delete de la persona
     */
    public void softDelete(Person person, Integer deletedBy) {
        person.softDelete(deletedBy);
    }

    /**
     * Eliminación física permanente (solo ROOT)
     */
    public void hardDelete(Person person) {
        person.delete();
    }

    /**
     * Restaura una persona eliminada
     */
    public void restore(Person person) {
        person.restore();
    }

    /**
     * Cuenta personas eliminadas
     */
    public long countDeleted() {
        Long c = Person.count("status = ?", Person.STATUS_DELETED);
        return c == null ? 0L : c;
    }

    // ===== MÉTODOS LEGACY (mantener compatibilidad) =====

    /**
     * @deprecated Usar softDelete() o hardDelete() según el caso
     */
    @Deprecated
    public boolean delete(Integer id) {
        Person p = Person.findById(id);
        if (p == null) return false;
        return p.delete();
    }
}
