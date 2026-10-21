package com.joshuamc1.sicam.core;

import com.joshuamc1.sicam.models.User;
import org.jetbrains.annotations.Nullable;

public final class Session {
    private static volatile User currentUser = null;
    private static final java.util.concurrent.atomic.AtomicLong revision = new java.util.concurrent.atomic.AtomicLong();

    private Session() {}

    public static synchronized void setCurrentUser(User user) {
        if (currentUser != user) {
            revision.incrementAndGet();
            currentUser = user;
        }
    }

    public static long getRevision() {
        return revision.get();
    }

    public static User getCurrentUser() {
        return currentUser;
    }

    public static @Nullable Integer getCurrentUserId() {
        if (currentUser == null) return null;
        return currentUser.getInteger("id");
    }

    public static @Nullable String getCurrentUserRole() {
        if (currentUser == null) return null;
        return currentUser.getRolNombre();
    }

    public static  String getCurrentUsername() {
        if (currentUser == null) return "Guest";
        return currentUser.getString("username");
    }
}

