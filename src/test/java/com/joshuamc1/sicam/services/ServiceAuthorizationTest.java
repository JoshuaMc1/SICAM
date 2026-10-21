package com.joshuamc1.sicam.services;

import com.joshuamc1.sicam.core.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Sin sesión, debe rechazar antes de consultar una BD o tocar archivos. */
public class ServiceAuthorizationTest {
    @BeforeEach public void clearSession() { Session.setCurrentUser(null); }
    @Test public void productWritesRequireAuthorization() {
        ProductService products=new ProductService();
        assertThrows(SecurityException.class,()->products.createProduct("Producto","","unidad",0));
        assertThrows(SecurityException.class,()->products.save(null));
    }
    @Test public void roleAndUserChangesCannotBypassServiceChecks() {
        assertThrows(SecurityException.class,()->new RolService().saveRole(null,"NUEVO","",true));
        assertThrows(SecurityException.class,()->new RolService().updatePermissions(2,List.of(1)));
        assertThrows(SecurityException.class,()->new UserService().createUserFull("Nombre","usuario","clave","ADMIN"));
        assertThrows(SecurityException.class,()->new UserService().updateUserWithRoleId(2,"Nombre","usuario",2,null));
        assertThrows(SecurityException.class,()->new UserService().resetPassword(2,"clave",true));
    }
    @Test public void reportsAndAuditCannotBeReadWithoutSession() {
        assertThrows(SecurityException.class,()->new ReportService().usersReport(false,true));
        assertThrows(SecurityException.class,()->new ReportService().transactionReport("Historial",null,null,null,null,null,null));
        assertThrows(SecurityException.class,()->new AuditService().getAll());
    }
    @Test public void backupCannotReachFilesystemOrProcessWithoutPermission() {
        assertThrows(SecurityException.class,()->new BackupService().createBackup());
        assertThrows(SecurityException.class,()->new BackupService().deleteBackup(1));
        assertThrows(SecurityException.class,()->new BackupService().cleanOldBackups(2));
    }
    @Test public void specialFunctionsDenyMissingSession() {
        assertFalse(com.joshuamc1.sicam.core.PermissionHelper.isAdminOrRoot());
        assertThrows(SecurityException.class,()->com.joshuamc1.sicam.core.Authorization.requireAdminOrRoot());
        assertThrows(SecurityException.class,()->new InventoryImportService().importItems(List.of()));
    }
    @Test public void inventoryPreviewRejectsDuplicateCodesBeforeWriting(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        var csv = dir.resolve("inventory.csv");
        String header = "code,name,unit,current_stock,minimum_stock\n";
        java.nio.file.Files.writeString(csv, header + "8888660000000,Pico,unidad,10,2\n8888660000000,Machete,unidad,25,5\n");
        var importer = new InventoryImportService();
        var error = assertThrows(IllegalArgumentException.class, () -> importer.parseFileForPreview(csv.toFile()));
        assertTrue(error.getMessage().contains("8888660000000"));
        assertTrue(error.getMessage().contains("2 y 3"));
        java.nio.file.Files.writeString(csv, header + "8888660000000,Pico,unidad,10,2\n8888660000001,Machete,unidad,25,5\n");
        assertEquals(2, importer.readFile(csv.toFile()).size());
        java.nio.file.Files.writeString(csv, header + ",Pico,unidad,10,2\n,Machete,unidad,25,5\n");
        assertEquals(2, importer.readFile(csv.toFile()).size());
    }
}
