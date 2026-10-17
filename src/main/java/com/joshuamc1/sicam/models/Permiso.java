package com.joshuamc1.sicam.models;

import org.javalite.activejdbc.Model;
import org.javalite.activejdbc.annotations.Table;

import java.util.List;

@Table("permisos")
public class Permiso extends Model {

    /**
     * Obtiene todos los roles que tienen este permiso
     */
    public List<Rol> getRoles() {
        String sql = "SELECT r.* FROM roles r " +
                    "INNER JOIN rol_permisos rp ON r.id = rp.rol_id " +
                    "WHERE rp.permiso_id = ?";
        return Rol.findBySQL(sql, getId());
    }

    /**
     * Obtiene el nombre completo del permiso (Módulo: Acción)
     */
    public String getNombreCompleto() {
        return getString("modulo") + ": " + getString("accion");
    }

    /**
     * Obtiene todos los permisos agrupados por módulo
     */
    public static java.util.Map<String, List<Permiso>> agruparPorModulo() {
        List<Permiso> permisos = Permiso.findAll().orderBy("modulo, accion");
        java.util.Map<String, List<Permiso>> agrupados = new java.util.HashMap<>();

        for (Permiso permiso : permisos) {
            String modulo = permiso.getString("modulo");
            if (!agrupados.containsKey(modulo)) {
                agrupados.put(modulo, new java.util.ArrayList<>());
            }
            agrupados.get(modulo).add(permiso);
        }

        return agrupados;
    }

    /**
     * Busca un permiso por módulo y acción
     */
    public static Permiso buscarPorModuloYAccion(String modulo, String accion) {
        return Permiso.findFirst("modulo = ? AND accion = ?", modulo, accion);
    }

    /**
     * Obtiene todos los permisos de un módulo específico
     */
    public static List<Permiso> obtenerPorModulo(String modulo) {
        return Permiso.where("modulo = ?", modulo).orderBy("accion");
    }
}

