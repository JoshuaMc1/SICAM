package com.joshuamc1.sicam.services;

import com.joshuamc1.sicam.core.DatabaseManager;
import com.joshuamc1.sicam.core.Logs;
import com.joshuamc1.sicam.models.Audit;
import com.joshuamc1.sicam.models.dto.AuditDTO;
import com.joshuamc1.sicam.repositories.AuditRepository;

import java.time.LocalDate;
import java.util.List;

public class AuditService {
    private final AuditRepository repo = new AuditRepository();

    // ===== CONSTANTES DE ACCIONES DE AUDITORÍA =====

    // Acciones básicas
    public static final String ACTION_CREATE = "create";
    public static final String ACTION_UPDATE = "update";
    public static final String ACTION_DELETE = "delete";
    public static final String ACTION_READ = "read";

    // Acciones de autenticación
    public static final String ACTION_LOGIN = "login";
    public static final String ACTION_LOGOUT = "logout";
    public static final String ACTION_LOGIN_FAILED = "login_failed";
    public static final String ACTION_LOGIN_BLOCKED = "login_blocked";

    // Acciones de soft delete y restauración
    public static final String ACTION_SOFT_DELETE = "soft_delete";
    public static final String ACTION_HARD_DELETE = "hard_delete";
    public static final String ACTION_RESTORE = "restore";

    // Acciones de transacciones
    public static final String ACTION_INVALIDATE = "invalidate";
    public static final String ACTION_APPROVE = "approve";
    public static final String ACTION_RETURN = "return";

    // Acciones de estado
    public static final String ACTION_MARK_ACTIVE = "mark_active";
    public static final String ACTION_MARK_INACTIVE = "mark_inactive";

    // Acciones de backup
    public static final String ACTION_BACKUP_CREATE = "backup_create";
    public static final String ACTION_BACKUP_RESTORE = "backup_restore";
    public static final String ACTION_BACKUP_DELETE = "backup_delete";

    public void logAction(Integer userId, String action, String table, Integer recordId, String details) {
        DatabaseManager.runInConnection(() -> {
            Audit audit = new Audit();
            audit.set("user_id", userId);
            audit.set("action", action);
            audit.set("table_name", table);
            audit.set("record_id", recordId);
            audit.set("details", details);
            repo.save(audit);
        });

        Logs.info(getClass(), "Logged action: " + action + " on table: " + table + " for record ID: " + recordId);
    }

    public List<Audit> getAll() {
        return DatabaseManager.callInConnection(repo::findAll);
    }

    public List<AuditDTO> getAllWithUser() {
        return DatabaseManager.callInConnection(repo::findAllWithUser);
    }

    public List<AuditDTO> getPageWithUser(int offset, int limit, String search) {
        return DatabaseManager.callInConnection(() -> repo.findPageWithUser(offset, limit, search));
    }

    public int countWithFilter(String search) {
        return DatabaseManager.callInConnection(() -> repo.countWithFilter(search));
    }

    public List<AuditDTO> getAllWithUserByDateRange(LocalDate from, LocalDate to) {
        return DatabaseManager.callInConnection(() -> repo.findAllWithUserByDateRange(from, to));
    }
}
