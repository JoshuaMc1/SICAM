package com.joshuamc1.sicam.core;

import com.joshuamc1.sicam.models.User;
import org.jetbrains.annotations.Nullable;
import com.joshuamc1.sicam.config.DatabaseConfig;
import com.joshuamc1.sicam.repositories.SessionRepository;
import org.javalite.activejdbc.Base;

import java.sql.Connection;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class Session {
    private static volatile User currentUser = null;
    private static final java.util.concurrent.atomic.AtomicLong revision = new java.util.concurrent.atomic.AtomicLong();
    public static final int RENEW_SECONDS = 15;
    private static volatile Lease lease;
    private static ScheduledExecutorService heartbeat;
    private static ScheduledFuture<?> renewal;
    private static Runnable onSessionLost;

    private static final class Lease {
        final int userId;
        final String token = UUID.randomUUID().toString();
        volatile boolean lost;
        Lease(int userId) { this.userId = userId; }
    }

    private Session() {}

    public static synchronized void beginAuthenticated(User user) {
        if (lease != null) throw new SecurityException("Ya existe una sesión en esta instancia. Cierre sesión antes de iniciar otra.");
        setCurrentUser(user);
    }

    public static synchronized void setCurrentUser(User user) {
        if (user == null) {
            clear();
            return;
        }
        Integer userId = user.getInteger("id");
        if (userId == null) throw new IllegalArgumentException("El usuario no tiene ID");
        if (lease == null || lease.userId != userId) {
            if (lease != null) clear();
            Lease candidate = new Lease(userId);
            boolean acquired = DatabaseManager.callInConnection(
                () -> new SessionRepository().acquire(userId, candidate.token));
            if (!acquired) throw new SecurityException("Esta cuenta ya tiene una sesión activa o dejó de estar disponible. "
                + "Cierre la otra sesión. Si el programa se cerró inesperadamente, espere hasta un minuto e intente nuevamente.");
            lease = candidate;
        }
        if (lease.lost) throw new SecurityException("La sesión dejó de estar vigente. Inicie sesión nuevamente.");
        if (currentUser != user) {
            revision.incrementAndGet();
            currentUser = user;
        }
    }

    private static void clear() {
        Lease previous = lease;
        lease = null;
        if (currentUser != null) revision.incrementAndGet();
        currentUser = null;
        onSessionLost = null;
        if (renewal != null) { renewal.cancel(false); renewal = null; }
        if (previous == null) return;
        try {
            if (Base.hasConnection() && "SQLite".equalsIgnoreCase(Base.connection().getMetaData().getDatabaseProductName()))
                new SessionRepository().release(previous.userId, previous.token);
            else try (Connection connection = DatabaseConfig.openSessionConnection()) {
                new SessionRepository(connection).release(previous.userId, previous.token);
            }
        } catch (Exception e) {
            // La caducidad recupera la cuenta aunque no haya red durante el cierre.
            Logs.warn(Session.class, "No se pudo liberar la sesión; la reserva caducará automáticamente.");
        }
    }

    /** Se comprueba en BD antes de autorizar, sin confiar en el temporizador de la UI. */
    public static boolean isCurrentLeaseValid(Integer expectedUserId) {
        Lease current = lease;
        return current != null && expectedUserId != null && current.userId == expectedUserId && !current.lost
            && new SessionRepository().isValid(current.userId, current.token)
            && lease == current;
    }

    /** No mide actividad del usuario: renueva también durante diálogos e inactividad. */
    public static synchronized void startHeartbeat() {
        if (lease == null || renewal != null) return;
        if (heartbeat == null) heartbeat = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "sicam-session-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        Lease owner = lease;
        renewal = heartbeat.scheduleWithFixedDelay(() -> renew(owner), RENEW_SECONDS, RENEW_SECONDS, TimeUnit.SECONDS);
    }

    public static synchronized void setOnSessionLost(Runnable handler) {
        onSessionLost = handler;
        if (lease != null && lease.lost && handler != null) handler.run();
    }

    public static boolean hasLostLease() {
        Lease current = lease;
        return current != null && current.lost;
    }

    private static void renew(Lease owner) {
        if (lease != owner || owner.lost) return;
        try (Connection connection = DatabaseConfig.openSessionConnection()) {
            boolean renewed = new SessionRepository(connection).renew(owner.userId, owner.token);
            Runnable handler = null;
            synchronized (Session.class) {
                if (lease != owner) return;
                if (!renewed) { owner.lost = true; handler = onSessionLost; }
            }
            if (handler != null) handler.run();
        } catch (Exception e) {
            // Un fallo transitorio no cierra la ventana; la BD sigue rechazando operaciones sin reserva vigente.
            Logs.warn(Session.class, "No se pudo renovar la sesión; se reintentará automáticamente.");
        }
    }

    public static long getRevision() {
        return revision.get();
    }

    public static User getCurrentUser() {
        return currentUser;
    }

    public static @Nullable Integer getCurrentUserId() {
        User user = currentUser;
        return user == null ? null : user.getInteger("id");
    }

    public static @Nullable String getCurrentUserRole() {
        User user = currentUser;
        return user == null ? null : user.getRolNombre();
    }

    public static  String getCurrentUsername() {
        User user = currentUser;
        return user == null ? "Guest" : user.getString("username");
    }
}

