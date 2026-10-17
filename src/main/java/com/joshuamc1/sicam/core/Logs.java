package com.joshuamc1.sicam.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Objects;

/**
 * Helper ligero para logging.
 * Proporciona helpers estáticos para obtener loggers y gestionar MDC (contexto)
 * para evitar repetir boilerplate en cada clase.
 *
 * Uso recomendado:
 *   private static final Logger LOG = Logs.getLogger(MyClass.class);
 * o directamente:
 *   Logs.info(MyClass.class, "mensaje {}", arg);
 */
public final class Logs {
    private Logs() {
        // Evita multiples instancias
    }

    /**
     * Devuelve un Logger para la clase dada.
     */
    public static Logger getLogger(Class<?> clazz) {
        Objects.requireNonNull(clazz, "clazz no puede ser null");
        return LoggerFactory.getLogger(clazz);
    }

    /**
     * Registra un mensaje de nivel TRACE.
     *
     * @param clazz
     * @param message
     * @param args
     */
    public static void trace(Class<?> clazz, String message, Object... args) {
        getLogger(clazz).trace(message, args);
    }

    public static void debug(Class<?> clazz, String message, Object... args) {
        getLogger(clazz).debug(message, args);
    }

    public static void info(Class<?> clazz, String message, Object... args) {
        getLogger(clazz).info(message, args);
    }

    public static void warn(Class<?> clazz, String message, Object... args) {
        getLogger(clazz).warn(message, args);
    }

    public static void error(Class<?> clazz, String message, Object... args) {
        getLogger(clazz).error(message, args);
    }

    public static void error(Class<?> clazz, String message, Throwable t) {
        getLogger(clazz).error(message, t);
    }

    /**
     * Añade o elimina una entrada en el contexto MDC.
     * Si value es null, elimina la clave del contexto.
     *
     * @param key
     * @param value
     */
    public static void putCtx(String key, String value) {
        if (key == null) return;
        if (value == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, value);
        }
    }

    /**
     * Obtiene un valor del contexto MDC.
     *
     * @param key
     * @return el valor asociado a la clave, o null si no existe
     */
    public static String getCtx(String key) {
        return MDC.get(key);
    }

    /**
     * Elimina una entrada del contexto MDC.
     *
     * @param key
     */
    public static void removeCtx(String key) {
        if (key == null) return;
        MDC.remove(key);
    }

    public static void clearCtx() {
        MDC.clear();
    }
}

