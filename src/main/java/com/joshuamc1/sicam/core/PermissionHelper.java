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
        return Authorization.canAccess(modulo, accion);
    }

    /**
     * Configura un botón según los permisos del usuario
     * Si no tiene permiso, lo deshabilita y agrega un tooltip explicativo
     */
    public static void configureButton(@NotNull Button btn, String modulo, String accion) {
        configureVisibleAction(btn, canAccess(modulo,accion));
    }

    /**
     * Oculta un nodo si el usuario no tiene el permiso especificado
     */
    public static void hideIfNoPermission(@NotNull Node node, String modulo, String accion) {
        boolean allowed=canAccess(modulo,accion);
        node.setVisible(allowed); node.setManaged(allowed);
    }

    /**
     * Deshabilita un nodo si el usuario no tiene el permiso especificado
     */
    public static void disableIfNoPermission(@NotNull Node node, String modulo, String accion) {
        configureVisibleAction(node, canAccess(modulo,accion));
    }

    public static javafx.scene.control.Label permissionWarning() {
        javafx.scene.control.Label label = new javafx.scene.control.Label("No cuenta con permisos suficientes");
        label.setStyle("-fx-text-fill: #D32F2F; -fx-font-weight: bold;");
        label.setWrapText(true);
        label.setMaxWidth(240);
        return label;
    }

    /** Mantiene la acción visible, bloqueada y con un aviso rojo junto a sus controles. */
    public static void configureVisibleAction(Node node, boolean allowed) {
        if (node == null) return;
        node.setDisable(!allowed);
        node.getProperties().put("permission-denied", !allowed);
        if (node.getParent() instanceof javafx.scene.layout.Pane) {
            javafx.scene.layout.Pane parent = (javafx.scene.layout.Pane) node.getParent();
            Node warning = parent.getChildren().stream()
                .filter(child -> Boolean.TRUE.equals(child.getProperties().get("permission-warning"))).findFirst().orElse(null);
            boolean denied = parent.getChildren().stream()
                .anyMatch(child -> Boolean.TRUE.equals(child.getProperties().get("permission-denied")));
            if (warning == null && denied) {
                warning = permissionWarning(); warning.getProperties().put("permission-warning", true);
                parent.getChildren().add(warning);
            }
            if (warning != null) { warning.setVisible(denied); warning.setManaged(denied); }
        }
    }

    /**
     * Verifica si el usuario es ROOT
     */
    public static boolean isRoot() {
        return Authorization.isRoot();
    }

    /**
     * Verifica si el usuario es ADMIN o ROOT
     */
    public static boolean isAdminOrRoot() {
        return Authorization.isAdminOrRoot();
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

        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.WARNING);
        alert.setTitle("Acceso Denegado");
        alert.setHeaderText(null);
        javafx.scene.control.Label warning = permissionWarning();
        warning.setText("No cuenta con permisos suficientes para " + getAccionDescripcion(accion) + " en " + modulo + ".");
        alert.getDialogPane().setContent(warning);
        alert.showAndWait();
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

