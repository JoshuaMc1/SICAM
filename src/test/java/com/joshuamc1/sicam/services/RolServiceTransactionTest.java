package com.joshuamc1.sicam.services;

import com.joshuamc1.sicam.core.Session;
import com.joshuamc1.sicam.models.User;
import org.javalite.activejdbc.Base;
import org.junit.jupiter.api.*;
import java.sql.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class RolServiceTransactionTest {
    private Connection connection;
    @BeforeEach public void setup() throws Exception {
        connection=DriverManager.getConnection("jdbc:sqlite::memory:");
        try(Statement s=connection.createStatement()) {
            s.execute("CREATE TABLE users(id INTEGER PRIMARY KEY,username TEXT,status TEXT,rol_id INTEGER,session_token TEXT,session_expires_at TEXT)");
            s.execute("INSERT INTO users(id,username,status,rol_id) VALUES(1,'root','active',1)");
            s.execute("CREATE TABLE roles(id INTEGER PRIMARY KEY,nombre TEXT,status TEXT,activo INTEGER)");
            s.execute("INSERT INTO roles VALUES(1,'ROOT','active',1),(2,'QA1_LECTOR','active',1)");
            s.execute("CREATE TABLE permisos(id INTEGER PRIMARY KEY,modulo TEXT,accion TEXT)");
            s.execute("INSERT INTO permisos VALUES(1,'DASHBOARD','READ')");
            s.execute("CREATE TABLE rol_permisos(rol_id INTEGER,permiso_id INTEGER)");
            s.execute("INSERT INTO rol_permisos VALUES(2,1)");
            s.execute("CREATE TABLE audit(id INTEGER PRIMARY KEY,user_id INTEGER,action TEXT,table_name TEXT,record_id INTEGER,details TEXT)");
        }
        Base.attach(connection); Session.setCurrentUser(User.findById(1));
    }
    @AfterEach public void cleanup() throws Exception {
        Session.setCurrentUser(null); Base.detach(); connection.close();
    }
    @Test public void assigningPermissionsRestoresAutoCommitForFollowingOperations() throws Exception {
        new RolService().updatePermissions(2,List.of());
        assertTrue(connection.getAutoCommit(),"Guardar permisos no debe dejar abierta una transacción");
        assertEquals(0L,((Number)Base.firstCell("SELECT COUNT(*) FROM rol_permisos")).longValue());
        assertEquals(1L,((Number)Base.firstCell("SELECT COUNT(*) FROM audit")).longValue());
    }
    @Test public void failedAuditRollsBackPermissionsAndRestoresAutoCommit() throws Exception {
        Base.exec("CREATE TRIGGER reject_audit BEFORE INSERT ON audit BEGIN SELECT RAISE(ABORT,'audit failure'); END");
        assertThrows(RuntimeException.class,()->new RolService().updatePermissions(2,List.of()));
        assertTrue(connection.getAutoCommit());
        assertEquals(1L,((Number)Base.firstCell("SELECT COUNT(*) FROM rol_permisos")).longValue());
    }
    @Test public void nestedPermissionChangeDoesNotCommitCallersTransaction() throws Exception {
        connection.setAutoCommit(false);
        new RolService().updatePermissions(2,List.of());
        assertFalse(connection.getAutoCommit());
        connection.rollback();
        assertEquals(1L,((Number)Base.firstCell("SELECT COUNT(*) FROM rol_permisos")).longValue());
        assertEquals(0L,((Number)Base.firstCell("SELECT COUNT(*) FROM audit")).longValue());
    }
}
