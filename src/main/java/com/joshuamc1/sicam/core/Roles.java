package com.joshuamc1.sicam.core;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Gestión de roles del sistema
 */
public class Roles {

    public enum Role {
        ROOT("root", "Root (Acceso Total)"),
        ADMIN("admin", "Administrador"),
        OPERATOR("operator", "Operador"),
        AUDITOR("auditor", "Auditor"),
        GUEST("guest", "Invitado");

        private final String code;
        private final String displayName;

        Role(String code, String displayName) {
            this.code = code;
            this.displayName = displayName;
        }

        public String getCode() {
            return code;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static Role fromCode(String code) {
            for (Role role : values()) {
                if (role.code.equals(code)) {
                    return role;
                }
            }
            return null;
        }
    }

    /**
     * Get the full name of a role based on its identifier.
     */
    @Contract(pure = true)
    public static @NotNull String getRoleFullName(@NotNull String role) {
        Role r = Role.fromCode(role);
        return r != null ? r.getDisplayName() : "Desconocido";
    }

    /**
     * Obtiene todos los roles disponibles (excluyendo ROOT por seguridad)
     */
    public static List<Role> getAssignableRoles() {
        return Arrays.stream(Role.values())
                .filter(role -> role != Role.ROOT)
                .collect(Collectors.toList());
    }

    /**
     * Obtiene todos los roles disponibles
     */
    public static List<Role> getAllRoles() {
        return Arrays.asList(Role.values());
    }

    /**
     * Verifica si un rol es ROOT
     */
    public static boolean isRoot(String roleCode) {
        return Role.ROOT.getCode().equals(roleCode);
    }

    /**
     * Verifica si un rol es ADMIN
     */
    public static boolean isAdmin(String roleCode) {
        return Role.ADMIN.getCode().equals(roleCode);
    }
}
