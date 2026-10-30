package com.joshuamc1.sicam.repositories;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class SessionRepositoryTest {
    @TempDir Path directory;
    private String url;
    private Connection connection;
    private SessionRepository sessions;

    @BeforeEach void setup() throws Exception {
        url = "jdbc:sqlite:" + directory.resolve("sessions.db");
        connection = DriverManager.getConnection(url);
        sessions = new SessionRepository(connection);
        sql("CREATE TABLE roles(id INTEGER PRIMARY KEY,status TEXT,activo INTEGER)");
        sql("CREATE TABLE users(id INTEGER PRIMARY KEY,rol_id INTEGER,status TEXT,session_token TEXT,session_expires_at TEXT)");
        sql("INSERT INTO roles VALUES(1,'active',1)");
        sql("INSERT INTO users(id,rol_id,status) VALUES(1,1,'active'),(2,1,'active')");
    }

    @AfterEach void close() throws Exception { connection.close(); }

    private void sql(String query) throws Exception {
        try (Statement s = connection.createStatement()) { s.execute(query); }
    }

    @Test void rejectsAnotherInstanceButAllowsAnotherAccount() {
        assertTrue(sessions.acquire(1, "first"));
        assertFalse(sessions.acquire(1, "second"));
        assertTrue(sessions.isValid(1, "first"));
        assertFalse(sessions.isValid(1, "second"));
        assertTrue(sessions.acquire(2, "second"));
    }

    @Test void logoutAllowsImmediateLoginAndOldLogoutCannotReleaseNewSession() {
        assertTrue(sessions.acquire(1, "old"));
        sessions.release(1, "old");
        assertTrue(sessions.acquire(1, "new"));
        sessions.release(1, "old");
        assertTrue(sessions.isValid(1, "new"));
        assertFalse(sessions.renew(1, "old"));
    }

    @Test void crashExpiresWithoutCleanupAndExpiredInstanceCannotComeBack() throws Exception {
        assertTrue(sessions.acquire(1, "crashed"));
        // Avanzar el estado de la reserva sin esperar un minuto en la prueba.
        sql("UPDATE users SET session_expires_at='2000-01-01 00:00:00' WHERE id=1");
        assertFalse(sessions.isValid(1, "crashed"));
        assertFalse(sessions.renew(1, "crashed"));
        assertTrue(sessions.acquire(1, "recovered"));
        sessions.release(1, "crashed");
        assertFalse(sessions.renew(1, "crashed"));
        assertTrue(sessions.isValid(1, "recovered"));
    }

    @Test void heartbeatRenewsWithoutAnyUserActivityAndUsesDatabaseClock() throws Exception {
        assertTrue(sessions.acquire(1, "idle"));
        sql("UPDATE users SET session_expires_at=strftime('%Y-%m-%d %H:%M:%f','now','+5 seconds') WHERE id=1");
        assertTrue(sessions.renew(1, "idle"));
        try (Statement s = connection.createStatement(); ResultSet rs = s.executeQuery(
            "SELECT session_expires_at>strftime('%Y-%m-%d %H:%M:%f','now','+50 seconds') FROM users WHERE id=1")) {
            assertTrue(rs.next());
            assertTrue(rs.getBoolean(1));
        }
        assertFalse(sessions.acquire(1, "other"));
    }

    @Test void inactiveAccountOrRoleCannotAcquireOrRenew() throws Exception {
        sql("UPDATE users SET status='inactive' WHERE id=1");
        assertFalse(sessions.acquire(1, "blocked"));
        sql("UPDATE users SET status='active' WHERE id=1");
        assertTrue(sessions.acquire(1, "active"));
        sql("UPDATE roles SET activo=0 WHERE id=1");
        assertFalse(sessions.isValid(1, "active"));
        assertFalse(sessions.renew(1, "active"));
        assertFalse(sessions.acquire(2, "blocked"));
    }

    @Test void simultaneousProcessesHaveExactlyOneWinner() throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try {
            Callable<Boolean> contender = () -> {
                try (Connection other = DriverManager.getConnection(url)) {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    return new SessionRepository(other).acquire(1, Thread.currentThread().getName());
                }
            };
            Future<Boolean> first = workers.submit(contender), second = workers.submit(contender);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertNotEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        } finally { start.countDown(); workers.shutdownNow(); }
    }
}
