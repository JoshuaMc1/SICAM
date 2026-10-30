package com.joshuamc1.sicam.repositories;

import org.junit.jupiter.api.*;
import java.sql.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

public class AuthorizationRepositoryTest {
    private Connection connection;
    private AuthorizationRepository auth;
    @BeforeEach public void setup() throws Exception {
        connection=DriverManager.getConnection("jdbc:sqlite::memory:");
        auth=new AuthorizationRepository(connection);
        sql("PRAGMA foreign_keys=ON");
        sql("CREATE TABLE roles(id INTEGER PRIMARY KEY,nombre TEXT,status TEXT,activo INTEGER)");
        sql("CREATE TABLE users(id INTEGER PRIMARY KEY,rol_id INTEGER,status TEXT,username TEXT,session_token TEXT,session_expires_at TEXT)");
        sql("CREATE TABLE permisos(id INTEGER PRIMARY KEY,modulo TEXT,accion TEXT)");
        sql("CREATE TABLE rol_permisos(rol_id INTEGER,permiso_id INTEGER REFERENCES permisos(id) ON DELETE CASCADE)");
        sql("INSERT INTO roles VALUES(1,'ROOT','active',1),(2,'ADMIN','active',1),(3,'LECTOR','active',1)");
        sql("INSERT INTO users(id,rol_id,status) VALUES(1,1,'active'),(2,2,'active'),(3,3,'active')");
        sql("INSERT INTO permisos VALUES(1,'INVENTARIO','READ'),(2,'SISTEMA','DESTRUCTIVE_OPERATIONS'),(3,'ROLES','UPDATE'),(4,'PRESTAMOS','APPROVE'),(5,'EGRESOS','UPDATE'),(6,'INGRESOS','UPDATE'),(7,'PRESTAMOS','UPDATE'),(8,'CONFIGURACION','READ'),(9,'CONFIGURACION','UPDATE'),(10,'ROLES','ASSIGN_PERMISSIONS'),(11,'USUARIOS','CHANGE_ROLE'),(12,'REPORTES','READ'),(13,'REPORTES','GENERATE'),(14,'REPORTES','EXPORT'),(15,'AUDITORIA','READ'),(16,'AUDITORIA','EXPORT')");
        sql("INSERT INTO rol_permisos VALUES(2,1),(2,2),(2,3),(2,4),(2,5),(2,6),(2,7),(2,8),(2,9),(3,1),(3,12),(3,15)");
    }
    @AfterEach public void close() throws Exception { connection.close(); }
    private void sql(String statement) throws Exception { try(Statement s=connection.createStatement()){s.execute(statement);} }
    private int scalar(String statement) throws Exception { try(Statement s=connection.createStatement();ResultSet r=s.executeQuery(statement)){return r.next()?r.getInt(1):0;} }
    @Test public void rootKeepsSupportedPermissionsButCannotUseRemovedActions() throws Exception {
        assertTrue(auth.hasPermission(1,"ROLES","ASSIGN_PERMISSIONS"));
        assertTrue(auth.hasPermission(1,"SISTEMA","DESTRUCTIVE_OPERATIONS"));
        assertFalse(auth.hasPermission(1,"PRESTAMOS","APPROVE"));
        assertFalse(auth.hasPermission(1,"PRESTAMOS","UPDATE"));
        assertFalse(auth.hasPermission(1,"CONFIGURACION","READ"));
        sql("UPDATE roles SET activo=0 WHERE id=1");
        assertFalse(auth.hasPermission(1,"ROLES","READ"));
    }
    @Test public void destructiveOperationsRemainRootOnlyDespiteAccidentalGrant() {
        assertTrue(auth.hasPermission(2,"INVENTARIO","READ"));
        assertFalse(auth.hasPermission(2,"SISTEMA","DESTRUCTIVE_OPERATIONS"));
    }
    @Test public void roleAndUserRevocationTakeEffectWithoutNewSession() throws Exception {
        assertTrue(auth.hasPermission(3,"INVENTARIO","READ"));
        sql("UPDATE users SET status='inactive' WHERE id=3");
        assertFalse(auth.hasPermission(3,"INVENTARIO","READ"));
        sql("UPDATE users SET status='active' WHERE id=3");
        sql("UPDATE roles SET status='inactive' WHERE id=3");
        assertFalse(auth.hasPermission(3,"INVENTARIO","READ"));
        sql("UPDATE roles SET status='active',activo=0 WHERE id=3");
        assertFalse(auth.hasPermission(3,"INVENTARIO","READ"));
    }
    @Test public void changingRoleOrRemovingPermissionIsImmediate() throws Exception {
        assertTrue(auth.hasPermission(3,"INVENTARIO","READ"));
        sql("DELETE FROM rol_permisos WHERE rol_id=3 AND permiso_id=1");
        assertFalse(auth.hasPermission(3,"INVENTARIO","READ"));
        sql("UPDATE users SET rol_id=2 WHERE id=3");
        assertTrue(auth.hasPermission(3,"INVENTARIO","READ"));
        assertEquals("ADMIN",auth.activeRoleName(3));
    }
    @Test public void specificPrivilegesAreNotImpliedByReadOrUpdate() {
        assertTrue(auth.hasPermission(2,"ROLES","UPDATE"));
        assertFalse(auth.hasPermission(2,"ROLES","ASSIGN_PERMISSIONS"));
        assertFalse(auth.hasPermission(2,"USUARIOS","CHANGE_ROLE"));
        assertTrue(auth.hasPermission(3,"REPORTES","READ"));
        assertFalse(auth.hasPermission(3,"REPORTES","GENERATE"));
        assertFalse(auth.hasPermission(3,"REPORTES","EXPORT"));
        assertTrue(auth.hasPermission(3,"AUDITORIA","READ"));
        assertFalse(auth.hasPermission(3,"AUDITORIA","EXPORT"));
    }
    @Test public void missingOrDeletedIdentityFailsClosed() throws Exception {
        assertFalse(auth.hasPermission(null,"INVENTARIO","READ"));
        assertFalse(auth.hasPermission(999,"INVENTARIO","READ"));
        sql("UPDATE users SET status='deleted' WHERE id=3");
        assertNull(auth.activeRoleName(3));
        sql("UPDATE roles SET status='deleted' WHERE id=2");
        assertFalse(auth.hasPermission(2,"INVENTARIO","READ"));
    }
    @SuppressWarnings("unchecked")
    private java.util.List<String> splitMigration(String sql) throws Exception {
        var method = com.joshuamc1.sicam.config.MigrationRunner.class.getDeclaredMethod("splitSqlStatements", String.class);
        method.setAccessible(true);
        return (java.util.List<String>) method.invoke(null, sql);
    }
    @Test public void migrationParserIgnoresSemicolonsInsideCommentsAndQuotes() throws Exception {
        var statements = splitMigration("-- comentario; texto que no es SQL\nSELECT 'a; b';/* bloque; */SELECT \"c; d\";# comentario;\nSELECT `e; f`;SELECT 'it''s; fine';SELECT 1");
        assertEquals(java.util.List.of("SELECT 'a; b'", "SELECT \"c; d\"", "SELECT `e; f`", "SELECT 'it''s; fine'", "SELECT 1"), statements);
        assertEquals(java.util.List.of(), splitMigration("-- solo comentario; sin SQL"));
        for (String query : splitMigration("-- las FK; eliminan asignaciones.\nDELETE FROM rol_permisos WHERE rol_id=3;")) sql(query);
        assertEquals(0, scalar("SELECT COUNT(*) FROM rol_permisos WHERE rol_id=3"));
    }
    @Test public void servicesUseFreshDatabasePermissionsInsteadOfSessionRole() throws Exception {
        org.javalite.activejdbc.Base.attach(connection);
        try {
            com.joshuamc1.sicam.core.Session.setCurrentUser(com.joshuamc1.sicam.models.User.findById(3));
            assertFalse(com.joshuamc1.sicam.core.PermissionHelper.isAdminOrRoot());
            sql("UPDATE users SET rol_id=2 WHERE id=3");
            assertTrue(com.joshuamc1.sicam.core.PermissionHelper.isAdminOrRoot());
            sql("UPDATE users SET rol_id=3 WHERE id=3");
            assertThrows(SecurityException.class,()->new com.joshuamc1.sicam.services.ReportService().usersReport(false,false));
            sql("INSERT INTO rol_permisos VALUES(3,13),(3,14)");
            // Tener permisos de Reportes no permite leer el módulo Usuarios.
            assertThrows(SecurityException.class,()->new com.joshuamc1.sicam.services.ReportService().usersReport(false,false));
            sql("INSERT INTO permisos VALUES(17,'EGRESOS','READ')");
            sql("INSERT INTO rol_permisos VALUES(3,17)");
            assertThrows(SecurityException.class,()->new com.joshuamc1.sicam.services.ReportService().transactionReport("Historial","out",null,null,null,null,"invalidated"));
            sql("UPDATE users SET status='inactive' WHERE id=3");
            assertFalse(com.joshuamc1.sicam.core.PermissionHelper.canAccess("INVENTARIO","READ"));
        } finally {
            com.joshuamc1.sicam.core.Session.setCurrentUser(null);
            org.javalite.activejdbc.Base.detach();
        }
    }

