package com.joshuamc1.sicam.core;

import com.joshuamc1.sicam.models.User;
import com.joshuamc1.sicam.repositories.SessionRepository;
import com.joshuamc1.sicam.services.UserService;
import org.javalite.activejdbc.Base;
import org.junit.jupiter.api.*;

import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

class SessionTest {
    private Connection connection;

    @BeforeEach void setup() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        sql("CREATE TABLE roles(id INTEGER PRIMARY KEY,nombre TEXT,status TEXT,activo INTEGER)");
        sql("CREATE TABLE users(id INTEGER PRIMARY KEY,username TEXT,name TEXT,password TEXT,rol_id INTEGER,status TEXT,session_token TEXT,session_expires_at TEXT)");
        sql("CREATE TABLE audit(id INTEGER PRIMARY KEY,user_id INTEGER,action TEXT,table_name TEXT,record_id INTEGER,details TEXT)");
        sql("INSERT INTO roles VALUES(1,'ROOT','active',1)");
        sql("INSERT INTO users(id,username,name,rol_id,status) VALUES(1,'root','Original',1,'active')");
        Base.attach(connection);
        User user = User.findById(1);
        user.setPassword("correct-password");
        user.saveIt();
    }

    @AfterEach void close() throws Exception {
        Session.setCurrentUser(null);
        Base.detach();
        connection.close();
    }

    private void sql(String query) throws Exception {
        try (Statement s = connection.createStatement()) { s.execute(query); }
    }

    @Test void loginReservesAndLogoutReleasesAccount() {
        UserService users = new UserService();
        assertNotNull(users.login("root", "correct-password"));
        assertTrue(Authorization.isRoot());
        assertThrows(SecurityException.class, () -> users.login("root", "correct-password"));
        assertTrue(Authorization.isRoot());
        Session.setCurrentUser(null);
        assertNotNull(users.login("root", "correct-password"));
    }

    @Test void anotherInstanceIsRejectedWithoutChangingItsReservation() {
        SessionRepository repo = new SessionRepository(connection);
        assertTrue(repo.acquire(1, "another-instance"));
        assertThrows(SecurityException.class, () -> new UserService().login("root", "correct-password"));
        assertNull(Session.getCurrentUser());
        assertTrue(repo.isValid(1, "another-instance"));
    }

    @Test void invalidCredentialsNeverReserveAccount() {
        assertNull(new UserService().login("root", "incorrect-password"));
        assertNull(Session.getCurrentUser());
        assertTrue(new SessionRepository(connection).acquire(1, "another-instance"));
    }

    @Test void failedLoginAuditReleasesReservation() throws Exception {
        sql("CREATE TRIGGER fail_audit BEFORE INSERT ON audit BEGIN SELECT RAISE(ABORT,'simulated failure'); END");
        assertThrows(RuntimeException.class, () -> new UserService().login("root", "correct-password"));
        assertNull(Session.getCurrentUser());
        assertTrue(new SessionRepository(connection).acquire(1, "another-instance"));
    }

    @Test void expiredOrReplacedSessionCannotAuthorizeOrReleaseNewOwner() throws Exception {
        Session.setCurrentUser(User.findById(1));
        assertTrue(Authorization.canAccess("INVENTARIO", "READ"));
        sql("UPDATE users SET session_expires_at='2000-01-01 00:00:00' WHERE id=1");
        assertFalse(Authorization.canAccess("INVENTARIO", "READ"));
        assertThrows(SecurityException.class, Authorization::requireActiveSession);
        SessionRepository repo = new SessionRepository(connection);
        assertTrue(repo.acquire(1, "new-owner"));
        assertFalse(Authorization.isRoot());
        Session.setCurrentUser(null);
        assertTrue(repo.isValid(1, "new-owner"));
    }

    @Test void savingAndReloadingProfileKeepsReservation() {
        User user = new UserService().login("root", "correct-password");
        // El modelo se cargó antes de reservar: guardar perfil no debe borrar el token actual.
        user.set("name", "Updated");
        user.saveIt();
        Session.setCurrentUser(User.findById(1));
        assertTrue(Authorization.isRoot());
        assertThrows(SecurityException.class, () -> new UserService().login("root", "correct-password"));
    }
}
