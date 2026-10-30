package com.joshuamc1.sicam.core;

import com.joshuamc1.sicam.config.MySQLConfigManager;
import com.joshuamc1.sicam.models.User;
import com.joshuamc1.sicam.repositories.SessionRepository;
import org.javalite.activejdbc.Base;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in: usa SICAM_TEST_MYSQL_URL/USER/PASSWORD y crea exclusivamente un esquema aislado. */
@EnabledIfSystemProperty(named = "sicam.test.mysql", matches = "true")
class SessionMySqlTest {
    @TempDir static Path directory;
    private Connection connection;
    private String schema, url;
    private Properties credentials;

    @BeforeEach void setup() throws Exception {
        System.setProperty("app.secure.dir", directory.toString());
        String secureDirectory = System.getenv("SICAM_TEST_SECURE_DIR");
        if (secureDirectory != null) Files.copy(Path.of(secureDirectory, "mysql.config"),
            directory.resolve("mysql.config"), StandardCopyOption.REPLACE_EXISTING);
        String configuredUrl = secureDirectory == null
            ? System.getenv().getOrDefault("SICAM_TEST_MYSQL_URL", "jdbc:mysql://localhost:3306/")
            : MySQLConfigManager.getJdbcUrl();
        URI server = URI.create(configuredUrl.substring("jdbc:".length()));
        if (server.getHost() == null || !"mysql".equals(server.getScheme()))
            throw new IllegalArgumentException("SICAM_TEST_MYSQL_URL debe ser una URL JDBC de MySQL.");
        credentials = new Properties();
        credentials.setProperty("user", secureDirectory == null
            ? System.getenv().getOrDefault("SICAM_TEST_MYSQL_USER", "root") : MySQLConfigManager.getUsername());
        credentials.setProperty("password", secureDirectory == null
            ? System.getenv().getOrDefault("SICAM_TEST_MYSQL_PASSWORD", "") : MySQLConfigManager.getPassword());
        credentials.setProperty("connectTimeout", "5000");
        credentials.setProperty("socketTimeout", "5000");
        schema = "sicam_session_test_" + UUID.randomUUID().toString().replace("-", "");
        url = "jdbc:mysql://" + server.getAuthority() + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        connection = DriverManager.getConnection(url, credentials);
        sql("CREATE DATABASE " + schema);
        connection.setCatalog(schema);
        sql("CREATE TABLE roles(id INT PRIMARY KEY,nombre VARCHAR(50),status VARCHAR(20),activo INT) ENGINE=InnoDB");
        try (var input = getClass().getResourceAsStream("/migrations/20251114112628_create_user_table.sql")) {
            sql(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        sql("INSERT INTO roles VALUES(1,'ROOT','active',1)");
        sql("INSERT INTO users(id,name,username,password,rol_id,status) VALUES(1,'Test','test','unused',1,'active')");
        MySQLConfigManager.saveConfig(server.getHost(), Integer.toString(server.getPort() == -1 ? 3306 : server.getPort()),
            schema, credentials.getProperty("user"), credentials.getProperty("password"));
        Base.attach(connection);
    }

    @AfterEach void cleanup() throws Exception {
        Session.setCurrentUser(null);
        if (Base.hasConnection()) Base.detach();
        if (connection != null) {
            try {
                if (schema != null && schema.matches("sicam_session_test_[a-f0-9]{32}")) sql("DROP DATABASE " + schema);
            } finally { connection.close(); }
        }
    }

    private void sql(String query) throws Exception {
        try (Statement s = connection.createStatement()) { s.execute(query); }
    }

    @Test void originalMigrationSupportsExactlyOneConcurrentWinner() throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try {
            Callable<Boolean> contender = () -> {
                try (Connection other = DriverManager.getConnection(url, credentials)) {
                    other.setCatalog(schema);
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    return new SessionRepository(other).acquire(1, Thread.currentThread().getName());
                }
            };
            Future<Boolean> first = workers.submit(contender), second = workers.submit(contender);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertNotEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            sql("UPDATE users SET session_expires_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=1");
            SessionRepository repo = new SessionRepository(connection);
            assertTrue(repo.acquire(1, "new-owner"));
            repo.release(1, "old-owner");
            assertTrue(repo.isValid(1, "new-owner"));
            assertTrue(repo.renew(1, "new-owner"));
        } finally { start.countDown(); workers.shutdownNow(); }
    }

    @Test void backgroundHeartbeatKeepsIdleAccountBeyondOriginalExpiry() throws Exception {
        Session.setCurrentUser(User.findById(1));
        Session.startHeartbeat();
        Thread.sleep(65_000); // Sin actividad de UI; supera la duración inicial de la reserva.
        assertTrue(Authorization.isRoot());
        assertFalse(new SessionRepository(connection).acquire(1, "second-instance"));
        Session.setCurrentUser(null);
        assertTrue(new SessionRepository(connection).acquire(1, "second-instance"));
    }
}
