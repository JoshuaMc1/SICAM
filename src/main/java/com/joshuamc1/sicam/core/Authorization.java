package com.joshuamc1.sicam.core;

import com.joshuamc1.sicam.repositories.AuthorizationRepository;

/** Control de acceso para servicios y pantallas. Siempre consulta el estado actual. */
public final class Authorization {
    private static final AuthorizationRepository repo = new AuthorizationRepository();
    private Authorization() { }
    public static boolean canAccess(String module, String action) {
        Integer id=Session.getCurrentUserId();
        if(id==null) return false;
        return DatabaseManager.callInConnection(() -> Session.isCurrentLeaseValid(id) && repo.hasPermission(id,module,action));
    }
    public static String currentRole() {
        Integer id=Session.getCurrentUserId();
        return id==null ? null : DatabaseManager.callInConnection(
            () -> Session.isCurrentLeaseValid(id) ? repo.activeRoleName(id) : null);
    }
    public static boolean isRoot() { return "ROOT".equalsIgnoreCase(currentRole()); }
    public static boolean isAdminOrRoot() {
        String role=currentRole();
        return "ROOT".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role);
    }
    public static void require(String module,String action) {
        if(!canAccess(module,action)) throw new SecurityException("No cuenta con permisos suficientes: " + module + "." + action);
    }
    public static void requireAny(String module,String... actions) {
        for(String action: actions) if(canAccess(module,action)) return;
        throw new SecurityException("No cuenta con permisos suficientes para " + module);
    }
    public static void requireActiveSession() {
        if(currentRole()==null) throw new SecurityException("La sesión o el rol ya no está activo. Inicie sesión nuevamente.");
    }
    public static void requireAdminOrRoot() {
        if(!isAdminOrRoot()) throw new SecurityException("Esta función es exclusiva de ADMIN y ROOT");
    }
}
