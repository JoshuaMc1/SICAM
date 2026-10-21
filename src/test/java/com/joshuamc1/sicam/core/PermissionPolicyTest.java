package com.joshuamc1.sicam.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PermissionPolicyTest {
    @Test public void delegatedPrivilegesCannotExceedTheActorOrAlterRoot() {
        assertFalse(PermissionPolicy.canDelegate("ADMIN","ADMIN","SISTEMA","DESTRUCTIVE_OPERATIONS",true));
        assertFalse(PermissionPolicy.canDelegate("ROOT","OPERADOR","SISTEMA","DESTRUCTIVE_OPERATIONS",true));
        assertFalse(PermissionPolicy.canDelegate("ROOT","ROOT","ROLES","READ",true));
        assertFalse(PermissionPolicy.canDelegate("ADMIN","OPERADOR","ROLES","ASSIGN_PERMISSIONS",false));
        assertTrue(PermissionPolicy.canDelegate("ADMIN","OPERADOR","INVENTARIO","READ",true));
        assertFalse(PermissionPolicy.canDelegate("ROOT","OPERADOR","PRESTAMOS","APPROVE",true));
    }
    @Test public void transactionsAllowCreationInvalidationAndRestoreButNeverEditing() {
        for(String module:new String[]{"INGRESOS","EGRESOS","PRESTAMOS"}) {
            assertTrue(PermissionPolicy.isSupported(module,"CREATE"));
            assertTrue(PermissionPolicy.isSupported(module,"DELETE"));
            assertTrue(PermissionPolicy.isSupported(module,"RESTORE"));
            assertFalse(PermissionPolicy.isSupported(module,"UPDATE"));
        }
        assertFalse(PermissionPolicy.isSupported("PRESTAMOS","APPROVE"));
    }
    @Test public void roleNamesAreCompatibleWithDatabaseAndCustomRoles() {
        assertEquals("Administrador",Roles.getRoleFullName("ADMIN"));
        assertEquals("Supervisor",Roles.getRoleFullName("SUPERVISOR"));
        assertEquals("Consultor",Roles.getRoleFullName("CONSULTOR"));
        assertEquals("Operador",Roles.getRoleFullName("operator"));
        assertEquals("ROL_PERSONALIZADO",Roles.getRoleFullName("ROL_PERSONALIZADO"));
    }
}