    @Test public void freshSeedContainsOnlySupportedPermissionsAndMatchesRoleMatrix() throws Exception {
        try(Connection seed=DriverManager.getConnection("jdbc:sqlite::memory:"); Statement statement=seed.createStatement()) {
            statement.execute("CREATE TABLE roles(id INTEGER PRIMARY KEY,nombre TEXT,descripcion TEXT,activo INTEGER,status TEXT DEFAULT 'active')");
            statement.execute("CREATE TABLE permisos(id INTEGER PRIMARY KEY,modulo TEXT,accion TEXT,descripcion TEXT)");
            statement.execute("CREATE TABLE rol_permisos(rol_id INTEGER,permiso_id INTEGER,PRIMARY KEY(rol_id,permiso_id))");
            String initial=Files.readString(Path.of("src/main/resources/migrations/20251114114107_seed_roles_and_permissions.sql"));
            initial=initial.substring(0,initial.indexOf("-- Crear usuario SYSTEM"));
            String backup=Files.readString(Path.of("src/main/resources/migrations/20251120000002_add_backup_permissions.sql"));
            for(String query:splitMigration(initial+";"+backup)) statement.execute(query);
            try(ResultSet rows=statement.executeQuery("SELECT modulo,accion FROM permisos")) {
                int count=0;
                while(rows.next()) { assertTrue(com.joshuamc1.sicam.core.PermissionPolicy.isSupported(rows.getString(1),rows.getString(2))); count++; }
                assertEquals(45,count);
            }
            int[] expected={45,44,13,27,13,9};
            for(int role=1;role<=6;role++) try(ResultSet rows=statement.executeQuery("SELECT COUNT(*) FROM rol_permisos WHERE rol_id="+role)) {
                assertTrue(rows.next()); assertEquals(expected[role-1],rows.getInt(1),"Rol "+role);
            }
        }
    }

