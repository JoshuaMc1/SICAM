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
}
