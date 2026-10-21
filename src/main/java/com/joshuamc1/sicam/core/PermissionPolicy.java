package com.joshuamc1.sicam.core;

import java.util.Map;
import java.util.Set;

/** Catálogo de acciones implementadas y límites que no se pueden delegar. */
public final class PermissionPolicy {
    private PermissionPolicy() { }
    private static final Map<String, Set<String>> ACTIONS = Map.ofEntries(
        Map.entry("DASHBOARD", Set.of("READ")),
        Map.entry("INVENTARIO", Set.of("CREATE", "READ", "UPDATE", "DELETE", "RESTORE")),
        Map.entry("INGRESOS", Set.of("CREATE", "READ", "DELETE", "RESTORE")),
        Map.entry("EGRESOS", Set.of("CREATE", "READ", "DELETE", "RESTORE")),
        Map.entry("PRESTAMOS", Set.of("CREATE", "READ", "DELETE", "RESTORE")),
        Map.entry("PERSONAS", Set.of("CREATE", "READ", "UPDATE", "DELETE", "RESTORE")),
        Map.entry("USUARIOS", Set.of("CREATE", "READ", "UPDATE", "DELETE", "RESTORE", "CHANGE_ROLE")),
        Map.entry("ROLES", Set.of("CREATE", "READ", "UPDATE", "DELETE", "RESTORE", "ASSIGN_PERMISSIONS")),
        Map.entry("AUDITORIA", Set.of("READ", "EXPORT")),
        Map.entry("REPORTES", Set.of("READ", "GENERATE", "EXPORT")),
        Map.entry("SISTEMA", Set.of("VIEW_DELETED", "DESTRUCTIVE_OPERATIONS")),
        Map.entry("BACKUP", Set.of("CREATE", "READ", "DELETE")));

    public static boolean isSupported(String module, String action) {
        return ACTIONS.getOrDefault(module, Set.of()).contains(action);
    }
    public static boolean isActive(String userStatus, String roleStatus, boolean roleActive) {
        return "active".equals(userStatus) && "active".equals(roleStatus) && roleActive;
    }
    public static boolean isRootOnly(String module, String action) {
        return "SISTEMA".equals(module) && "DESTRUCTIVE_OPERATIONS".equals(action);
    }
    public static boolean canDelegate(String actorRole, String targetRole, String module, String action, boolean actorHasPermission) {
        if (!isSupported(module, action) || "ROOT".equalsIgnoreCase(targetRole)) return false;
        if (isRootOnly(module, action)) return false;
        return "ROOT".equalsIgnoreCase(actorRole) || actorHasPermission;
    }
}
