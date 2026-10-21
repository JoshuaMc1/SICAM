package com.joshuamc1.sicam.core;

import com.joshuamc1.sicam.config.DatabaseConfig;
import org.javalite.activejdbc.Base;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public class DatabaseManager {
    private static final ThreadLocal<Integer> CONNECTION_COUNTER = ThreadLocal.withInitial(() -> 0);

    public static void runInConnection(@NotNull Runnable runnable) {
        if (runnable == null) {
            throw new IllegalArgumentException("runnable no puede ser null");
        }

        int count = CONNECTION_COUNTER.get();
        boolean openedByThis = false;

        boolean hadConnection = Base.hasConnection();
        if (!hadConnection && count == 0) {
            DatabaseConfig.openConnection();
            openedByThis = true;
            // registrar apertura de conexión a INFO (útil pero no ruidoso)
            Logs.info(DatabaseManager.class, "Abierta conexión por DatabaseManager en hilo {}", Thread.currentThread().getName());
        }

        CONNECTION_COUNTER.set(count + 1);

        try {
            runnable.run();
        } finally {
            int current = CONNECTION_COUNTER.get() - 1;
            if (current <= 0) {
                CONNECTION_COUNTER.remove();
            } else {
                CONNECTION_COUNTER.set(current);
            }

            if (openedByThis) {
                DatabaseConfig.closeConnection();
                Logs.info(DatabaseManager.class, "Cerrada conexión por DatabaseManager en hilo {}", Thread.currentThread().getName());
            }
        }
    }

    public static <T> T callInConnection(@NotNull Supplier<T> supplier) {
        if (supplier == null) {
            throw new IllegalArgumentException("supplier no puede ser null");
        }

        int count = CONNECTION_COUNTER.get();
        boolean openedByThis = false;

        boolean hadConnection = Base.hasConnection();
        if (!hadConnection && count == 0) {
            DatabaseConfig.openConnection();
            openedByThis = true;
            Logs.info(DatabaseManager.class, "Abierta conexión por DatabaseManager en hilo {}", Thread.currentThread().getName());
        }

        CONNECTION_COUNTER.set(count + 1);

        try {
            return supplier.get();
        } finally {
            int current = CONNECTION_COUNTER.get() - 1;
            if (current <= 0) {
                CONNECTION_COUNTER.remove();
            } else {
                CONNECTION_COUNTER.set(current);
            }

            if (openedByThis) {
                DatabaseConfig.closeConnection();
                Logs.info(DatabaseManager.class, "Cerrada conexión por DatabaseManager en hilo {}", Thread.currentThread().getName());
            }
        }
    }
    /** Conserva la conexión y usa un savepoint si el llamador ya posee una transacción. */
    public static <T> T callInTransaction(Supplier<T> operation) {
        return callInConnection(() -> {
            java.sql.Connection connection = Base.connection();
            boolean owner;
            java.sql.Savepoint savepoint;
            try {
                owner = connection.getAutoCommit();
                if (owner) { connection.setAutoCommit(false); savepoint = null; }
                else savepoint = connection.setSavepoint();
            } catch (java.sql.SQLException ex) {
                throw new IllegalStateException("No se pudo iniciar la operación", ex);
            }
            Throwable failure = null;
            try {
                T result = operation.get();
                if (owner) connection.commit();
                else connection.releaseSavepoint(savepoint);
                return result;
            } catch (RuntimeException | Error | java.sql.SQLException ex) {
                failure = ex;
                try {
                    if (owner) connection.rollback();
                    else { connection.rollback(savepoint); connection.releaseSavepoint(savepoint); }
                } catch (java.sql.SQLException rollback) { ex.addSuppressed(rollback); }
                if (ex instanceof RuntimeException) throw (RuntimeException) ex;
                if (ex instanceof Error) throw (Error) ex;
                throw new IllegalStateException("No se pudo guardar la operación", ex);
            } finally {
                if (owner) try { connection.setAutoCommit(true); }
                catch (java.sql.SQLException cleanup) {
                    if (failure != null) failure.addSuppressed(cleanup);
                    else throw new IllegalStateException("No se pudo restablecer la conexión", cleanup);
                }
            }
        });
    }
}