    @Test public void updateCannotRestoreDeletedUsersOrRoles() throws Exception {
        org.javalite.activejdbc.Base.attach(connection);
        try {
            com.joshuamc1.sicam.core.Session.setCurrentUser(com.joshuamc1.sicam.models.User.findById(2));
            sql("INSERT INTO permisos VALUES(18,'USUARIOS','UPDATE')"); sql("INSERT INTO rol_permisos VALUES(2,18)");
            sql("UPDATE users SET status='deleted' WHERE id=3");
            com.joshuamc1.sicam.models.User deleted=com.joshuamc1.sicam.models.User.findById(3);
            SecurityException userError=assertThrows(SecurityException.class,()->new com.joshuamc1.sicam.services.UserService().markAsActive(deleted));
            assertTrue(userError.getMessage().contains("RESTORE"));
            sql("UPDATE roles SET status='deleted',activo=0 WHERE id=3");
            SecurityException roleError=assertThrows(SecurityException.class,()->new com.joshuamc1.sicam.services.RolService().saveRole(3,"LECTOR","",true));
            assertTrue(roleError.getMessage().contains("RESTORE"));
        } finally {
            com.joshuamc1.sicam.core.Session.setCurrentUser(null);
            org.javalite.activejdbc.Base.detach();
        }
    }

}
