package com.joshuamc1.sicam.core;

import com.joshuamc1.sicam.models.User;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import org.jetbrains.annotations.NotNull;

/**
 * Clase utilitaria para gestión de permisos en la UI
 */
public class PermissionHelper {

    /**
     * Verifica si el usuario actual tiene permiso para una acción en un módulo
     */
    public static boolean canAccess(String modulo, String accion) {
        User currentUser = Session.getCurrentUser();
        if (currentUser == null) {
            return false;
        }
        return currentUser.tienePermiso(modulo, accion);
    }

    /**
     * Configura un botón según los permisos del usuario
     * Si no tiene permiso, lo deshabilita y agrega un tooltip explicativo
     */
    public static void configureButton(@NotNull Button btn, String modulo, String accion) {
        if (!canAccess(modulo, accion)) {
            btn.setDisable(true);
            btn.setStyle("-fx-opacity: 0.5;");
            Tooltip tooltip = new Tooltip("No tienes permisos para " + getAccionDescripcion(accion));
            Tooltip.install(btn, tooltip);
        }
    }

    /**
     * Oculta un nodo si el usuario no tiene el permiso especificado
     */
    public static void hideIfNoPermission(@NotNull Node node, String modulo, String accion) {
        if (!canAccess(modulo, accion)) {
            node.setVisible(false);
            node.setManaged(false);
        }
    }

    /**
     * Deshabilita un nodo si el usuario no tiene el permiso especificado
     */
    public static void disableIfNoPermission(@NotNull Node node, String modulo, String accion) {
        if (!canAccess(modulo, accion)) {
            node.setDisable(true);
            if (node instanceof Button) {
                node.setStyle("-fx-opacity: 0.5;");
                Tooltip tooltip = new Tooltip("No tienes permisos para " + getAccionDescripcion(accion));
                Tooltip.install(node, tooltip);
            }
        }
    }

    /**
     * Verifica si el usuario es ROOT
     */
    public static boolean isRoot() {
        User currentUser = Session.getCurrentUser();
        return currentUser != null && currentUser.isRootRole();
    }

    /**
     * Verifica si el usuario es ADMIN o ROOT
     */
    public static boolean isAdminOrRoot() {
        User currentUser = Session.getCurrentUser();
        if (currentUser == null) return false;
        return currentUser.isRootRole() || currentUser.isAdmin();
    }

    /**
     * Verifica si el usuario tiene al menos uno de los permisos especificados
     */
    public static boolean hasAnyPermission(String modulo, String... acciones) {
        for (String accion : acciones) {
            if (canAccess(modulo, accion)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Verifica si el usuario tiene todos los permisos especificados
     */
    public static boolean hasAllPermissions(String modulo, String... acciones) {
        for (String accion : acciones) {
            if (!canAccess(modulo, accion)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Obtiene una descripción amigable de la acción
     */
    private static String getAccionDescripcion(String accion) {
        return switch (accion.toUpperCase()) {
            case "CREATE" -> "crear";
            case "READ" -> "ver";
            case "UPDATE" -> "editar";
            case "DELETE" -> "eliminar";
            case "APPROVE" -> "aprobar";
            case "GENERATE" -> "generar";
            case "EXPORT" -> "exportar";
            case "CHANGE_ROLE" -> "cambiar roles";
            case "ASSIGN_PERMISSIONS" -> "asignar permisos";
            default -> "realizar esta acción";
        };
    }

    /**
     * Muestra un mensaje de acceso denegado y registra el intento
     */
    public static void showAccessDenied(String modulo, String accion) {
        User currentUser = Session.getCurrentUser();
        String username = currentUser != null ? currentUser.getString("username") : "desconocido";

        Logs.warn(PermissionHelper.class,
            "Acceso denegado: usuario={}, módulo={}, acción={}",
            username, modulo, accion);

        Utils.showAlert("Acceso Denegado",
            "No tienes permisos para " + getAccionDescripcion(accion) + " en el módulo " + modulo + ".\n" +
            "Contacta al administrador si necesitas acceso.");
    }

    /**
     * Valida permiso y muestra mensaje si no lo tiene
     * Retorna true si tiene permiso, false si no
     */
    public static boolean validateAndShow(String modulo, String accion) {
        if (!canAccess(modulo, accion)) {
            showAccessDenied(modulo, accion);
            return false;
        }
        return true;
    }
}

